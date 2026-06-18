# deploy_branches.ps1
# Automates the safe deployment pipeline for Minecraft mod versions 26.1.2 and 26.2.
# Running this script compiles the code, executes tests, and only deploys + pushes to GitHub if all tests pass.
# Includes fool-proof safety checks for: dirty git tree, version mismatches, and duplicate uploads.

# Set TLS 1.2 to prevent connection hanging on Cloudflare-protected APIs in older PowerShell environments
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

# 1. Verify Modrinth Token exists
if ([string]::IsNullOrEmpty($env:MODRINTH_TOKEN)) {
    # Fallback to reading from User or Machine environment registry if not inherited in current process
    $env:MODRINTH_TOKEN = [Environment]::GetEnvironmentVariable("MODRINTH_TOKEN", "User")
    if ([string]::IsNullOrEmpty($env:MODRINTH_TOKEN)) {
        $env:MODRINTH_TOKEN = [Environment]::GetEnvironmentVariable("MODRINTH_TOKEN", "Machine")
    }
}

if ([string]::IsNullOrEmpty($env:MODRINTH_TOKEN)) {
    Write-Error "ERROR: Environment variable 'MODRINTH_TOKEN' is not defined. Please set it before deploying."
    exit 1
}

# 2. Verify working tree is clean (Lớp 1: Chống dirty working tree)
$gitStatus = ([string](git status --porcelain)).Trim()
if ($gitStatus) {
    Write-Error "ERROR: Working tree is dirty. Please commit or stash your changes before running deploy."
    exit 1
}

$branches = @("26.1.2", "26.2")
$compileStatus = [ordered]@{}
$junitStatus = [ordered]@{}
$gametestStatus = [ordered]@{}
$deployStatus = [ordered]@{}
$pushStatus = [ordered]@{}

# Save current branch
$originalBranch = (git symbolic-ref --short HEAD).Trim()
Write-Host "Starting deployment pipeline. Original branch: $originalBranch" -ForegroundColor Cyan

