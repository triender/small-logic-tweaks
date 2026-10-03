# scripts/deploy.ps1
# Config-driven, verify-first release pipeline for every Minecraft version branch.
#
# Usage (from any directory):
#   .\scripts\deploy.ps1 -DryRun   # verify every branch and print the release preview; nothing is pushed or uploaded
#   .\scripts\deploy.ps1           # verify every branch, then release the branches marked "deploy": true
#
# WHAT is released and WHERE is decided only by scripts/deploy_config.json:
#   platforms.modrinth / platforms.github / platforms.git_push -> enabled + target
#   branches[]            -> every supported branch (all of them are verified)
#   branches[].deploy     -> released in this run
#   branches[].loaders    -> Modrinth loader tags (default ["fabric"]); "quilt" adds a Quilt server boot test
#   quilt_test_loader     -> Quilt Loader version used by that boot test
#
# ARCHITECTURAL RULES (see .agents/AGENTS.md):
# 1. Phase 1 verifies EVERY branch in the config: changelog, clean compile, JUnit, GameTest, packaging,
#    plus the Quilt boot test when the branch declares the "quilt" loader.
# 2. If any branch fails Phase 1, the pipeline stops: nothing is pushed or uploaded.
# 3. Phase 2 releases only "deploy": true branches, and only the exact commit verified in Phase 1.
# 4. Each branch is built from its committed state in its own git worktree outside the repository,
#    so the current checkout is never switched and uncommitted edits never reach a release jar.

param(
    [switch]$DryRun
)

