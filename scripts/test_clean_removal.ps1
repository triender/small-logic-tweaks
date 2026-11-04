<#
.SYNOPSIS
    Automated verification of clean mod uninstallation (Safe-to-Remove Verification).
.DESCRIPTION
    Two-phase verification architecture:
    - Phase 1: Modded world state generation & pristine snapshotting.
               Boots the modded server, injects realistic test state (chest with timber books and axes,
               armor stand holding timber axe, cookable item on magma), saves the world to disk,
               and captures an untouched snapshot (world_snapshot/).
    - Phase 2: Uninstallation verification across N target environments.
               Each environment restores a fresh copy from world_snapshot/, boots unmodded,
               and executes automated NBT assertions and strict log auditing:
               * Environment A: Modloader runtime (Fabric Server without the mod).
               * Environment B: Pure Vanilla runtime (Mojang server.jar with no loader).
.EXAMPLE
    .\scripts\test_clean_removal.ps1
    .\scripts\test_clean_removal.ps1 -TestVanilla -KeepWorld
#>

[CmdletBinding()]
param(
    [string]$ModJar = "",
    [string]$MinecraftVersion = "",
    [string]$LoaderVersion = "",
    [switch]$TestVanilla,
    [switch]$KeepWorld,
    [int]$TickDurationSeconds = 6
)

$ErrorActionPreference = "Stop"

$RepoRoot = Split-Path -Parent $PSScriptRoot
$DeployRoot = Join-Path $env:LOCALAPPDATA "small-logic-tweaks-deploy"
$TestWorkDir = Join-Path $DeployRoot "clean-removal-test"

function Read-GradleProperties([string]$Dir) {
    $props = @{}
    $file = Join-Path $Dir "gradle.properties"
    if (-not (Test-Path $file)) { return $null }
    foreach ($line in Get-Content $file) {
        if ($line -match '^\s*([^#!\s][^=]*?)\s*=\s*(.*?)\s*$') {
            $props[$Matches[1]] = $Matches[2]
        }
    }
    return $props
}

function Get-JavaPath {
    if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME "bin\java.exe"))) {
        return Join-Path $env:JAVA_HOME "bin\java.exe"
    }
    $cmd = Get-Command java -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    throw "Java runtime not found. Please ensure JAVA_HOME is configured."
}

function Read-SharedText([string]$Path) {
    if (-not (Test-Path $Path)) { return "" }
    try {
        $fs = [IO.File]::Open($Path, [IO.FileMode]::Open, [IO.FileAccess]::Read, [IO.FileShare]::ReadWrite -bor [IO.FileShare]::Delete)
        try { return (New-Object IO.StreamReader($fs)).ReadToEnd() } finally { $fs.Dispose() }
    } catch {
        return ""
    }
}

function Resolve-ModJar {
    if ($ModJar -and (Test-Path $ModJar)) {
        return (Resolve-Path $ModJar).Path
    }

    $existing = Get-ChildItem -Path (Join-Path $RepoRoot "fabric\build\libs") -Filter "small-logic-tweaks-fabric-*.jar" -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -notlike "*-sources.jar" -and $_.Name -notlike "*-dev.jar" } |
        Select-Object -First 1

    if ($existing) {
        Write-Host "[Artifact] Found existing mod jar: $($existing.Name)" -ForegroundColor Cyan
        return $existing.FullName
    }

    Write-Host "[Artifact] No built jar found in fabric\build\libs. Triggering Gradle build..." -ForegroundColor Yellow
    Push-Location $RepoRoot
    try {
        & .\gradlew.bat :fabric:build --no-daemon --console=plain | Out-Host
        if ($LASTEXITCODE -ne 0) {
            throw "Gradle build failed with exit code $LASTEXITCODE."
        }
    } finally {
        Pop-Location
    }

    $newlyBuilt = Get-ChildItem -Path (Join-Path $RepoRoot "fabric\build\libs") -Filter "small-logic-tweaks-fabric-*.jar" |
        Where-Object { $_.Name -notlike "*-sources.jar" -and $_.Name -notlike "*-dev.jar" } |
        Select-Object -First 1

    if ($null -eq $newlyBuilt) {
        throw "Could not locate built mod jar after Gradle build."
    }
    return $newlyBuilt.FullName
}

