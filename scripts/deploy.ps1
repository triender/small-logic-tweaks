# scripts/deploy.ps1
# Config-driven, verify-first release pipeline for every Minecraft version branch.
#
# Usage (from any directory):
#   .\scripts\deploy.ps1 -DryRun   # verify every branch and print the release preview; nothing is pushed or uploaded
#   .\scripts\deploy.ps1           # verify every branch, then release the branches marked "deploy": true
#
# WHAT is released and WHERE is decided only by scripts/deploy_config.json:
#   platforms.modrinth / curseforge / github / git_push -> enabled + target
#   branches[]            -> every supported branch (all of them are verified)
#   branches[].deploy     -> released in this run
#   branches[].loaders    -> "fabric", "neoforge" (one jar and one Modrinth/CurseForge version each) and
#                            "quilt" (extra tag on the Fabric jar); default ["fabric"]
#   quilt_test_loader     -> Quilt Loader version used by the Quilt boot test
#
# ARCHITECTURAL RULES (see .agents/AGENTS.md):
# 1. Phase 1 verifies EVERY branch in the config: changelog, clean compile, JUnit, GameTest, packaging,
#    plus a headless server boot test (with a forced mixin audit) for every declared "quilt"/"neoforge" loader.
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
$NeoForgeRoot = Join-Path $WorkRoot "neoforge"
$KnownLoaders = @("fabric", "quilt", "neoforge")
$UserAgent = "triender/small-logic-tweaks-deploy/2.0"
$VerifyStages = @("Changelog", "Compile", "JUnit", "GameTest", "Package", "Quilt", "NeoForge")
$ReleaseStages = @("Push", "GitHub")
$ArtifactStages = @("Modrinth", "CurseForge")
$MixinAuditMarker = "[Mixin audit] PASSED"

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
        Write-Host "build/release/info.json missing. Branch $($Branch.Name) needs the exportReleaseInfo task in build.gradle (sync it from the newest branch)." -ForegroundColor Red
        return $false
    }
    $Branch.Info = Get-Content -Raw -Encoding UTF8 $path | ConvertFrom-Json
    $expected = ($Branch.Loaders -join ",")
    $actual = (@($Branch.Info.loaders) -join ",")
    if ($actual -ne $expected) {
        Write-Host "build.gradle reports loaders '$actual' but config asks for '$expected'." -ForegroundColor Red
        return $false
    }

    # Branches not yet split into loader modules only report one Fabric jar (bare file name)
    $entries = @($Branch.Info.artifacts)
    if (-not $Branch.Info.artifacts) {
        $entries = @([pscustomobject]@{
            loader = "fabric"; jar = "build/libs/$($Branch.Info.jar)"; project = ""
            versionNumber = $Branch.Info.modVersion; versionName = $Branch.Info.versionName
            versionType = $Branch.Info.versionType; modrinthLoaders = @($Branch.Info.loaders)
        })
    }
    $Branch.Artifacts = @()
    foreach ($entry in $entries) {
        $status = [ordered]@{}
        foreach ($stage in $ArtifactStages) { $status[$stage] = "-" }
        $artifact = [pscustomobject]@{
            Loader          = "$($entry.loader)"
            JarPath         = Join-Path $Branch.Worktree "$($entry.jar)"
            Project         = "$($entry.project)"
            VersionNumber   = "$($entry.versionNumber)"
            VersionName     = "$($entry.versionName)"
            VersionType     = "$($entry.versionType)"
            ModrinthLoaders = @($entry.modrinthLoaders)
            Status          = $status
        }
        if (-not (Test-Path $artifact.JarPath)) {
            Write-Host "Jar not found for $($artifact.Loader): $($artifact.JarPath)" -ForegroundColor Red
            return $false
        }
        $Branch.Artifacts += $artifact
    }
    foreach ($loader in @($Branch.Loaders | Where-Object { $_ -ne "quilt" })) {
        if (-not @($Branch.Artifacts | Where-Object { $_.Loader -eq $loader })) {
            Write-Host "Branch $($Branch.Name) does not build a '$loader' jar (config asks for it)." -ForegroundColor Red
            return $false
        }
    }
    return Test-JarIsolation $Branch
}