[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
$ProgressPreference = "SilentlyContinue"

$RepoRoot = Split-Path -Parent $PSScriptRoot
$ConfigPath = Join-Path $PSScriptRoot "deploy_config.json"
$WorkRoot = Join-Path $env:LOCALAPPDATA "small-logic-tweaks-deploy"
$WorktreeRoot = Join-Path $WorkRoot "worktrees"
$QuiltRoot = Join-Path $WorkRoot "quilt"
$QuiltInstallerVersion = "0.15.1"
$KnownLoaders = @("fabric", "quilt")
$UserAgent = "triender/small-logic-tweaks-deploy/2.0"
$VerifyStages = @("Changelog", "Compile", "JUnit", "GameTest", "Package", "Quilt")
$ReleaseStages = @("Push", "Modrinth", "GitHub")

# ==============================================================================
# Helpers
# ==============================================================================

function Stop-Pipeline([string]$Message) {
    Write-Host "ERROR: $Message" -ForegroundColor Red
    exit 1
}

function Get-Secret([string]$Name) {
    foreach ($scope in @("Process", "User", "Machine")) {
        $value = [Environment]::GetEnvironmentVariable($Name, $scope)
        if (-not [string]::IsNullOrEmpty($value)) { return $value }
    }
    return $null
}

function Read-GradleProperties([string]$Dir) {
    $props = @{}
    $file = Join-Path $Dir "gradle.properties"
    if (-not (Test-Path $file)) { return $null }
    foreach ($line in Get-Content $file) {
        if ($line -match '^\s*([^#!\s][^=]*?)\s*=\s*(.*?)\s*$') { $props[$Matches[1]] = $Matches[2] }
    }
    return $props
}

function Get-NormalizedPath([string]$Path) {
    return [IO.Path]::GetFullPath($Path).TrimEnd('\', '/').Replace('\', '/').ToLowerInvariant()
}

# Reads a file that another process (e.g. a running server) may still be writing.
function Read-SharedText([string]$Path) {
    if (-not (Test-Path $Path)) { return "" }
    try {
        $fs = [IO.File]::Open($Path, [IO.FileMode]::Open, [IO.FileAccess]::Read, [IO.FileShare]::ReadWrite -bor [IO.FileShare]::Delete)
        try { return (New-Object IO.StreamReader($fs)).ReadToEnd() } finally { $fs.Dispose() }
    } catch {
        return ""
    }
}

function Get-HttpStatus($ErrorRecord) {
    $response = $ErrorRecord.Exception.Response
    if ($null -ne $response) { return [int]$response.StatusCode }
    return $null
}

# Release parameters reach build.gradle as project properties via ORG_GRADLE_PROJECT_* variables
# (avoids gradlew.bat mangling ',' and '=' in command-line arguments).
function Set-ReleaseProperties($Branch) {
    $env:ORG_GRADLE_PROJECT_modrinthProjectId = $ModrinthProject
    $env:ORG_GRADLE_PROJECT_modrinthLoaders = ($Branch.Loaders -join ",")
}

function Clear-ReleaseProperties {
    Remove-Item Env:\ORG_GRADLE_PROJECT_modrinthProjectId -ErrorAction SilentlyContinue
    Remove-Item Env:\ORG_GRADLE_PROJECT_modrinthLoaders -ErrorAction SilentlyContinue
}

function Invoke-Gradle($Branch, [string[]]$Tasks) {
    Set-ReleaseProperties $Branch
    Push-Location $Branch.Worktree
    try {
        & .\gradlew.bat @Tasks --no-daemon --console=plain | Out-Host
        return ($LASTEXITCODE -eq 0)
    } finally {
        Pop-Location
    }
}

# Runs one verification stage and records Passed/Failed for the summary table.
function Invoke-Stage($Branch, [string]$Stage, [scriptblock]$Action) {
    Write-Host "[$($Branch.Name)] $Stage..." -ForegroundColor Cyan
    $ok = [bool](& $Action)
    $Branch.Status[$Stage] = if ($ok) { "Passed" } else { "Failed" }
    if (-not $ok) { Write-Host "[$($Branch.Name)] $Stage FAILED." -ForegroundColor Red }
    return $ok
}

function Set-SkippedStages($Branch, [string[]]$Stages) {
    foreach ($stage in $Stages) {
        if ($Branch.Status[$stage] -eq "-") { $Branch.Status[$stage] = "Skipped" }
    }
}

# Creates (or resets) a detached worktree at the branch's verified commit, outside the repository.
function Initialize-Worktree($Branch) {
    $path = Join-Path $WorktreeRoot $Branch.Name
    $registered = @(git -C $RepoRoot worktree list --porcelain |
        Where-Object { $_ -like "worktree *" } |
        ForEach-Object { Get-NormalizedPath $_.Substring(9) })

    if ($registered -contains (Get-NormalizedPath $path)) {
        git -C $path checkout --quiet --detach --force $Branch.Sha | Out-Host
        if ($LASTEXITCODE -ne 0) { return $false }
        # Keep .gradle (Loom project cache) warm; wipe build outputs and run directories.
        git -C $path clean -fdxq -e .gradle | Out-Host
    } else {
        git -C $RepoRoot worktree prune | Out-Host
        if (Test-Path $path) { Remove-Item -Recurse -Force $path }
        New-Item -ItemType Directory -Force $WorktreeRoot | Out-Null
        git -C $RepoRoot worktree add --detach --force $path $Branch.Sha | Out-Host
    }
    if ($LASTEXITCODE -ne 0) { return $false }
    $Branch.Worktree = $path
    return $true
}

function Test-VersionAlignment($Branch) {
    $props = Read-GradleProperties $Branch.Worktree
    if ($null -eq $props) {
        Write-Host "gradle.properties not found on branch $($Branch.Name)." -ForegroundColor Red
        return $false
    }
    $Branch.ModVersion = $props["mod_version"]
    if ($props["minecraft_version"] -ne $Branch.Name) {
        Write-Host "Branch is '$($Branch.Name)' but minecraft_version is '$($props["minecraft_version"])'." -ForegroundColor Red
        return $false
    }
    if ($Branch.ModVersion -notlike "*-mc$($Branch.Name)") {
        Write-Host "mod_version '$($Branch.ModVersion)' does not end with -mc$($Branch.Name)." -ForegroundColor Red
        return $false
    }
    return $true
}

function Read-ReleaseInfo($Branch) {
    $path = Join-Path $Branch.Worktree "build\release\info.json"
    if (-not (Test-Path $path)) {
        Write-Host "build/release/info.json missing. Branch $($Branch.Name) needs the exportReleaseInfo task in build.gradle (sync it from 26.3)." -ForegroundColor Red
        return $false
    }
    $Branch.Info = Get-Content -Raw -Encoding UTF8 $path | ConvertFrom-Json
    # Multi-module branches report a repo-relative path (fabric/build/libs/...); older single-module branches a bare file name
    $jar = "$($Branch.Info.jar)"
    $Branch.JarPath = if ($jar -match '[\\/]') { Join-Path $Branch.Worktree $jar } else { Join-Path $Branch.Worktree "build\libs\$jar" }
    if (-not (Test-Path $Branch.JarPath)) {
        Write-Host "Jar not found: $($Branch.JarPath)" -ForegroundColor Red
        return $false
    }
    $expected = ($Branch.Loaders -join ",")
    $actual = (@($Branch.Info.loaders) -join ",")
    if ($actual -ne $expected) {
        Write-Host "build.gradle reports loaders '$actual' but config asks for '$expected'." -ForegroundColor Red
        return $false
    }
    return $true
}

# Boots a headless server on Quilt Loader with Fabric API and the freshly built jar.
# Passes only if the server reaches "Done (" with small_logic_tweaks in the loaded mod list.
function Test-QuiltBoot($Branch) {
    $props = Read-GradleProperties $Branch.Worktree
    $mcVersion = $props["minecraft_version"]
    $fabricApi = $props["fabric_api_version"]
    $loader = $QuiltTestLoader
    $java = if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME "bin\java.exe"))) { Join-Path $env:JAVA_HOME "bin\java.exe" } else { (Get-Command java -ErrorAction SilentlyContinue).Source }
    if (-not $java) { Write-Host "java not found (set JAVA_HOME)." -ForegroundColor Red; return $false }

    New-Item -ItemType Directory -Force $QuiltRoot | Out-Null
    $installer = Join-Path $QuiltRoot "quilt-installer-$QuiltInstallerVersion.jar"
    $fabricApiJar = Join-Path $QuiltRoot "fabric-api-$fabricApi.jar"
    $serverDir = Join-Path $QuiltRoot "server-$mcVersion-$loader"
    try {
        if (-not (Test-Path $installer)) {
            Invoke-WebRequest -UseBasicParsing -UserAgent $UserAgent -OutFile $installer `
                -Uri "https://maven.quiltmc.org/repository/release/org/quiltmc/quilt-installer/$QuiltInstallerVersion/quilt-installer-$QuiltInstallerVersion.jar"
        }
        if (-not (Test-Path $fabricApiJar)) {
            $encoded = [Uri]::EscapeDataString($fabricApi)
            Invoke-WebRequest -UseBasicParsing -UserAgent $UserAgent -OutFile $fabricApiJar `
                -Uri "https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/$encoded/fabric-api-$encoded.jar"
        }
    } catch {
        Write-Host "Quilt test download failed: $($_.Exception.Message)" -ForegroundColor Red
        Remove-Item $installer, $fabricApiJar -ErrorAction SilentlyContinue
        return $false
    }

    if (-not (Test-Path (Join-Path $serverDir "quilt-server-launch.jar"))) {
        Write-Host "Installing Quilt Loader $loader server for Minecraft $mcVersion..." -ForegroundColor Cyan
        & $java -jar $installer install server $mcVersion $loader --download-server "--install-dir=$serverDir" | Out-Host
        if ($LASTEXITCODE -ne 0) { return $false }
    }

    foreach ($dir in @("mods", "world", "logs", "config", "crash-reports")) {
        Remove-Item -Recurse -Force (Join-Path $serverDir $dir) -ErrorAction SilentlyContinue
    }
    $modsDir = Join-Path $serverDir "mods"
    New-Item -ItemType Directory -Force $modsDir | Out-Null
    Copy-Item $fabricApiJar $modsDir
    Copy-Item $Branch.JarPath $modsDir
    # Throwaway local test server: accept the Minecraft EULA so it can boot.
    Set-Content -Encoding ASCII (Join-Path $serverDir "eula.txt") "eula=true"
    Set-Content -Encoding ASCII (Join-Path $serverDir "server.properties") @("server-port=25599", "online-mode=false")

    $psi = New-Object System.Diagnostics.ProcessStartInfo
    $psi.FileName = $java
    $psi.Arguments = "-Xmx2G -jar quilt-server-launch.jar nogui"
    $psi.WorkingDirectory = $serverDir
    $psi.UseShellExecute = $false
    $psi.RedirectStandardInput = $true
    $psi.RedirectStandardOutput = $true
    $psi.RedirectStandardError = $true
    $psi.CreateNoWindow = $true

    Write-Host "Booting Quilt $loader server (MC $mcVersion, Fabric API $fabricApi)..." -ForegroundColor Cyan
    $proc = [System.Diagnostics.Process]::Start($psi)
    $stdout = $proc.StandardOutput.ReadToEndAsync()
    $stderr = $proc.StandardError.ReadToEndAsync()
    $logFile = Join-Path $serverDir "logs\latest.log"
    $deadline = (Get-Date).AddSeconds(300)
    $ready = $false
    while (-not $proc.HasExited -and (Get-Date) -lt $deadline) {
        Start-Sleep -Seconds 2
        if ((Read-SharedText $logFile) -match 'Done \(') { $ready = $true; break }
    }
    if (-not $proc.HasExited) {
        try { $proc.StandardInput.WriteLine("stop"); $proc.StandardInput.Flush() } catch { }
        if (-not $proc.WaitForExit(60000)) { $proc.Kill() }
    }
    $proc.WaitForExit()
    $console = $stdout.Result + $stderr.Result
    $consoleFile = Join-Path $serverDir "console.log"
    Set-Content -Encoding UTF8 $consoleFile $console

    $modLoaded = $console -match 'small_logic_tweaks'
    if ($ready -and $modLoaded) {
        Write-Host "Quilt boot test passed." -ForegroundColor Green
        return $true
    }
    Write-Host "Quilt boot test failed (ready=$ready, modLoaded=$modLoaded). Full log: $consoleFile" -ForegroundColor Red
    ($console -split "`r?`n" | Where-Object { $_ -match 'ERROR|Exception|Caused by|Incompatible' } | Select-Object -First 15) | Out-Host
    return $false
}

