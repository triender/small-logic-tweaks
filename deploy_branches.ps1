# deploy_branches.ps1
# Automates the safe deployment pipeline for Minecraft mod versions 26.1.2 and 26.2.
# Running this script compiles the code, executes tests, and only deploys + pushes to GitHub if all tests pass.

# 1. Verify Modrinth Token exists
if ([string]::IsNullOrEmpty($env:MODRINTH_TOKEN)) {
    Write-Error "ERROR: Environment variable 'MODRINTH_TOKEN' is not defined. Please set it before deploying."
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
        
        # 2. JUnit
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
        $comp = ($compileStatus[$branch] ?: "N/A").PadRight(8)
        $junit = ($junitStatus[$branch] ?: "N/A").PadRight(8)
        $gt = ($gametestStatus[$branch] ?: "N/A").PadRight(8)
        $dep = ($deployStatus[$branch] ?: "N/A").PadRight(15)
        $push = ($pushStatus[$branch] ?: "N/A").PadRight(10)
        
        Write-Host "  $($branch.PadRight(9)) | " -NoNewline
        
        # Color formatting helper
        if ($comp.Trim() -eq "Passed") { Write-Host "$comp" -ForegroundColor Green -NoNewline } else { Write-Host "$comp" -ForegroundColor Red -NoNewline }
        Write-Host " | " -NoNewline
        if ($junit.Trim() -eq "Passed") { Write-Host "$junit" -ForegroundColor Green -NoNewline } else { Write-Host "$junit" -ForegroundColor Red -NoNewline }
        Write-Host " | " -NoNewline
        if ($gt.Trim() -eq "Passed") { Write-Host "$gt" -ForegroundColor Green -NoNewline } else { Write-Host "$gt" -ForegroundColor Red -NoNewline }
        Write-Host " | " -NoNewline
        if ($dep.Trim() -eq "Success") { Write-Host "$dep" -ForegroundColor Green -NoNewline } else { Write-Host "$dep" -ForegroundColor Red -NoNewline }
        Write-Host " | " -NoNewline
        if ($push.Trim() -eq "Success") { Write-Host "$push" -ForegroundColor Green } else { Write-Host "$push" -ForegroundColor Red }
    }
    Write-Host "==================================================================================" -ForegroundColor Green
}