try {
    foreach ($branch in $branches) {
        Write-Host "`n===============================================" -ForegroundColor Yellow
        Write-Host " Pipeline for branch: $branch" -ForegroundColor Yellow
        Write-Host "===============================================" -ForegroundColor Yellow
        
        # Checkout branch
        Write-Host "Checking out $branch..." -ForegroundColor Cyan
        git checkout $branch
        if ($LASTEXITCODE -ne 0) {
            $compileStatus[$branch] = "Skipped"
            $junitStatus[$branch] = "Skipped"
            $gametestStatus[$branch] = "Skipped"
            $deployStatus[$branch] = "Failed (git checkout)"
            $pushStatus[$branch] = "Skipped"
            continue
        }
        
        # Đọc file gradle.properties của nhánh hiện tại (Lớp 2: Chống lệch cấu hình / râu ông nọ cắm cằm bà kia)
        Write-Host "Checking version alignment in gradle.properties..." -ForegroundColor Cyan
        if (-not (Test-Path "gradle.properties")) {
            Write-Error "ERROR: gradle.properties not found in branch $branch."
            $compileStatus[$branch] = "Skipped"
            $junitStatus[$branch] = "Skipped"
            $gametestStatus[$branch] = "Skipped"
            $deployStatus[$branch] = "Failed (gradle.properties missing)"
            $pushStatus[$branch] = "Skipped"
            continue
        }
        $props = ConvertFrom-StringData (Get-Content "gradle.properties" -Raw)
        $mcVer = $props["minecraft_version"]
        $modVer = $props["mod_version"]
        
        if ($mcVer -ne $branch) {
            Write-Error "ERROR: Git branch is '$branch' but minecraft_version in gradle.properties is '$mcVer'!"
            $compileStatus[$branch] = "Skipped"
            $junitStatus[$branch] = "Skipped"
            $gametestStatus[$branch] = "Skipped"
            $deployStatus[$branch] = "Failed (minecraft_version mismatch)"
            $pushStatus[$branch] = "Skipped"
            continue
        }
        if ($modVer -notlike "*-$branch") {
            Write-Error "ERROR: Git branch is '$branch' but mod_version in gradle.properties is '$modVer' (does not end with -$branch)!"
            $compileStatus[$branch] = "Skipped"
            $junitStatus[$branch] = "Skipped"
            $gametestStatus[$branch] = "Skipped"
            $deployStatus[$branch] = "Failed (mod_version mismatch)"
            $pushStatus[$branch] = "Skipped"
            continue
        }
        Write-Host "Versions match: minecraft_version=$mcVer, mod_version=$modVer" -ForegroundColor Green
        
        # Đọc projectId từ build.gradle để kiểm tra trùng lặp
        $projId = "small-logic-tweaks"
        if (Test-Path "build.gradle") {
            $buildGradle = Get-Content "build.gradle" -Raw
            if ($buildGradle -match 'projectId\s*=\s*"([^"]+)"') {
                $projId = $Matches[1]
            }
        }
        
        # Lớp 3: Kiểm tra trùng lặp phiên bản trên Modrinth qua REST API
        Write-Host "Querying Modrinth API to check if version $modVer already exists..." -ForegroundColor Cyan
        $alreadyExists = $false
        try {
            $headers = @{ "User-Agent" = "triender/small-logic-tweaks-deploy/1.0" }
            $versions = Invoke-RestMethod -Uri "https://api.modrinth.com/v2/project/$projId/version" -Headers $headers -Method Get -TimeoutSec 5
            foreach ($v in $versions) {
                if ($v.version_number -eq $modVer) {
                    $alreadyExists = $true
                    break
                }
            }
        } catch {
            Write-Host "Could not query Modrinth API (Error: $_.Exception.Message). Skipping check and continuing..." -ForegroundColor Yellow
        }
        
        if ($alreadyExists) {
            Write-Warning "Version $modVer already exists on Modrinth. Skipping deploy/push to prevent duplicate release errors."
            $compileStatus[$branch] = "Skipped"
            $junitStatus[$branch] = "Skipped"
            $gametestStatus[$branch] = "Skipped"
            $deployStatus[$branch] = "Skipped (Exists)"
            $pushStatus[$branch] = "Skipped (Exists)"
            continue
        }
        
        # 1. Compile
        Write-Host "Running compileJava..." -ForegroundColor Cyan
        .\gradlew clean compileJava
        if ($LASTEXITCODE -ne 0) {
            $compileStatus[$branch] = "Failed"
            $junitStatus[$branch] = "Skipped"
            $gametestStatus[$branch] = "Skipped"
            $deployStatus[$branch] = "Skipped (Compile Failed)"
            $pushStatus[$branch] = "Skipped"
            continue
        }
        $compileStatus[$branch] = "Passed"
        
        # 2. JUnit (Lớp 4: Tự động chạy verifyChangelog qua modrinth -> verifyChangelog cũng được chạy ở đây nếu gọi test trực tiếp)
        Write-Host "Running JUnit tests..." -ForegroundColor Cyan
        .\gradlew cleanTest test
        if ($LASTEXITCODE -ne 0) {
            $junitStatus[$branch] = "Failed"
            $gametestStatus[$branch] = "Skipped"
            $deployStatus[$branch] = "Skipped (JUnit Failed)"
            $pushStatus[$branch] = "Skipped"
            continue
        }
        $junitStatus[$branch] = "Passed"
        
        # 3. GameTests
        Write-Host "Running GameTest server..." -ForegroundColor Cyan
        .\gradlew runGameTestServer
        if ($LASTEXITCODE -ne 0) {
            $gametestStatus[$branch] = "Failed"
            $deployStatus[$branch] = "Skipped (GameTest Failed)"
            $pushStatus[$branch] = "Skipped"
            continue
        }
        $gametestStatus[$branch] = "Passed"
        
        # 4. Deploy to Modrinth
        Write-Host "Deploying to Modrinth..." -ForegroundColor Cyan
        .\gradlew build modrinth
        if ($LASTEXITCODE -ne 0) {
            $deployStatus[$branch] = "Failed (Upload error)"
            $pushStatus[$branch] = "Skipped (Deploy Failed)"
            continue
        }
        $deployStatus[$branch] = "Success"

        # 5. Push to GitHub
        Write-Host "Pushing code to GitHub (origin $branch)..." -ForegroundColor Cyan
        git push origin $branch
        if ($LASTEXITCODE -ne 0) {
            $pushStatus[$branch] = "Failed (git push error)"
            continue
        }
        $pushStatus[$branch] = "Success"
    }
}
finally {
    # Restore original branch
    Write-Host "`nRestoring original branch: $originalBranch..." -ForegroundColor Cyan
    git checkout $originalBranch
    
    # Print summary
    Write-Host "`n==================================================================================" -ForegroundColor Green
    Write-Host "                               DEPLOYMENT SUMMARY" -ForegroundColor Green
    Write-Host "==================================================================================" -ForegroundColor Green
    Write-Host "  Branch    | Compile  | JUnit    | GameTest | Modrinth Deploy | GitHub Push" -ForegroundColor Green
    Write-Host "  ----------|----------|----------|----------|-----------------|------------" -ForegroundColor Green
    foreach ($branch in $branches) {
        $comp = $compileStatus[$branch]
        if ($null -eq $comp) { $comp = "N/A" }
        $comp = $comp.PadRight(8)
        
        $junit = $junitStatus[$branch]
        if ($null -eq $junit) { $junit = "N/A" }
        $junit = $junit.PadRight(8)
        
        $gt = $gametestStatus[$branch]
        if ($null -eq $gt) { $gt = "N/A" }
        $gt = $gt.PadRight(8)
        
        $dep = $deployStatus[$branch]
        if ($null -eq $dep) { $dep = "N/A" }
        $dep = $dep.PadRight(15)
        
        $push = $pushStatus[$branch]
        if ($null -eq $push) { $push = "N/A" }
        $push = $push.PadRight(10)
        
        Write-Host "  $($branch.PadRight(9)) | " -NoNewline
        
        # Color formatting helper for Compile
        if ($comp.Trim() -eq "Passed") { Write-Host "$comp" -ForegroundColor Green -NoNewline }
        elseif ($comp.Trim() -eq "Skipped") { Write-Host "$comp" -ForegroundColor Yellow -NoNewline }
        else { Write-Host "$comp" -ForegroundColor Red -NoNewline }
        Write-Host " | " -NoNewline
        
        # Color formatting helper for JUnit
        if ($junit.Trim() -eq "Passed") { Write-Host "$junit" -ForegroundColor Green -NoNewline }
        elseif ($junit.Trim() -eq "Skipped") { Write-Host "$junit" -ForegroundColor Yellow -NoNewline }
        else { Write-Host "$junit" -ForegroundColor Red -NoNewline }
        Write-Host " | " -NoNewline
        
        # Color formatting helper for GameTest
        if ($gt.Trim() -eq "Passed") { Write-Host "$gt" -ForegroundColor Green -NoNewline }
        elseif ($gt.Trim() -eq "Skipped") { Write-Host "$gt" -ForegroundColor Yellow -NoNewline }
        else { Write-Host "$gt" -ForegroundColor Red -NoNewline }
        Write-Host " | " -NoNewline
        
        # Color formatting helper for Deploy
        if ($dep.Trim() -eq "Success") { Write-Host "$dep" -ForegroundColor Green -NoNewline }
        elseif ($dep.Trim() -eq "Skipped (Exists)") { Write-Host "$dep" -ForegroundColor Yellow -NoNewline }
        else { Write-Host "$dep" -ForegroundColor Red -NoNewline }
        Write-Host " | " -NoNewline
        
        # Color formatting helper for Push
        if ($push.Trim() -eq "Success") { Write-Host "$push" -ForegroundColor Green }
        elseif ($push.Trim() -eq "Skipped" -or $push.Trim() -eq "Skipped (Exists)") { Write-Host "$push" -ForegroundColor Yellow }
        else { Write-Host "$push" -ForegroundColor Red }
    }
    Write-Host "==================================================================================" -ForegroundColor Green
}