# $true = exists, $false = not found, $null = could not tell
function Test-ModrinthVersion([string]$Version) {
    try {
        Invoke-RestMethod -UserAgent $UserAgent -TimeoutSec 15 `
            -Uri "https://api.modrinth.com/v2/project/$ModrinthProject/version/$([Uri]::EscapeDataString($Version))" | Out-Null
        return $true
    } catch {
        if ((Get-HttpStatus $_) -eq 404) { return $false }
        Write-Host "Could not query Modrinth ($($_.Exception.Message))." -ForegroundColor Yellow
        return $null
    }
}

function Get-GitHubHeaders {
    $headers = @{ "Accept" = "application/vnd.github+json"; "X-GitHub-Api-Version" = "2022-11-28" }
    if ($GitHubToken) { $headers["Authorization"] = "Bearer $GitHubToken" }
    return $headers
}

# $true = published release exists for the tag, $false = not found, $null = could not tell
function Test-GitHubRelease([string]$Tag) {
    try {
        Invoke-RestMethod -UserAgent $UserAgent -TimeoutSec 15 -Headers (Get-GitHubHeaders) `
            -Uri "https://api.github.com/repos/$GitHubRepo/releases/tags/$([Uri]::EscapeDataString($Tag))" | Out-Null
        return $true
    } catch {
        if ((Get-HttpStatus $_) -eq 404) { return $false }
        Write-Host "Could not query GitHub ($($_.Exception.Message))." -ForegroundColor Yellow
        return $null
    }
}