# A loader jar must never carry the other loader's API classes (would crash or misbehave at load time).
function Test-JarIsolation($Branch) {
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $forbidden = @{ "fabric" = "net/neoforged/"; "neoforge" = "net/fabricmc/" }
    foreach ($artifact in $Branch.Artifacts) {
        $needle = $forbidden[$artifact.Loader]
        if (-not $needle) { continue }
        $zip = [IO.Compression.ZipFile]::OpenRead($artifact.JarPath)
        try {
            foreach ($entry in $zip.Entries) {
                if (-not $entry.FullName.EndsWith(".class")) { continue }
                $reader = New-Object IO.StreamReader($entry.Open(), [Text.Encoding]::GetEncoding(28591))
                try { $bytes = $reader.ReadToEnd() } finally { $reader.Dispose() }
                if ($bytes.Contains($needle)) {
                    Write-Host "$($artifact.Loader) jar references $needle in $($entry.FullName)." -ForegroundColor Red
                    return $false
                }
            }
        } finally {
            $zip.Dispose()
        }
    }
    return $true
}

function Get-Artifact($Branch, [string]$Loader) {
    return @($Branch.Artifacts | Where-Object { $_.Loader -eq $Loader })[0]
}

function Get-Java {
    if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME "bin\java.exe"))) { return Join-Path $env:JAVA_HOME "bin\java.exe" }
    return (Get-Command java -ErrorAction SilentlyContinue).Source
}

function Save-Download([string]$Uri, [string]$OutFile) {
    if (Test-Path $OutFile) { return $true }
    try {
        Invoke-WebRequest -UseBasicParsing -UserAgent $UserAgent -Uri $Uri -OutFile $OutFile
        return $true
    } catch {
        Write-Host "Download failed: $Uri ($($_.Exception.Message))" -ForegroundColor Red
        Remove-Item $OutFile -ErrorAction SilentlyContinue
        return $false
    }
}

# Starts a prepared server directory with -Dslt.mixinAudit=true and stops it once it is up.
# Passes only if the server reaches "Done (" AND the mod's mixin audit reports success, which proves the mod
# initialized and every mixin target could be transformed on this loader.
function Invoke-ServerBoot([string]$Label, [string]$ServerDir, [string]$JavaArgs) {
    $java = Get-Java
    foreach ($dir in @("world", "logs", "config", "crash-reports")) {
        Remove-Item -Recurse -Force (Join-Path $ServerDir $dir) -ErrorAction SilentlyContinue
    }
    # Throwaway local test server: accept the Minecraft EULA so it can boot.
    Set-Content -Encoding ASCII (Join-Path $ServerDir "eula.txt") "eula=true"
    Set-Content -Encoding ASCII (Join-Path $ServerDir "server.properties") @("server-port=25599", "online-mode=false")

    $psi = New-Object System.Diagnostics.ProcessStartInfo
    $psi.FileName = $java
    $psi.Arguments = "-Xmx2G -Dslt.mixinAudit=true $JavaArgs nogui"
    $psi.WorkingDirectory = $ServerDir
    $psi.UseShellExecute = $false
    $psi.RedirectStandardInput = $true
    $psi.RedirectStandardOutput = $true
    $psi.RedirectStandardError = $true
    $psi.CreateNoWindow = $true

    Write-Host "Booting $Label server..." -ForegroundColor Cyan
    $proc = [System.Diagnostics.Process]::Start($psi)
    $stdout = $proc.StandardOutput.ReadToEndAsync()
    $stderr = $proc.StandardError.ReadToEndAsync()
    $logFile = Join-Path $ServerDir "logs\latest.log"
    $deadline = (Get-Date).AddSeconds(300)
    $ready = $false
    $audited = $false
    while (-not $proc.HasExited -and (Get-Date) -lt $deadline) {
        Start-Sleep -Seconds 2
        $log = Read-SharedText $logFile
        if (-not $ready -and $log -match 'Done \(') {
            $ready = $true
            $deadline = (Get-Date).AddSeconds(60)   # the audit runs right after the server has started
        }
        if ($log.Contains($MixinAuditMarker)) { $audited = $true; break }
    }
    if (-not $proc.HasExited) {
        try { $proc.StandardInput.WriteLine("stop"); $proc.StandardInput.Flush() } catch { }
        if (-not $proc.WaitForExit(60000)) { $proc.Kill() }
    }
    $proc.WaitForExit()
    $console = $stdout.Result + $stderr.Result
    $consoleFile = Join-Path $ServerDir "console.log"
    Set-Content -Encoding UTF8 $consoleFile $console

    if ($ready -and $audited) {
        Write-Host "$Label boot test passed (server started, mixin audit passed)." -ForegroundColor Green
        return $true
    }
    Write-Host "$Label boot test failed (started=$ready, mixinAudit=$audited). Full log: $consoleFile" -ForegroundColor Red
    ($console -split "`r?`n" | Where-Object { $_ -match 'ERROR|Exception|Caused by|Incompatible|Mixin audit' } | Select-Object -First 15) | Out-Host
    return $false
}