function Ensure-ServerEnvironment([string]$McVersion, [string]$LdrVersion, [string]$FabricApiVersion) {
    $serverDir = Join-Path $DeployRoot "fabric\server-$McVersion-$LdrVersion"
    $installerPath = Join-Path $DeployRoot "fabric\fabric-installer-1.0.1.jar"
    $fabricApiJar = Join-Path $DeployRoot "quilt\fabric-api-$FabricApiVersion.jar"

    if (-not (Test-Path $serverDir)) {
        Write-Host "[Setup] Installing Fabric Server ($McVersion, Loader $LdrVersion)..." -ForegroundColor Cyan
        New-Item -ItemType Directory -Force (Join-Path $DeployRoot "fabric") | Out-Null
        if (-not (Test-Path $installerPath)) {
            Invoke-WebRequest -Uri "https://maven.fabricmc.net/net/fabricmc/fabric-installer/1.0.1/fabric-installer-1.0.1.jar" -OutFile $installerPath
        }
        New-Item -ItemType Directory -Force $serverDir | Out-Null
        $java = Get-JavaPath
        & $java -jar $installerPath server -mcversion $McVersion -loader $LdrVersion -dir $serverDir -downloadMinecraft | Out-Host
        if ($LASTEXITCODE -ne 0) {
            throw "Fabric installer failed to set up server directory."
        }
    }

    return @{
        ServerDir    = $serverDir
        FabricApiJar = $fabricApiJar
    }
}