function Publish-Git($Branch, [string]$Tag) {
    $existing = git -C $RepoRoot rev-parse --verify --quiet "refs/tags/$Tag^{commit}"
    if ($existing) {
        if ("$existing".Trim() -ne $Branch.Sha) {
            Write-Host "Tag $Tag already points to $("$existing".Trim()), not $($Branch.Sha). Refusing to move it." -ForegroundColor Red
            return $false
        }
    } else {
        git -C $RepoRoot tag -a $Tag $Branch.Sha -m $Branch.Info.versionName | Out-Host
        if ($LASTEXITCODE -ne 0) { return $false }
    }
    git -C $RepoRoot push $GitRemote "refs/heads/$($Branch.Name):refs/heads/$($Branch.Name)" "refs/tags/$($Tag):refs/tags/$($Tag)" | Out-Host
    return ($LASTEXITCODE -eq 0)
}

# Creates the release as a draft, attaches the jar, then publishes it.
# On failure the draft is deleted so a rerun starts clean (drafts are not found by tag lookups).
function Publish-GitHubRelease($Branch, [string]$Tag) {
    $api = "https://api.github.com/repos/$GitHubRepo"
    $json = @{
        tag_name         = $Tag
        target_commitish = $Branch.Sha
        name             = $Branch.Info.versionName
        body             = $Branch.Info.changelog
        draft            = $true
        prerelease       = ($Branch.Info.versionType -ne "release")
    } | ConvertTo-Json
    $release = Invoke-RestMethod -Method Post -Uri "$api/releases" -UserAgent $UserAgent -Headers (Get-GitHubHeaders) `
        -ContentType "application/json; charset=utf-8" -Body ([Text.Encoding]::UTF8.GetBytes($json))
    try {
        $uploadUrl = ($release.upload_url -replace '\{.*\}$', '') + "?name=" + [Uri]::EscapeDataString((Split-Path -Leaf $Branch.JarPath))
        Invoke-RestMethod -Method Post -Uri $uploadUrl -UserAgent $UserAgent -Headers (Get-GitHubHeaders) `
            -ContentType "application/java-archive" -InFile $Branch.JarPath | Out-Null
        $publish = @{ draft = $false } | ConvertTo-Json
        Invoke-RestMethod -Method Patch -Uri "$api/releases/$($release.id)" -UserAgent $UserAgent -Headers (Get-GitHubHeaders) `
            -ContentType "application/json; charset=utf-8" -Body ([Text.Encoding]::UTF8.GetBytes($publish)) | Out-Null
    } catch {
        try { Invoke-RestMethod -Method Delete -Uri "$api/releases/$($release.id)" -UserAgent $UserAgent -Headers (Get-GitHubHeaders) | Out-Null } catch { }
        throw
    }
}

function Write-Cell([string]$Text, [int]$Width) {
    $color = "Gray"
    if ($Text -match '^(Passed|Success)') { $color = "Green" }
    elseif ($Text -match '^Failed') { $color = "Red" }
    elseif ($Text -match '^(Planned|Exists|Skipped \(Exists\))') { $color = "Yellow" }
    elseif ($Text -match '^(-|Off|Skipped)') { $color = "DarkGray" }
    Write-Host " $($Text.PadRight($Width)) |" -ForegroundColor $color -NoNewline
}

function Write-Summary {
    Write-Host "`n======================================================================================================" -ForegroundColor Green
    Write-Host " VERIFICATION (all branches)" -ForegroundColor Green
    Write-Host "======================================================================================================" -ForegroundColor Green
    Write-Host (" {0,-8} | {1,-8} | {2,-26} | {3,-9} | {4,-9} | {5,-9} | {6,-9} | {7,-9} | {8,-9} |" -f "Branch", "Commit", "Version [type]", "Changelog", "Compile", "JUnit", "GameTest", "Package", "Quilt")
    foreach ($b in $Branches) {
        $version = if ($b.Info) { "$($b.Info.modVersion) [$($b.Info.versionType)]" } elseif ($b.ModVersion) { $b.ModVersion } else { "N/A" }
        $commit = if ($b.Sha) { $b.Sha.Substring(0, 7) } else { "N/A" }
        Write-Host (" {0,-8} | {1,-8} | {2,-26} |" -f $b.Name, $commit, $version) -NoNewline
        foreach ($stage in $VerifyStages) { Write-Cell $b.Status[$stage] 9 }
        Write-Host ""
    }

    Write-Host "`n RELEASE ($(if ($DryRun) { 'dry run: planned actions, nothing was sent' } else { 'deploy: true branches' }))" -ForegroundColor Green
    Write-Host (" {0,-8} | {1,-16} | {2,-16} | {3,-16} | {4,-16} |" -f "Branch", "Loaders", "Git push + tag", "Modrinth", "GitHub")
    foreach ($b in $Branches) {
        if (-not $b.Deploy) { continue }
        Write-Host (" {0,-8} | {1,-16} |" -f $b.Name, ($b.Loaders -join ",")) -NoNewline
        foreach ($stage in $ReleaseStages) { Write-Cell $b.Status[$stage] 16 }
        Write-Host ""
    }
    if (-not @($Branches | Where-Object { $_.Deploy })) { Write-Host " (no branch has `"deploy`": true)" -ForegroundColor DarkGray }

    foreach ($b in $Branches) {
        if (-not ($b.Deploy -and $b.Info)) { continue }
        $lines = @($b.Info.changelog -split "`n")
        Write-Host "`n [$($b.Name)] v$($b.Info.modVersion) changelog ($($lines.Count) lines): $($lines[0])" -ForegroundColor Cyan
    }
    Write-Host "======================================================================================================" -ForegroundColor Green
}