# Quilt: Quilt Loader + Fabric API + the Fabric jar
function Test-QuiltBoot($Branch) {
    $props = Read-GradleProperties $Branch.Worktree
    $mcVersion = $props["minecraft_version"]
    $fabricApi = $props["fabric_api_version"]
    $loader = $QuiltTestLoader
    $java = Get-Java
    if (-not $java) { Write-Host "java not found (set JAVA_HOME)." -ForegroundColor Red; return $false }

    New-Item -ItemType Directory -Force $QuiltRoot | Out-Null
    $installer = Join-Path $QuiltRoot "quilt-installer-$QuiltInstallerVersion.jar"
    $fabricApiJar = Join-Path $QuiltRoot "fabric-api-$fabricApi.jar"
    $serverDir = Join-Path $QuiltRoot "server-$mcVersion-$loader"
    $encoded = [Uri]::EscapeDataString($fabricApi)
    if (-not (Save-Download "https://maven.quiltmc.org/repository/release/org/quiltmc/quilt-installer/$QuiltInstallerVersion/quilt-installer-$QuiltInstallerVersion.jar" $installer)) { return $false }
    if (-not (Save-Download "https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/$encoded/fabric-api-$encoded.jar" $fabricApiJar)) { return $false }

    if (-not (Test-Path (Join-Path $serverDir "quilt-server-launch.jar"))) {
        Write-Host "Installing Quilt Loader $loader server for Minecraft $mcVersion..." -ForegroundColor Cyan
        & $java -jar $installer install server $mcVersion $loader --download-server "--install-dir=$serverDir" | Out-Host
        if ($LASTEXITCODE -ne 0) { return $false }
    }
    $modsDir = Join-Path $serverDir "mods"
    Remove-Item -Recurse -Force $modsDir -ErrorAction SilentlyContinue
    New-Item -ItemType Directory -Force $modsDir | Out-Null
    Copy-Item $fabricApiJar $modsDir
    Copy-Item (Get-Artifact $Branch "fabric").JarPath $modsDir
    return Invoke-ServerBoot "Quilt $loader (MC $mcVersion, Fabric API $fabricApi)" $serverDir "-jar quilt-server-launch.jar"
}

