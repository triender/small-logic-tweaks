# deploy_branches.ps1
# Automates the safe, verify-first deployment pipeline for Minecraft mod versions 26.1.2, 26.2, and 26.3.
#
# ARCHITECTURAL RULES & CONSTRAINTS:
# 1. Phase 1 (Independent Verification Gate): Compiles, runs JUnit, and executes GameTests on ALL branches first.
# 2. Zero Piecemeal Deployment: If even one branch fails verification, the pipeline is terminated immediately with NO deployment.
# 3. Phase 2 (Safe Deployment): Only runs when 100% of branches have successfully passed Phase 1.
# 4. Dynamic Version & Status: Accurately resolves base version and release status (release, beta, alpha) from CHANGE_LOG.md.

[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

# 1. Verify Modrinth Token exists
if ([string]::IsNullOrEmpty($env:MODRINTH_TOKEN)) {
    $env:MODRINTH_TOKEN = [Environment]::GetEnvironmentVariable("MODRINTH_TOKEN", "User")
    if ([string]::IsNullOrEmpty($env:MODRINTH_TOKEN)) {
        $env:MODRINTH_TOKEN = [Environment]::GetEnvironmentVariable("MODRINTH_TOKEN", "Machine")
    }
}

if ([string]::IsNullOrEmpty($env:MODRINTH_TOKEN)) {
    Write-Error "ERROR: Environment variable 'MODRINTH_TOKEN' is not defined. Please set it before deploying."
    exit 1
}

# 2. Verify working tree is clean (Lop 1: Chong dirty working tree)
$gitStatus = "$(git status --porcelain)".Trim()
if ($gitStatus) {
    Write-Error "ERROR: Working tree is dirty. Please commit or stash your changes before running deploy."
    exit 1
}

$branches = @("26.1.2", "26.2", "26.3")

# Status dictionaries for tracking
$compileStatus = [ordered]@{}
$junitStatus = [ordered]@{}
$gametestStatus = [ordered]@{}
$changelogStatus = [ordered]@{}
$versionInfo = [ordered]@{}
$deployStatus = [ordered]@{}
$pushStatus = [ordered]@{}

# Helper: Extract version metadata from gradle.properties and CHANGE_LOG.md
function Get-ModMetadata {
    param([string]$targetBranch)
    
    if (-not (Test-Path "gradle.properties")) {
        return $null
    }
    
    $props = ConvertFrom-StringData (Get-Content "gradle.properties" -Raw)
    $mcVer = $props["minecraft_version"]
    $modVer = $props["mod_version"]
    
    # Calculate base version (strip -mc<mcVer>)
    $mcSuffix = "-mc$mcVer"
    $baseVer = $modVer
    if ($baseVer.EndsWith($mcSuffix)) {
        $baseVer = $baseVer.Substring(0, $baseVer.Length - $mcSuffix.Length)
    } else {
        $baseVer = $baseVer.Split('-')[0]
    }
    
    # Extract status from CHANGE_LOG.md
    $statusType = "release"
    if (Test-Path "CHANGE_LOG.md") {
        $lines = Get-Content "CHANGE_LOG.md"
        $foundVersion = $false
        foreach ($line in $lines) {
            if ($line.StartsWith("# ") -and ($line.Contains("Version $baseVer") -or $line.Contains("Version: $baseVer"))) {
                $foundVersion = $true
                continue
            }
            if ($foundVersion) {
                if ($line.StartsWith("---")) { break }
                $trimmed = $line.Trim().ToLower()
                if ($trimmed.StartsWith("**status:**") -or $trimmed.StartsWith("status:")) {
                    if ($trimmed.Contains("release")) { $statusType = "release"; break }
                    if ($trimmed.Contains("beta")) { $statusType = "beta"; break }
                    if ($trimmed.Contains("alpha")) { $statusType = "alpha"; break }
                }
            }
        }
    }
    
    return [pscustomobject]@{
        MinecraftVersion = $mcVer
        ModVersion = $modVer
        BaseVersion = $baseVer
        VersionType = $statusType
    }
}

$originalBranch = (git symbolic-ref --short HEAD).Trim()
Write-Host "Starting two-phase deploy pipeline. Original branch: $originalBranch" -ForegroundColor Cyan

$allVerified = $false

try {
    # ==============================================================================
    # PHASE 1: INDEPENDENT VERIFICATION GATE (TOAN BO NHANH PHAI PASS TRUOC)
    # ==============================================================================
    Write-Host "`n==================================================================================" -ForegroundColor Magenta
    Write-Host " PHASE 1: INDEPENDENT VERIFICATION GATE (ALL BRANCHES MUST PASS)" -ForegroundColor Magenta
    Write-Host "==================================================================================" -ForegroundColor Magenta
    
    $verificationFailed = $false
    
    foreach ($branch in $branches) {
        Write-Host "`n--------------------------------------------------" -ForegroundColor Yellow
        Write-Host " Verifying branch: $branch" -ForegroundColor Yellow
        Write-Host "--------------------------------------------------" -ForegroundColor Yellow
        
        # 1.1 Checkout branch
        Write-Host "Checking out $branch..." -ForegroundColor Cyan
        git checkout $branch
        if ($LASTEXITCODE -ne 0) {
            $compileStatus[$branch] = "Skipped"
            $junitStatus[$branch] = "Skipped"
            $gametestStatus[$branch] = "Skipped"
            $changelogStatus[$branch] = "Skipped"
            $verificationFailed = $true
            Write-Host "Failed to checkout branch $branch!" -ForegroundColor Red
            continue
        }
        
        # 1.2 Stop Daemons & kill stray java to avoid lock issues
        Write-Host "Stopping Gradle Daemons to prevent file locks..." -ForegroundColor Cyan
        .\gradlew --stop
        Stop-Process -Name java -Force -ErrorAction SilentlyContinue
        Remove-Item -Recurse -Force build -ErrorAction SilentlyContinue
        
        # 1.3 Validate version alignment
        $meta = Get-ModMetadata -targetBranch $branch
        if ($null -eq $meta) {
            Write-Error "ERROR: gradle.properties not found in branch $branch."
            $compileStatus[$branch] = "Failed (gradle.properties)"
            $junitStatus[$branch] = "Skipped"
            $gametestStatus[$branch] = "Skipped"
            $changelogStatus[$branch] = "Skipped"
            $verificationFailed = $true
            continue
        }
        
        $versionInfo[$branch] = "$($meta.ModVersion) [$($meta.VersionType)]"
        Write-Host "Target: MC $($meta.MinecraftVersion) | Mod Version: $($meta.ModVersion) | Type: $($meta.VersionType)" -ForegroundColor Green
        
        if ($meta.MinecraftVersion -ne $branch) {
            Write-Error "ERROR: Branch is '$branch' but minecraft_version is '$($meta.MinecraftVersion)'!"
            $compileStatus[$branch] = "Failed (MC version mismatch)"
            $junitStatus[$branch] = "Skipped"
            $gametestStatus[$branch] = "Skipped"
            $changelogStatus[$branch] = "Skipped"
            $verificationFailed = $true
            continue
        }
        
        if ($meta.ModVersion -notlike "*-mc$branch") {
            Write-Error "ERROR: mod_version '$($meta.ModVersion)' does not end with -mc$branch!"
            $compileStatus[$branch] = "Failed (Mod version suffix)"
            $junitStatus[$branch] = "Skipped"
            $gametestStatus[$branch] = "Skipped"
            $changelogStatus[$branch] = "Skipped"
            $verificationFailed = $true
            continue
        }
        
        # 1.4 Verify Changelog Task
        Write-Host "Verifying changelog and metadata extraction..." -ForegroundColor Cyan
        .\gradlew verifyChangelog --no-daemon
        if ($LASTEXITCODE -ne 0) {
            $compileStatus[$branch] = "Skipped"
            $junitStatus[$branch] = "Skipped"
            $gametestStatus[$branch] = "Skipped"
            $changelogStatus[$branch] = "Failed"
            $verificationFailed = $true
            Write-Host "Changelog verification failed for branch $branch!" -ForegroundColor Red
            continue
        }
        $changelogStatus[$branch] = "Passed"
        
        # 1.5 Compile
        Write-Host "Running compileJava..." -ForegroundColor Cyan
        .\gradlew clean compileJava --no-daemon
        if ($LASTEXITCODE -ne 0) {
            $compileStatus[$branch] = "Failed"
            $junitStatus[$branch] = "Skipped"
            $gametestStatus[$branch] = "Skipped"
            $verificationFailed = $true
            Write-Host "Compilation failed on branch $branch!" -ForegroundColor Red
            continue
        }
        $compileStatus[$branch] = "Passed"
        
        # 1.6 JUnit
        Write-Host "Running JUnit test suite..." -ForegroundColor Cyan
        .\gradlew cleanTest test --no-daemon
        if ($LASTEXITCODE -ne 0) {
            $junitStatus[$branch] = "Failed"
            $gametestStatus[$branch] = "Skipped"
            $verificationFailed = $true
            Write-Host "JUnit tests failed on branch $branch!" -ForegroundColor Red
            continue
        }
        $junitStatus[$branch] = "Passed"
        
        # 1.7 GameTest Server
        Write-Host "Running GameTest server..." -ForegroundColor Cyan
        .\gradlew runGameTestServer --no-daemon
        if ($LASTEXITCODE -ne 0) {
            $gametestStatus[$branch] = "Failed"
            $verificationFailed = $true
            Write-Host "GameTest server failed on branch $branch!" -ForegroundColor Red
            continue
        }
        $gametestStatus[$branch] = "Passed"
        
        Write-Host "Branch $branch VERIFICATION PASSED (All checks green)!" -ForegroundColor Green
    }
    
    # ------------------------------------------------------------------------------
    # ALL-PASS GATE CHECK
    # ------------------------------------------------------------------------------
    if ($verificationFailed) {
        Write-Host "`n==================================================================================" -ForegroundColor Red
        Write-Host " CRITICAL: VERIFICATION GATE FAILED ON ONE OR MORE BRANCHES!" -ForegroundColor Red
        Write-Host " ABORTING DEPLOYMENT PIPELINE. NO PACKAGES WERE UPLOADED OR PUSHED." -ForegroundColor Red
        Write-Host "==================================================================================" -ForegroundColor Red
        return
    }
    
    $allVerified = $true
    Write-Host "`n==================================================================================" -ForegroundColor Green
    Write-Host " ALL BRANCHES PASSED VERIFICATION GATE (100%). PROCEEDING TO DEPLOYMENT PHASE." -ForegroundColor Green
    Write-Host "==================================================================================" -ForegroundColor Green
    
    # ==============================================================================
    # PHASE 2: DEPLOYMENT PHASE (CHI CHAY KHI TAT CA CAC NHANH DA PASS O PHASE 1)
    # ==============================================================================
    foreach ($branch in $branches) {
        Write-Host "`n--------------------------------------------------" -ForegroundColor Yellow
        Write-Host " Deploying branch: $branch" -ForegroundColor Yellow
        Write-Host "--------------------------------------------------" -ForegroundColor Yellow
        
        git checkout $branch
        $meta = Get-ModMetadata -targetBranch $branch
        $modVer = $meta.ModVersion
        
        # Check if already exists on Modrinth to prevent duplicate release error
        $projId = "small-logic-tweak"
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
            Write-Host "Could not query Modrinth API ($($_.Exception.Message)). Proceeding with caution..." -ForegroundColor Yellow
        }
        
        if ($alreadyExists) {
            Write-Warning "Version $modVer already exists on Modrinth. Skipping upload."
            $deployStatus[$branch] = "Skipped (Exists)"
        } else {
            Write-Host "Uploading $modVer [$($meta.VersionType)] to Modrinth..." -ForegroundColor Cyan
            .\gradlew build modrinth --no-daemon
            if ($LASTEXITCODE -ne 0) {
                $deployStatus[$branch] = "Failed"
                $pushStatus[$branch] = "Skipped"
                Write-Host "Modrinth upload failed for branch $branch!" -ForegroundColor Red
                continue
            }
            $deployStatus[$branch] = "Success"
        }
        
        # Push to remote git
        Write-Host "Pushing branch $branch to remote origin..." -ForegroundColor Cyan
        git push origin $branch
        if ($LASTEXITCODE -ne 0) {
            $pushStatus[$branch] = "Failed"
            Write-Host "Git push failed for branch $branch!" -ForegroundColor Red
            continue
        }
        $pushStatus[$branch] = "Success"
    }
}
finally {
    # Restore original branch
    Write-Host "`nRestoring original branch: $originalBranch..." -ForegroundColor Cyan
    git checkout $originalBranch
    
    # Print Comprehensive Pipeline Summary Table
    Write-Host "`n====================================================================================================" -ForegroundColor Green
    Write-Host "                                    DEPLOYMENT PIPELINE SUMMARY" -ForegroundColor Green
    Write-Host "====================================================================================================" -ForegroundColor Green
    Write-Host "  Branch    | Version & Status      | Changelog | Compile  | JUnit    | GameTest | Modrinth Deploy | Git Push" -ForegroundColor Green
    Write-Host "  ----------|-----------------------|-----------|----------|----------|----------|-----------------|---------" -ForegroundColor Green
    
    foreach ($branch in $branches) {
        $ver = $versionInfo[$branch]; if ($null -eq $ver) { $ver = "N/A" }; $ver = $ver.PadRight(21)
        $cl = $changelogStatus[$branch]; if ($null -eq $cl) { $cl = "N/A" }; $cl = $cl.PadRight(9)
        $comp = $compileStatus[$branch]; if ($null -eq $comp) { $comp = "N/A" }; $comp = $comp.PadRight(8)
        $junit = $junitStatus[$branch]; if ($null -eq $junit) { $junit = "N/A" }; $junit = $junit.PadRight(8)
        $gt = $gametestStatus[$branch]; if ($null -eq $gt) { $gt = "N/A" }; $gt = $gt.PadRight(8)
        $dep = $deployStatus[$branch]; if ($null -eq $dep) { $dep = "N/A" }; $dep = $dep.PadRight(15)
        $push = $pushStatus[$branch]; if ($null -eq $push) { $push = "N/A" }; $push = $push.PadRight(8)
        
        Write-Host "  $($branch.PadRight(9)) | $ver | " -NoNewline
        
        # Color formatting helper
        if ($cl.Trim() -eq "Passed") { Write-Host "$cl" -ForegroundColor Green -NoNewline } else { Write-Host "$cl" -ForegroundColor Red -NoNewline }
        Write-Host " | " -NoNewline
        
        if ($comp.Trim() -eq "Passed") { Write-Host "$comp" -ForegroundColor Green -NoNewline } else { Write-Host "$comp" -ForegroundColor Red -NoNewline }
        Write-Host " | " -NoNewline
        
        if ($junit.Trim() -eq "Passed") { Write-Host "$junit" -ForegroundColor Green -NoNewline } else { Write-Host "$junit" -ForegroundColor Red -NoNewline }
        Write-Host " | " -NoNewline
        
        if ($gt.Trim() -eq "Passed") { Write-Host "$gt" -ForegroundColor Green -NoNewline } else { Write-Host "$gt" -ForegroundColor Red -NoNewline }
        Write-Host " | " -NoNewline
        
        if ($dep.Trim() -eq "Success") { Write-Host "$dep" -ForegroundColor Green -NoNewline }
        elseif ($dep.Trim() -eq "Skipped (Exists)") { Write-Host "$dep" -ForegroundColor Yellow -NoNewline }
        else { Write-Host "$dep" -ForegroundColor Red -NoNewline }
        Write-Host " | " -NoNewline
        
        if ($push.Trim() -eq "Success") { Write-Host "$push" -ForegroundColor Green }
        else { Write-Host "$push" -ForegroundColor Red }
    }
    Write-Host "====================================================================================================" -ForegroundColor Green
}