# ==============================================================================
# Preflight: load and validate config, tokens and git state (fails before any build)
# ==============================================================================

if (-not (Test-Path $ConfigPath)) { Stop-Pipeline "Config not found: $ConfigPath" }
try {
    $Config = Get-Content -Raw -Encoding UTF8 $ConfigPath | ConvertFrom-Json
} catch {
    Stop-Pipeline "Invalid JSON in ${ConfigPath}: $($_.Exception.Message)"
}

$ModrinthEnabled = [bool]$Config.platforms.modrinth.enabled
$GitHubEnabled = [bool]$Config.platforms.github.enabled
$GitPushEnabled = [bool]$Config.platforms.git_push.enabled
$ModrinthProject = if ($Config.platforms.modrinth.project) { "$($Config.platforms.modrinth.project)" } else { "small-logic-tweak" }
$GitHubRepo = "$($Config.platforms.github.repo)"
$GitRemote = if ($Config.platforms.git_push.remote) { "$($Config.platforms.git_push.remote)" } else { "origin" }
$QuiltTestLoader = "$($Config.quilt_test_loader)"

$Branches = @()
foreach ($entry in @($Config.branches)) {
    if (-not $entry.name) { Stop-Pipeline "Every entry in 'branches' needs a 'name'." }
    $name = "$($entry.name)"
    if (@($Branches | Where-Object { $_.Name -eq $name })) { Stop-Pipeline "Branch '$name' is listed twice." }
    $loaders = if ($entry.loaders) { @($entry.loaders | ForEach-Object { "$_".Trim().ToLowerInvariant() }) } else { @("fabric") }
    foreach ($loader in $loaders) {
        if ($KnownLoaders -notcontains $loader) { Stop-Pipeline "Branch '$name': unknown loader '$loader' (allowed: $($KnownLoaders -join ', '))." }
    }
    if ($loaders -notcontains "fabric") { Stop-Pipeline "Branch '$name': loaders must include 'fabric'." }

    $status = [ordered]@{}
    foreach ($stage in $VerifyStages + $ReleaseStages) { $status[$stage] = "-" }
    $Branches += [pscustomobject]@{
        Name       = $name
        Deploy     = [bool]$entry.deploy
        Loaders    = $loaders
        Sha        = $null
        Worktree   = $null
        ModVersion = $null
        Info       = $null
        JarPath    = $null
        Status     = $status
    }
}
if ($Branches.Count -eq 0) { Stop-Pipeline "No branches listed in $ConfigPath." }
$DeployBranches = @($Branches | Where-Object { $_.Deploy })