function Prepare-Workspace([string]$ServerDir, [string]$FabricApiJar) {
    if (Test-Path $TestWorkDir) {
        cmd /c "rmdir /s /q `"$TestWorkDir`"" | Out-Null
    }
    New-Item -ItemType Directory -Force $TestWorkDir | Out-Null

    # Create directory junction for libraries to avoid heavy file duplication
    cmd /c "mklink /J `"$TestWorkDir\libraries`" `"$ServerDir\libraries`"" | Out-Null

    Copy-Item (Join-Path $ServerDir "server.jar") $TestWorkDir
    Copy-Item (Join-Path $ServerDir "fabric-server-launch.jar") $TestWorkDir

    Set-Content -Encoding ASCII (Join-Path $TestWorkDir "eula.txt") "eula=true"
    $serverProps = @(
        "server-port=25597",
        "online-mode=false",
        "difficulty=peaceful",
        "level-seed=42",
        "sync-chunk-writes=false",
        "max-tick-time=-1"
    )
    Set-Content -Encoding ASCII (Join-Path $TestWorkDir "server.properties") $serverProps

    $modsDir = Join-Path $TestWorkDir "mods"
    New-Item -ItemType Directory -Force $modsDir | Out-Null
    if ($FabricApiJar -and (Test-Path $FabricApiJar)) {
        Copy-Item $FabricApiJar $modsDir
    }
}

function Execute-ServerInstance {
    param(
        [string]$RunLabel,
        [string]$LogArchiveName,
        [string]$JavaArgs,
        [string[]]$CommandsToSend,
        [int]$TickWaitSeconds = 6
    )

    Write-Host "`n[$RunLabel] Starting server instance..." -ForegroundColor Yellow
    $java = Get-JavaPath

    $logFile = Join-Path $TestWorkDir "logs\latest.log"
    if (Test-Path $logFile) { Remove-Item -Force $logFile }

    $psi = New-Object System.Diagnostics.ProcessStartInfo
    $psi.FileName = $java
    $psi.Arguments = "-Xmx2G $JavaArgs nogui"
    $psi.WorkingDirectory = $TestWorkDir
    $psi.UseShellExecute = $false
    $psi.RedirectStandardInput = $true
    $psi.RedirectStandardOutput = $true
    $psi.RedirectStandardError = $true
    $psi.CreateNoWindow = $true

    $proc = [System.Diagnostics.Process]::Start($psi)
    $stdout = $proc.StandardOutput.ReadToEndAsync()
    $stderr = $proc.StandardError.ReadToEndAsync()

    $deadline = (Get-Date).AddSeconds(180)
    $ready = $false

    while (-not $proc.HasExited -and (Get-Date) -lt $deadline) {
        Start-Sleep -Seconds 2
        $log = Read-SharedText $logFile
        if ($log -match 'Done \(') {
            $ready = $true
            break
        }
    }

    if (-not $ready) {
        Write-Host "[$RunLabel] FAILED: Server failed to reach ready state within timeout." -ForegroundColor Red
        if (-not $proc.HasExited) { $proc.Kill() }
        $proc.WaitForExit()
        return @{ Success = $false; Log = Read-SharedText $logFile }
    }

    Write-Host "[$RunLabel] Server ready (Done). Executing command sequence..." -ForegroundColor Green
    foreach ($cmd in $CommandsToSend) {
        $proc.StandardInput.WriteLine($cmd)
        $proc.StandardInput.Flush()
        Start-Sleep -Milliseconds 400
    }

    if ($TickWaitSeconds -gt 0) {
        Write-Host "[$RunLabel] Ticking world simulation ($TickWaitSeconds s)..." -ForegroundColor Gray
        Start-Sleep -Seconds $TickWaitSeconds
    }

    Write-Host "[$RunLabel] Flushing world data (save-all flush) and shutting down..." -ForegroundColor Cyan
    $proc.StandardInput.WriteLine("save-all flush")
    $proc.StandardInput.Flush()
    Start-Sleep -Seconds 1
    $proc.StandardInput.WriteLine("stop")
    $proc.StandardInput.Flush()

    if (-not $proc.WaitForExit(30000)) {
        Write-Host "[$RunLabel] Server shutdown unresponsive, killing process." -ForegroundColor Yellow
        $proc.Kill()
    }
    $proc.WaitForExit()

    $archiveLog = Join-Path $TestWorkDir "logs\$LogArchiveName.log"
    if (Test-Path $logFile) {
        Copy-Item -Force $logFile $archiveLog
    }

    $crashDir = Join-Path $TestWorkDir "crash-reports"
    if (Test-Path $crashDir) {
        $crashFiles = Get-ChildItem -Path $crashDir -Filter "*.txt"
        if ($crashFiles.Count -gt 0) {
            Write-Host "[$RunLabel] CRASH REPORT DETECTED:" -ForegroundColor Red
            Get-Content $crashFiles[0].FullName | Select-Object -First 30 | Out-Host
            return @{ Success = $false; Log = Read-SharedText $archiveLog; CrashReport = $true }
        }
    }

    return @{ Success = $true; Log = Read-SharedText $archiveLog; CrashReport = $false }
}

function Audit-LogOutput([string]$LogText, [string]$RunLabel) {
    $criticalErrors = @()
    $expectedDegradation = @()
    $warnings = @()

    foreach ($line in ($LogText -split "`r?`n")) {
        if ($line -match 'small_logic_tweaks:timber') {
            $expectedDegradation += $line
            continue
        }

        if ($line -match 'CrashReport|Encountered an unexpected exception|\b(FATAL|CRITICAL)\b|Failed to load chunk|Failed to parse tag') {
            $criticalErrors += $line
        } elseif ($line -match '^\s*at\s+(net\.minecraft\.|java\.base\.)') {
            $criticalErrors += $line
        } elseif ($line -match 'java\.lang\.(NullPointerException|ClassCastException|IllegalStateException|IllegalArgumentException|ArrayIndexOutOfBoundsException)') {
            $criticalErrors += $line
        } elseif ($line -match '\[.*\/WARN\]') {
            $warnings += $line
        }
    }

    return @{
        CriticalErrors      = $criticalErrors
        ExpectedDegradation = $expectedDegradation
        Warnings            = $warnings
    }
}

function Assert-WorldData([string]$LogText, [string]$EnvLabel) {
    Write-Host "[$EnvLabel] Evaluating automated data assertions..." -ForegroundColor Cyan
    $failures = @()

    # 1. Assert Chest Data
    $chestMatch = $LogText | Select-String -Pattern '0, 100, 0 has the following block data: (\[.*\])'
    if ($chestMatch) {
        $chestJson = $chestMatch.Matches[0].Groups[1].Value
        Write-Host "   -> Chest NBT: $chestJson" -ForegroundColor Gray

        if ($chestJson -notmatch 'count:\s*3.*id:\s*"minecraft:enchanted_book"') {
            $failures += "Chest slot 0: Missing 3x enchanted_book or corrupted count."
        }
        if ($chestJson -notmatch 'count:\s*1.*id:\s*"minecraft:diamond_axe"') {
            $failures += "Chest slot 1: Missing diamond_axe or corrupted count."
        }
        if ($chestJson -notmatch '"minecraft:unbreaking":\s*3') {
            $failures += "Chest slot 2: Netherite axe lost vanilla unbreaking: 3 enchantment!"
        }
        if ($chestJson -match 'small_logic_tweaks:timber') {
            $failures += "Chest: Residual small_logic_tweaks:timber enchantment tag still present!"
        }
    } else {
        $failures += "Failed to capture chest NBT output from server log."
    }

    # 2. Assert Armor Stand Equipment
    $asMatch = $LogText | Select-String -Pattern 'Armor Stand has the following entity data: (\{.*\})'
    if ($asMatch) {
        $asNbt = $asMatch.Matches[0].Groups[1].Value
        if ($asNbt -notmatch 'mainhand:\s*\{\s*count:\s*1,\s*id:\s*"minecraft:diamond_axe"\s*\}') {
            $failures += "Armor stand: Mainhand lost diamond_axe or equipment mapping corrupted."
        }
        if ($asNbt -match 'small_logic_tweaks:timber') {
            $failures += "Armor stand: Mainhand axe retained residual timber enchantment!"
        }
    } else {
        $failures += "Failed to capture Armor Stand entity NBT from server log."
    }

    # 3. Assert Cookable Beef Item Entity
    $beefMatch = $LogText | Select-String -Pattern 'Raw Beef has the following entity data: (\{.*\})'
    if ($beefMatch) {
        $beefNbt = $beefMatch.Matches[0].Groups[1].Value
        if ($beefNbt -notmatch 'id:\s*"minecraft:beef"') {
            $failures += "Cookable item entity: Corrupted item data or unexpected ID."
        }
    } else {
        # Check if wheat seeds or item was queried
        $itemMatch = $LogText | Select-String -Pattern 'has the following entity data:.*id:\s*"minecraft:beef"'
        if (-not $itemMatch) {
            $failures += "Failed to locate valid minecraft:beef item entity on magma block."
        }
    }

    if ($failures.Count -eq 0) {
        Write-Host "   -> All automated data assertions PASSED." -ForegroundColor Green
        return $true
    }

    Write-Host "   -> Assertion failures detected ($($failures.Count)):" -ForegroundColor Red
    foreach ($f in $failures) {
        Write-Host "      [FAIL] $f" -ForegroundColor Red
    }
    return $false
}

# ==============================================================================
# Main Pipeline Execution
# ==============================================================================

$stopwatch = [System.Diagnostics.Stopwatch]::StartNew()
$summaryResults = [ordered]@{}

try {
    # Resolve target versions from gradle.properties
    $props = Read-GradleProperties $RepoRoot
    if ($null -eq $props) { throw "Unable to read gradle.properties." }

    $mcVersion = if ($MinecraftVersion) { $MinecraftVersion } else { $props["minecraft_version"] }
    $loaderVersion = if ($LoaderVersion) { $LoaderVersion } else { $props["loader_version"] }
    $fabricApiVersion = $props["fabric_api_version"]

    Write-Host "========================================================" -ForegroundColor Cyan
    Write-Host " CLEAN MOD REMOVAL VERIFICATION PIPELINE " -ForegroundColor Cyan
    Write-Host " Target MC Version:     $mcVersion" -ForegroundColor Gray
    Write-Host " Fabric Loader Version: $loaderVersion" -ForegroundColor Gray
    Write-Host "========================================================" -ForegroundColor Cyan

    $modJarPath = Resolve-ModJar
    Write-Host "[Artifact] Testing mod jar: $modJarPath" -ForegroundColor Cyan

    $serverEnv = Ensure-ServerEnvironment $mcVersion $loaderVersion $fabricApiVersion
    Prepare-Workspace $serverEnv.ServerDir $serverEnv.FabricApiJar

    # --------------------------------------------------------------------------
    # PHASE 1: Modded World State Generation & Snapshotting
    # --------------------------------------------------------------------------
    Write-Host "`n>>> PHASE 1: Generating modded world state and taking pristine snapshot..." -ForegroundColor Cyan
    $modsDir = Join-Path $TestWorkDir "mods"
    $modInTest = Copy-Item $modJarPath $modsDir -PassThru

    $phase1Commands = @(
        "forceload add 0 0",
        'setblock 0 100 0 minecraft:chest{Items:[{Slot:0b,id:"minecraft:enchanted_book",count:3,components:{"minecraft:stored_enchantments":{"small_logic_tweaks:timber":1}}},{Slot:1b,id:"minecraft:diamond_axe",count:1,components:{"minecraft:enchantments":{"small_logic_tweaks:timber":1}}},{Slot:2b,id:"minecraft:netherite_axe",count:1,components:{"minecraft:enchantments":{"small_logic_tweaks:timber":1,"minecraft:unbreaking":3}}}]} replace',
        'summon armor_stand 0 101 0 {NoGravity:1b,ShowArms:1b,equipment:{mainhand:{id:"minecraft:diamond_axe",count:1,components:{"minecraft:enchantments":{"small_logic_tweaks:timber":1}}}},HandItems:[{id:"minecraft:diamond_axe",count:1,components:{"minecraft:enchantments":{"small_logic_tweaks:timber":1}}},{}]}',
        'setblock 0 100 2 minecraft:magma_block replace',
        'summon item 0 101.2 2 {Item:{id:"minecraft:beef",count:1}}',
        "time set day"
    )

    $phase1Result = Execute-ServerInstance "Phase 1 (Modded State Gen)" "phase1_modded" "-jar fabric-server-launch.jar" $phase1Commands 6
    if (-not $phase1Result.Success) {
        throw "Phase 1 generation failed: Server encountered startup or crash error."
    }

    # Capture pristine world snapshot
    $worldDir = Join-Path $TestWorkDir "world"
    $snapshotDir = Join-Path $TestWorkDir "world_snapshot"
    if (Test-Path $snapshotDir) { cmd /c "rmdir /s /q `"$snapshotDir`"" | Out-Null }
    Write-Host "[Snapshot] Capturing untouched modded world snapshot..." -ForegroundColor Cyan
    Copy-Item -Path $worldDir -Destination $snapshotDir -Recurse

    $summaryResults["Phase 1 (Modded Gen)"] = "PASSED"

    # --------------------------------------------------------------------------
    # PHASE 2: Clean Removal Verification across N Target Environments
    # --------------------------------------------------------------------------
    Write-Host "`n>>> PHASE 2: Verifying clean removal across target environments..." -ForegroundColor Cyan

    $environments = @(
        @{
            Id       = "env_fabric_loader"
            Name     = "Environment 1: Fabric Loader (Unmodded)"
            JavaArgs = "-jar fabric-server-launch.jar"
            Prepare  = {
                # Restore pristine snapshot
                cmd /c "rmdir /s /q `"$worldDir`"" | Out-Null
                Copy-Item -Path $snapshotDir -Destination $worldDir -Recurse
                # Remove mod jar from mods directory (keep fabric-api)
                Get-ChildItem -Path $modsDir -Filter "small-logic-tweaks-*.jar" | Remove-Item -Force
            }
        }
    )

    if ($TestVanilla) {
        $environments += @{
            Id       = "env_pure_vanilla"
            Name     = "Environment 2: Pure Vanilla Server (server.jar)"
            JavaArgs = "-jar server.jar"
            Prepare  = {
                # Restore pristine snapshot
                cmd /c "rmdir /s /q `"$worldDir`"" | Out-Null
                Copy-Item -Path $snapshotDir -Destination $worldDir -Recurse
            }
        }
    }

    $phase2Queries = @(
        "forceload query 0 0",
        "data get block 0 100 0 Items",
        "data get entity @e[type=armor_stand,limit=1]",
        "data get entity @e[type=item,limit=1] Item"
    )

    foreach ($envItem in $environments) {
        Write-Host "`n--- Running $($envItem.Name) ---" -ForegroundColor Cyan
        & $envItem.Prepare

        $envResult = Execute-ServerInstance $envItem.Name $envItem.Id $envItem.JavaArgs $phase2Queries $TickDurationSeconds
        if (-not $envResult.Success) {
            $summaryResults[$envItem.Name] = "FAILED (Crash / Boot error)"
            continue
        }

        $audit = Audit-LogOutput $envResult.Log $envItem.Name
        if ($audit.CriticalErrors.Count -gt 0) {
            Write-Host "[$($envItem.Name)] FAILED: Unexpected critical errors detected ($($audit.CriticalErrors.Count)):" -ForegroundColor Red
            $audit.CriticalErrors | Select-Object -First 10 | Out-Host
            $summaryResults[$envItem.Name] = "FAILED (Log Audit Error)"
            continue
        }

        if ($audit.ExpectedDegradation.Count -gt 0) {
            Write-Host "[$($envItem.Name)] Expected registry degradation recorded: $($audit.ExpectedDegradation.Count) lines (timber enchantment stripped by vanilla)." -ForegroundColor DarkYellow
        }

        $assertPassed = Assert-WorldData $envResult.Log $envItem.Name
        if (-not $assertPassed) {
            $summaryResults[$envItem.Name] = "FAILED (NBT Assertion Mismatch)"
            continue
        }

        $summaryResults[$envItem.Name] = "PASSED (Clean, no crashes, data intact)"
    }

    # --------------------------------------------------------------------------
    # Report & Summary
    # --------------------------------------------------------------------------
    $stopwatch.Stop()
    Write-Host "`n========================================================" -ForegroundColor Green
    Write-Host " CLEAN MOD REMOVAL VERIFICATION SUMMARY " -ForegroundColor Green
    Write-Host "========================================================" -ForegroundColor Green
    foreach ($key in $summaryResults.Keys) {
        $val = $summaryResults[$key]
        $color = if ($val -like "PASSED*") { "Green" } else { "Red" }
        Write-Host (" {0,-40} : {1}" -f $key, $val) -ForegroundColor $color
    }
    Write-Host " Total Elapsed Time: $([math]::Round($stopwatch.Elapsed.TotalSeconds, 1)) s" -ForegroundColor Cyan
    Write-Host " Degradation Note:   Custom enchantment (timber) gracefully stripped; vanilla items & enchantments preserved." -ForegroundColor Yellow
    Write-Host "========================================================`n" -ForegroundColor Green

    $anyFailed = @($summaryResults.Values | Where-Object { $_ -notlike "PASSED*" }).Count -gt 0

    if (-not $KeepWorld -and -not $anyFailed) {
        cmd /c "rmdir /s /q `"$TestWorkDir`"" | Out-Null
    } else {
        Write-Host "World test directory preserved at: $TestWorkDir" -ForegroundColor Yellow
    }

    if ($anyFailed) { exit 1 } else { exit 0 }
} catch {
    Write-Host "`nFATAL PIPELINE ERROR: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}