# NeoForge: official NeoForge server (version from the branch's gradle.properties) + the NeoForge jar
function Test-NeoForgeBoot($Branch) {
    $props = Read-GradleProperties $Branch.Worktree
    $neoforge = $props["neoforge_version"]
    if (-not $neoforge) { Write-Host "neoforge_version missing from gradle.properties." -ForegroundColor Red; return $false }
    $java = Get-Java
    if (-not $java) { Write-Host "java not found (set JAVA_HOME)." -ForegroundColor Red; return $false }

    New-Item -ItemType Directory -Force $NeoForgeRoot | Out-Null
    $installer = Join-Path $NeoForgeRoot "neoforge-$neoforge-installer.jar"
    $serverDir = Join-Path $NeoForgeRoot "server-$neoforge"
    $argsFile = "libraries/net/neoforged/neoforge/$neoforge/win_args.txt"
    if (-not (Save-Download "https://maven.neoforged.net/releases/net/neoforged/neoforge/$neoforge/neoforge-$neoforge-installer.jar" $installer)) { return $false }

    if (-not (Test-Path (Join-Path $serverDir $argsFile))) {
        Write-Host "Installing NeoForge $neoforge server..." -ForegroundColor Cyan
        New-Item -ItemType Directory -Force $serverDir | Out-Null
        Push-Location $serverDir
        try { & $java -jar $installer --install-server $serverDir | Out-Host } finally { Pop-Location }
        if ($LASTEXITCODE -ne 0 -or -not (Test-Path (Join-Path $serverDir $argsFile))) { return $false }
    }
    $modsDir = Join-Path $serverDir "mods"
    Remove-Item -Recurse -Force $modsDir -ErrorAction SilentlyContinue
    New-Item -ItemType Directory -Force $modsDir | Out-Null
    Copy-Item (Get-Artifact $Branch "neoforge").JarPath $modsDir
    return Invoke-ServerBoot "NeoForge $neoforge" $serverDir "@$argsFile"
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

# ------------------------------------------------------------------------------
# CurseForge: upload API (X-Api-Token, CURSEFORGE_TOKEN) + Core API to detect duplicates (x-api-key, CURSEFORGE_API_KEY)
# ------------------------------------------------------------------------------

# Maps "26.3" / "fabric" / "neoforge" / "quilt" to CurseForge game version ids (cached for the run).
function Get-CurseForgeVersionIds([string]$MinecraftVersion, [string[]]$Loaders) {
    if (-not $script:CurseForgeVersions) {
        $headers = @{ "X-Api-Token" = $CurseForgeToken }
        $types = Invoke-RestMethod -UserAgent $UserAgent -TimeoutSec 30 -Headers $headers -Uri "https://minecraft.curseforge.com/api/game/version-types"
        $script:CurseForgeTypeSlugs = @{}
        foreach ($t in $types) { $script:CurseForgeTypeSlugs[[int]$t.id] = "$($t.slug)" }
        $script:CurseForgeVersions = @(Invoke-RestMethod -UserAgent $UserAgent -TimeoutSec 30 -Headers $headers -Uri "https://minecraft.curseforge.com/api/game/versions")
    }
    $ids = @()
    $mc = @($script:CurseForgeVersions | Where-Object { $_.name -eq $MinecraftVersion -and $script:CurseForgeTypeSlugs[[int]$_.gameVersionTypeID] -like "minecraft*" })
    if (-not $mc) { throw "CurseForge has no game version '$MinecraftVersion'." }
    $ids += [int]$mc[0].id
    $names = @{ "fabric" = "Fabric"; "quilt" = "Quilt"; "neoforge" = "NeoForge" }
    foreach ($loader in $Loaders) {
        $match = @($script:CurseForgeVersions | Where-Object { $_.name -eq $names[$loader] -and $script:CurseForgeTypeSlugs[[int]$_.gameVersionTypeID] -eq "modloader" })
        if (-not $match) { throw "CurseForge has no mod loader '$($names[$loader])'." }
        $ids += [int]$match[0].id
    }
    return $ids
}

# $true = a file with this display name exists, $false = not found, $null = could not tell
function Test-CurseForgeFile([string]$DisplayName, [string]$MinecraftVersion) {
    if (-not $CurseForgeApiKey) { return $null }
    try {
        $uri = "https://api.curseforge.com/v1/mods/$CurseForgeProject/files?gameVersion=$([Uri]::EscapeDataString($MinecraftVersion))&pageSize=50"
        $files = Invoke-RestMethod -UserAgent $UserAgent -TimeoutSec 15 -Headers @{ "x-api-key" = $CurseForgeApiKey } -Uri $uri
        return [bool](@($files.data) | Where-Object { $_.displayName -eq $DisplayName })
    } catch {
        Write-Host "Could not query CurseForge ($($_.Exception.Message))." -ForegroundColor Yellow
        return $null
    }
}

function Publish-CurseForgeFile($Branch, $Artifact) {
    Add-Type -AssemblyName System.Net.Http
    $metadata = [ordered]@{
        changelog     = $Branch.Info.changelog
        changelogType = "markdown"
        displayName   = $Artifact.VersionName
        gameVersions  = @(Get-CurseForgeVersionIds $Branch.Info.minecraftVersion $Artifact.ModrinthLoaders)
        releaseType   = $Artifact.VersionType
    }
    if ($Artifact.Loader -eq "fabric") {
        $metadata["relations"] = @{ projects = @(@{ slug = "fabric-api"; type = "requiredDependency" }) }
    }
    $json = $metadata | ConvertTo-Json -Depth 6

    $client = New-Object System.Net.Http.HttpClient
    $stream = [IO.File]::OpenRead($Artifact.JarPath)
    try {
        $client.DefaultRequestHeaders.Add("X-Api-Token", $CurseForgeToken)
        $client.DefaultRequestHeaders.Add("User-Agent", $UserAgent)
        $form = New-Object System.Net.Http.MultipartFormDataContent
        $form.Add((New-Object System.Net.Http.StringContent($json, [Text.Encoding]::UTF8, "application/json")), "metadata")
        $file = New-Object System.Net.Http.StreamContent($stream)
        $file.Headers.ContentType = [System.Net.Http.Headers.MediaTypeHeaderValue]::Parse("application/java-archive")
        $form.Add($file, "file", [IO.Path]::GetFileName($Artifact.JarPath))
        $response = $client.PostAsync("https://minecraft.curseforge.com/api/projects/$CurseForgeProject/upload-file", $form).Result
        if (-not $response.IsSuccessStatusCode) {
            throw "HTTP $([int]$response.StatusCode): $($response.Content.ReadAsStringAsync().Result)"
        }
    } finally {
        $stream.Dispose()
        $client.Dispose()
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

# Creates the release as a draft, attaches every loader jar, then publishes it.
# On failure the draft is deleted so a rerun starts clean (drafts are not found by tag lookups).
function Publish-GitHubRelease($Branch, [string]$Tag) {
    $api = "https://api.github.com/repos/$GitHubRepo"
    $json = @{
        tag_name         = $Tag
        target_commitish = $Branch.Sha
        name             = $Branch.Info.versionName
        body             = $Branch.Info.changelog
        draft            = $true
        prerelease       = (@($Branch.Artifacts | Where-Object { $_.VersionType -ne "release" }).Count -eq $Branch.Artifacts.Count)
    } | ConvertTo-Json
    $release = Invoke-RestMethod -Method Post -Uri "$api/releases" -UserAgent $UserAgent -Headers (Get-GitHubHeaders) `
        -ContentType "application/json; charset=utf-8" -Body ([Text.Encoding]::UTF8.GetBytes($json))
    try {
        foreach ($artifact in $Branch.Artifacts) {
            $uploadUrl = ($release.upload_url -replace '\{.*\}$', '') + "?name=" + [Uri]::EscapeDataString((Split-Path -Leaf $artifact.JarPath))
            Invoke-RestMethod -Method Post -Uri $uploadUrl -UserAgent $UserAgent -Headers (Get-GitHubHeaders) `
                -ContentType "application/java-archive" -InFile $artifact.JarPath | Out-Null
        }
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
    Write-Host "`n======================================================================================================================" -ForegroundColor Green
    Write-Host " VERIFICATION (all branches)" -ForegroundColor Green
    Write-Host "======================================================================================================================" -ForegroundColor Green
    Write-Host (" {0,-8} | {1,-8} | {2,-26} | {3,-9} | {4,-9} | {5,-9} | {6,-9} | {7,-9} | {8,-9} | {9,-9} |" -f "Branch", "Commit", "Version [type]", "Changelog", "Compile", "JUnit", "GameTest", "Package", "Quilt", "NeoForge")
    foreach ($b in $Branches) {
        $version = if ($b.Info) { "$($b.Info.modVersion) [$($b.Info.versionType)]" } elseif ($b.ModVersion) { $b.ModVersion } else { "N/A" }
        $commit = if ($b.Sha) { $b.Sha.Substring(0, 7) } else { "N/A" }
        Write-Host (" {0,-8} | {1,-8} | {2,-26} |" -f $b.Name, $commit, $version) -NoNewline
        foreach ($stage in $VerifyStages) { Write-Cell $b.Status[$stage] 9 }
        Write-Host ""
    }

    Write-Host "`n RELEASE ($(if ($DryRun) { 'dry run: planned actions, nothing was sent' } else { 'deploy: true branches' }))" -ForegroundColor Green
    Write-Host (" {0,-8} | {1,-30} | {2,-15} | {3,-16} | {4,-16} | {5,-16} | {6,-16} |" -f "Branch", "Version [type]", "Loader tags", "Git push + tag", "Modrinth", "CurseForge", "GitHub")
    foreach ($b in $Branches) {
        if (-not $b.Deploy) { continue }
        if (-not $b.Artifacts) {
            Write-Host (" {0,-8} | {1,-30} | {2,-15} |" -f $b.Name, "N/A", ($b.Loaders -join ",")) -NoNewline
            foreach ($stage in @("Push") + $ArtifactStages + @("GitHub")) { Write-Cell "Skipped" 16 }
            Write-Host ""
            continue
        }
        foreach ($a in $b.Artifacts) {
            Write-Host (" {0,-8} | {1,-30} | {2,-15} |" -f $b.Name, "$($a.VersionNumber) [$($a.VersionType)]", ($a.ModrinthLoaders -join ",")) -NoNewline
            Write-Cell $b.Status["Push"] 16
            foreach ($stage in $ArtifactStages) { Write-Cell $a.Status[$stage] 16 }
            Write-Cell $b.Status["GitHub"] 16
            Write-Host ""
        }
    }
    if (-not @($Branches | Where-Object { $_.Deploy })) { Write-Host " (no branch has `"deploy`": true)" -ForegroundColor DarkGray }

    foreach ($b in $Branches) {
        if (-not ($b.Deploy -and $b.Info)) { continue }
        $lines = @($b.Info.changelog -split "`n")
        Write-Host "`n [$($b.Name)] v$($b.Info.modVersion) changelog ($($lines.Count) lines): $($lines[0])" -ForegroundColor Cyan
    }
    Write-Host "======================================================================================================================" -ForegroundColor Green
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
$CurseForgeEnabled = [bool]$Config.platforms.curseforge.enabled
$GitHubEnabled = [bool]$Config.platforms.github.enabled
$GitPushEnabled = [bool]$Config.platforms.git_push.enabled
$ModrinthProject = if ($Config.platforms.modrinth.project) { "$($Config.platforms.modrinth.project)" } else { "small-logic-tweak" }
$CurseForgeProject = "$($Config.platforms.curseforge.project_id)"
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
    if (($loaders -contains "quilt") -and ($loaders -notcontains "fabric")) { Stop-Pipeline "Branch '$name': 'quilt' is a tag on the Fabric jar and needs 'fabric'." }
    if (-not (($loaders -contains "fabric") -or ($loaders -contains "neoforge"))) { Stop-Pipeline "Branch '$name': loaders must include 'fabric' or 'neoforge'." }

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
        Artifacts  = @()
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
if ($CurseForgeEnabled -and $CurseForgeProject -notmatch '^[1-9][0-9]*$') {
    Stop-Pipeline "platforms.curseforge.project_id must be the numeric CurseForge project id."
}
if (-not $DryRun) {
    if ($DeployBranches.Count -eq 0) { Stop-Pipeline "No branch has `"deploy`": true. Nothing to release (use -DryRun to only verify)." }
    if (-not ($ModrinthEnabled -or $CurseForgeEnabled -or $GitHubEnabled -or $GitPushEnabled)) { Stop-Pipeline "Every platform is disabled in $ConfigPath." }
}

$ModrinthToken = Get-Secret "MODRINTH_TOKEN"
$CurseForgeToken = Get-Secret "CURSEFORGE_TOKEN"
$CurseForgeApiKey = Get-Secret "CURSEFORGE_API_KEY"
$GitHubToken = Get-Secret "GITHUB_TOKEN"
if (-not $DryRun) {
    if ($ModrinthEnabled -and -not $ModrinthToken) { Stop-Pipeline "platforms.modrinth is enabled but MODRINTH_TOKEN is not set." }
    if ($CurseForgeEnabled -and -not $CurseForgeToken) { Stop-Pipeline "platforms.curseforge is enabled but CURSEFORGE_TOKEN (upload token) is not set." }
    if ($CurseForgeEnabled -and -not $CurseForgeApiKey) { Stop-Pipeline "platforms.curseforge is enabled but CURSEFORGE_API_KEY (Core API key, used to detect duplicate uploads) is not set." }
    if ($GitHubEnabled -and -not $GitHubToken) { Stop-Pipeline "platforms.github is enabled but GITHUB_TOKEN is not set." }
}

foreach ($b in $Branches) {
    $sha = git -C $RepoRoot rev-parse --verify --quiet "refs/heads/$($b.Name)"
    if (-not $sha) { Stop-Pipeline "Local branch '$($b.Name)' does not exist." }
    $b.Sha = "$sha".Trim()
}

# CurseForge game version ids must resolve for every released branch before anything is built
if ($CurseForgeEnabled -and $DeployBranches.Count -gt 0) {
    if (-not $CurseForgeToken) {
        Write-Host "WARNING: CURSEFORGE_TOKEN not set; CurseForge game versions not checked in this dry run." -ForegroundColor Yellow
    } else {
        foreach ($b in $DeployBranches) {
            try { Get-CurseForgeVersionIds $b.Name @($b.Loaders) | Out-Null }
            catch { Stop-Pipeline "Branch '$($b.Name)': $($_.Exception.Message)" }
        }
    }
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
Write-Host "Verify : $(($Branches | ForEach-Object { "$($_.Name) [$($_.Loaders -join ',')]" }) -join ', ')" -ForegroundColor Cyan
Write-Host "Deploy : $(if ($DeployBranches.Count) { ($DeployBranches | ForEach-Object { $_.Name }) -join ', ' } else { '(none)' })" -ForegroundColor Cyan
Write-Host "Targets: Modrinth=$ModrinthEnabled ($ModrinthProject), CurseForge=$CurseForgeEnabled ($CurseForgeProject), GitHub=$GitHubEnabled ($GitHubRepo), GitPush=$GitPushEnabled ($GitRemote)" -ForegroundColor Cyan
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
        if ($passed -and ($b.Loaders -contains "neoforge")) {
            $passed = Invoke-Stage $b "NeoForge" { Test-NeoForgeBoot $b }
        }

        if ($passed) {
            Write-Host "Branch $($b.Name) VERIFICATION PASSED." -ForegroundColor Green
        } else {
            $verificationFailed = $true
            $applicable = @($VerifyStages | Where-Object { ($_ -ne "Quilt" -or ($b.Loaders -contains "quilt")) -and ($_ -ne "NeoForge" -or ($b.Loaders -contains "neoforge")) })
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
            foreach ($a in $b.Artifacts) {
                if ($ModrinthEnabled) {
                    $exists = Test-ModrinthVersion $a.VersionNumber
                    $a.Status["Modrinth"] = if ($exists -eq $true) { "Exists" } elseif ($exists -eq $false) { "Planned" } else { "Planned (?)" }
                } else { $a.Status["Modrinth"] = "Off" }
                if ($CurseForgeEnabled) {
                    $exists = Test-CurseForgeFile $a.VersionName $b.Info.minecraftVersion
                    $a.Status["CurseForge"] = if ($exists -eq $true) { "Exists" } elseif ($exists -eq $false) { "Planned" } else { "Planned (?)" }
                } else { $a.Status["CurseForge"] = "Off" }
            }
            if ($GitHubEnabled) {
                $exists = Test-GitHubRelease $tag
                $b.Status["GitHub"] = if ($exists -eq $true) { "Exists" } elseif ($exists -eq $false) { "Planned" } else { "Planned (?)" }
            } else { $b.Status["GitHub"] = "Off" }
        }
        return
    }

    # ==============================================================================
    # PHASE 2: RELEASE ("deploy": true branches only, exact verified commit)
    # Order per branch: git push + tag -> Modrinth -> CurseForge -> GitHub Release
    # ==============================================================================
    $env:MODRINTH_TOKEN = $ModrinthToken
    foreach ($b in $DeployBranches) {
        Write-Host "`n--------------------------------------------------" -ForegroundColor Yellow
        Write-Host " Releasing branch: $($b.Name) -> $($b.Info.modVersion) ($(($b.Artifacts | ForEach-Object { $_.Loader }) -join ', '))" -ForegroundColor Yellow
        Write-Host "--------------------------------------------------" -ForegroundColor Yellow

        $tag = "v$($b.Info.modVersion)"
        $currentSha = "$(git -C $RepoRoot rev-parse --verify --quiet "refs/heads/$($b.Name)")".Trim()
        if ($currentSha -ne $b.Sha) {
            Write-Host "Branch $($b.Name) moved during verification ($($b.Sha.Substring(0, 7)) -> $currentSha). Not releasing an unverified commit." -ForegroundColor Red
            foreach ($stage in $ReleaseStages) { $b.Status[$stage] = "Failed (moved)" }
            foreach ($a in $b.Artifacts) { foreach ($stage in $ArtifactStages) { $a.Status[$stage] = "Failed (moved)" } }
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
                foreach ($a in $b.Artifacts) { foreach ($stage in $ArtifactStages) { $a.Status[$stage] = "Skipped" } }
                $script:ExitCode = 1
                continue
            }
        } else { $b.Status["Push"] = "Off" }

        foreach ($a in $b.Artifacts) {
            # 2. Modrinth (Minotaur task of the loader module, fed by ORG_GRADLE_PROJECT_* release properties)
            if ($ModrinthEnabled) {
                $task = if ($a.Project) { "$($a.Project):modrinth" } else { "modrinth" }
                if ((Test-ModrinthVersion $a.VersionNumber) -eq $true) {
                    Write-Host "Version $($a.VersionNumber) already exists on Modrinth. Skipping upload." -ForegroundColor Yellow
                    $a.Status["Modrinth"] = "Skipped (Exists)"
                } elseif (Invoke-Gradle $b @($task)) {
                    $a.Status["Modrinth"] = "Success"
                } else {
                    $a.Status["Modrinth"] = "Failed"
                    $script:ExitCode = 1
                }
            } else { $a.Status["Modrinth"] = "Off" }

            # 3. CurseForge
            if ($CurseForgeEnabled) {
                $exists = Test-CurseForgeFile $a.VersionName $b.Info.minecraftVersion
                if ($exists -eq $true) {
                    Write-Host "'$($a.VersionName)' already exists on CurseForge. Skipping upload." -ForegroundColor Yellow
                    $a.Status["CurseForge"] = "Skipped (Exists)"
                } elseif ($null -eq $exists) {
                    Write-Host "Cannot tell whether '$($a.VersionName)' is already on CurseForge; not uploading blind." -ForegroundColor Red
                    $a.Status["CurseForge"] = "Failed (check)"
                    $script:ExitCode = 1
                } else {
                    try {
                        Write-Host "Uploading $($a.VersionName) to CurseForge..." -ForegroundColor Cyan
                        Publish-CurseForgeFile $b $a
                        $a.Status["CurseForge"] = "Success"
                    } catch {
                        Write-Host "CurseForge upload failed: $($_.Exception.Message)" -ForegroundColor Red
                        $a.Status["CurseForge"] = "Failed"
                        $script:ExitCode = 1
                    }
                }
            } else { $a.Status["CurseForge"] = "Off" }
        }

        # 4. GitHub Release with every loader jar attached
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