if (@($Branches | Where-Object { $_.Loaders -contains "quilt" }) -and -not $QuiltTestLoader) {
    Stop-Pipeline "A branch declares the 'quilt' loader but 'quilt_test_loader' is not set."
}
if ($GitHubEnabled -and -not $GitPushEnabled) {
    Stop-Pipeline "platforms.github requires platforms.git_push: the release tag must exist on the remote first."
}
if ($GitHubEnabled -and $GitHubRepo -notmatch '^[^/\s]+/[^/\s]+$') {
    Stop-Pipeline "platforms.github.repo must look like 'owner/repo'."
}
if (-not $DryRun) {
    if ($DeployBranches.Count -eq 0) { Stop-Pipeline "No branch has `"deploy`": true. Nothing to release (use -DryRun to only verify)." }
    if (-not ($ModrinthEnabled -or $GitHubEnabled -or $GitPushEnabled)) { Stop-Pipeline "Every platform is disabled in $ConfigPath." }
}

$ModrinthToken = Get-Secret "MODRINTH_TOKEN"
$GitHubToken = Get-Secret "GITHUB_TOKEN"
if (-not $DryRun) {
    if ($ModrinthEnabled -and -not $ModrinthToken) { Stop-Pipeline "platforms.modrinth is enabled but MODRINTH_TOKEN is not set." }
    if ($GitHubEnabled -and -not $GitHubToken) { Stop-Pipeline "platforms.github is enabled but GITHUB_TOKEN is not set." }
}

foreach ($b in $Branches) {
    $sha = git -C $RepoRoot rev-parse --verify --quiet "refs/heads/$($b.Name)"
    if (-not $sha) { Stop-Pipeline "Local branch '$($b.Name)' does not exist." }
    $b.Sha = "$sha".Trim()
}

if ($GitPushEnabled -and $DeployBranches.Count -gt 0) {
    Write-Host "Fetching $GitRemote..." -ForegroundColor Cyan
    git -C $RepoRoot fetch $GitRemote --quiet | Out-Host
    if ($LASTEXITCODE -ne 0) {
        if ($DryRun) { Write-Host "WARNING: git fetch $GitRemote failed; remote checks skipped." -ForegroundColor Yellow }
        else { Stop-Pipeline "git fetch $GitRemote failed." }
    } else {
        foreach ($b in $DeployBranches) {
            $remoteSha = git -C $RepoRoot rev-parse --verify --quiet "refs/remotes/$GitRemote/$($b.Name)"
            if (-not $remoteSha) { continue }
            git -C $RepoRoot merge-base --is-ancestor "$remoteSha".Trim() $b.Sha
            if ($LASTEXITCODE -ne 0) { Stop-Pipeline "Branch '$($b.Name)' is behind or diverged from $GitRemote/$($b.Name). Pull/rebase first." }
        }
    }
}

$currentBranch = "$(git -C $RepoRoot symbolic-ref --short -q HEAD)".Trim()
if ($currentBranch -and @($DeployBranches | Where-Object { $_.Name -eq $currentBranch })) {
    $dirty = @(git -C $RepoRoot status --porcelain | Where-Object { $_ -notmatch 'scripts/deploy_config\.json$' })
    if ($dirty.Count -gt 0) {
        Write-Host "WARNING: '$currentBranch' has uncommitted changes. Only committed code ($($DeployBranches | Where-Object { $_.Name -eq $currentBranch } | ForEach-Object { $_.Sha.Substring(0, 7) })) is verified and released:" -ForegroundColor Yellow
        $dirty | ForEach-Object { Write-Host "  $_" -ForegroundColor Yellow }
    }
}

Write-Host "`nMode: $(if ($DryRun) { 'DRY RUN (verify + preview only)' } else { 'RELEASE' })" -ForegroundColor Cyan
Write-Host "Verify : $(($Branches | ForEach-Object { $_.Name }) -join ', ')" -ForegroundColor Cyan
Write-Host "Deploy : $(if ($DeployBranches.Count) { ($DeployBranches | ForEach-Object { $_.Name }) -join ', ' } else { '(none)' })" -ForegroundColor Cyan
Write-Host "Targets: Modrinth=$ModrinthEnabled ($ModrinthProject), GitHub=$GitHubEnabled ($GitHubRepo), GitPush=$GitPushEnabled ($GitRemote)" -ForegroundColor Cyan
Write-Host "Worktrees: $WorktreeRoot" -ForegroundColor Cyan

function Invoke-Pipeline {
    # ==============================================================================
    # PHASE 1: INDEPENDENT VERIFICATION GATE (EVERY BRANCH MUST PASS)
    # ==============================================================================
    Write-Host "`n==================================================================================" -ForegroundColor Magenta
    Write-Host " PHASE 1: INDEPENDENT VERIFICATION GATE (ALL BRANCHES MUST PASS)" -ForegroundColor Magenta
    Write-Host "==================================================================================" -ForegroundColor Magenta

    $verificationFailed = $false
    foreach ($b in $Branches) {
        Write-Host "`n--------------------------------------------------" -ForegroundColor Yellow
        Write-Host " Verifying branch: $($b.Name) @ $($b.Sha.Substring(0, 7))" -ForegroundColor Yellow
        Write-Host "--------------------------------------------------" -ForegroundColor Yellow

        $passed = (Invoke-Stage $b "Changelog" { (Initialize-Worktree $b) -and (Test-VersionAlignment $b) -and (Invoke-Gradle $b @("verifyChangelog")) }) -and
                  (Invoke-Stage $b "Compile" { Invoke-Gradle $b @("clean", "compileJava") }) -and
                  (Invoke-Stage $b "JUnit" { Invoke-Gradle $b @("cleanTest", "test") }) -and
                  (Invoke-Stage $b "GameTest" { Invoke-Gradle $b @("runGameTestServer") }) -and
                  (Invoke-Stage $b "Package" { (Invoke-Gradle $b @("jar", "exportReleaseInfo")) -and (Read-ReleaseInfo $b) })
        if ($passed -and ($b.Loaders -contains "quilt")) {
            $passed = Invoke-Stage $b "Quilt" { Test-QuiltBoot $b }
        }

        if ($passed) {
            Write-Host "Branch $($b.Name) VERIFICATION PASSED." -ForegroundColor Green
        } else {
            $verificationFailed = $true
            $applicable = @($VerifyStages | Where-Object { $_ -ne "Quilt" -or ($b.Loaders -contains "quilt") })
            Set-SkippedStages $b $applicable
        }
    }

    if ($verificationFailed) {
        $script:ExitCode = 1
        foreach ($b in $DeployBranches) { Set-SkippedStages $b $ReleaseStages }
        Write-Host "`n==================================================================================" -ForegroundColor Red
        Write-Host " CRITICAL: VERIFICATION GATE FAILED ON ONE OR MORE BRANCHES!" -ForegroundColor Red
        Write-Host " ABORTING. NO PACKAGES WERE UPLOADED OR PUSHED." -ForegroundColor Red
        Write-Host "==================================================================================" -ForegroundColor Red
        return
    }

    Write-Host "`n==================================================================================" -ForegroundColor Green
    Write-Host " ALL BRANCHES PASSED VERIFICATION (100%)." -ForegroundColor Green
    Write-Host "==================================================================================" -ForegroundColor Green

    # ==============================================================================
    # DRY RUN: preview what a real run would do (read-only remote queries)
    # ==============================================================================
    if ($DryRun) {
        foreach ($b in $DeployBranches) {
            $tag = "v$($b.Info.modVersion)"
            $b.Status["Push"] = if ($GitPushEnabled) { "Planned" } else { "Off" }
            if ($ModrinthEnabled) {
                $exists = Test-ModrinthVersion $b.Info.modVersion
                $b.Status["Modrinth"] = if ($exists -eq $true) { "Exists" } elseif ($exists -eq $false) { "Planned" } else { "Planned (?)" }
            } else { $b.Status["Modrinth"] = "Off" }
            if ($GitHubEnabled) {
                $exists = Test-GitHubRelease $tag
                $b.Status["GitHub"] = if ($exists -eq $true) { "Exists" } elseif ($exists -eq $false) { "Planned" } else { "Planned (?)" }
            } else { $b.Status["GitHub"] = "Off" }
        }
        return
    }

    # ==============================================================================
    # PHASE 2: RELEASE ("deploy": true branches only, exact verified commit)
    # ==============================================================================
    $env:MODRINTH_TOKEN = $ModrinthToken
    foreach ($b in $DeployBranches) {
        Write-Host "`n--------------------------------------------------" -ForegroundColor Yellow
        Write-Host " Releasing branch: $($b.Name) -> $($b.Info.modVersion) [$($b.Info.versionType)]" -ForegroundColor Yellow
        Write-Host "--------------------------------------------------" -ForegroundColor Yellow

        $tag = "v$($b.Info.modVersion)"
        $currentSha = "$(git -C $RepoRoot rev-parse --verify --quiet "refs/heads/$($b.Name)")".Trim()
        if ($currentSha -ne $b.Sha) {
            Write-Host "Branch $($b.Name) moved during verification ($($b.Sha.Substring(0, 7)) -> $currentSha). Not releasing an unverified commit." -ForegroundColor Red
            foreach ($stage in $ReleaseStages) { $b.Status[$stage] = "Failed (moved)" }
            $script:ExitCode = 1
            continue
        }

        # 1. Push branch + tag first, so published binaries always have their source on the remote.
        if ($GitPushEnabled) {
            Write-Host "Pushing $($b.Name) and tag $tag to $GitRemote..." -ForegroundColor Cyan
            if (Publish-Git $b $tag) {
                $b.Status["Push"] = "Success"
            } else {
                $b.Status["Push"] = "Failed"
                Set-SkippedStages $b $ReleaseStages
                $script:ExitCode = 1
                continue
            }
        } else { $b.Status["Push"] = "Off" }

        # 2. Modrinth (Minotaur task in build.gradle, fed by ORG_GRADLE_PROJECT_* release properties)
        if ($ModrinthEnabled) {
            if ((Test-ModrinthVersion $b.Info.modVersion) -eq $true) {
                Write-Host "Version $($b.Info.modVersion) already exists on Modrinth. Skipping upload." -ForegroundColor Yellow
                $b.Status["Modrinth"] = "Skipped (Exists)"
            } elseif (Invoke-Gradle $b @("modrinth")) {
                $b.Status["Modrinth"] = "Success"
            } else {
                $b.Status["Modrinth"] = "Failed"
                $script:ExitCode = 1
            }
        } else { $b.Status["Modrinth"] = "Off" }

        # 3. GitHub Release with the jar attached
        if ($GitHubEnabled) {
            $exists = Test-GitHubRelease $tag
            if ($exists -eq $true) {
                Write-Host "GitHub release $tag already exists. Skipping." -ForegroundColor Yellow
                $b.Status["GitHub"] = "Skipped (Exists)"
            } else {
                try {
                    Write-Host "Creating GitHub release $tag..." -ForegroundColor Cyan
                    Publish-GitHubRelease $b $tag
                    $b.Status["GitHub"] = "Success"
                } catch {
                    Write-Host "GitHub release failed: $($_.Exception.Message)" -ForegroundColor Red
                    $b.Status["GitHub"] = "Failed"
                    $script:ExitCode = 1
                }
            }
        } else { $b.Status["GitHub"] = "Off" }
    }
}

$ExitCode = 0
try {
    Invoke-Pipeline
}
finally {
    Clear-ReleaseProperties
    Write-Summary
}
exit $ExitCode
