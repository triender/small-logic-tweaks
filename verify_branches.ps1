# verify_branches.ps1
# Automates the verification process (compilation, JUnit tests, and GameTests) for Minecraft mod versions 26.1.2 and 26.2

$branches = @("26.1.2", "26.2")
$results = [ordered]@{}

# Save current branch
$originalBranch = (git symbolic-ref --short HEAD).Trim()
Write-Host "Starting verification. Original branch: $originalBranch" -ForegroundColor Cyan

try {
    foreach ($branch in $branches) {
        Write-Host "`n===============================================" -ForegroundColor Yellow
        Write-Host " Verifying branch: $branch" -ForegroundColor Yellow
        Write-Host "===============================================" -ForegroundColor Yellow
        
        # Checkout branch
        Write-Host "Checking out $branch..." -ForegroundColor Cyan
        git checkout $branch
        if ($LASTEXITCODE -ne 0) {
            $results[$branch] = "Failed (git checkout)"
            continue
        }
        
        # 1. Compile
        Write-Host "Running compileJava..." -ForegroundColor Cyan
        .\gradlew clean compileJava
        if ($LASTEXITCODE -ne 0) {
            $results[$branch] = "Failed (Compilation)"
            continue
        }
        
        # 2. JUnit
        Write-Host "Running JUnit tests..." -ForegroundColor Cyan
        .\gradlew cleanTest test
        if ($LASTEXITCODE -ne 0) {
            $results[$branch] = "Failed (JUnit Tests)"
            continue
        }
        
        # 3. GameTests
        Write-Host "Running GameTest server..." -ForegroundColor Cyan
        .\gradlew runGameTestServer
        if ($LASTEXITCODE -ne 0) {
            $results[$branch] = "Failed (GameTests)"
            continue
        }
        
        $results[$branch] = "Passed (Stable)"
    }
}
finally {
    # Restore original branch
    Write-Host "`nRestoring original branch: $originalBranch..." -ForegroundColor Cyan
    git checkout $originalBranch
    
    # Print summary
    Write-Host "`n===============================================" -ForegroundColor Green
    Write-Host " VERIFICATION SUMMARY" -ForegroundColor Green
    Write-Host "===============================================" -ForegroundColor Green
    foreach ($branch in $results.Keys) {
        $status = $results[$branch]
        if ($status -eq "Passed (Stable)") {
            Write-Host "  Branch $branch`: " -NoNewline
            Write-Host "$status" -ForegroundColor Green
        } else {
            Write-Host "  Branch $branch`: " -NoNewline
            Write-Host "$status" -ForegroundColor Red
        }
    }
    Write-Host "===============================================" -ForegroundColor Green
}
