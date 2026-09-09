param(
    [string]$PresetPack,
    [switch]$PrepareOnly,
    [switch]$Offline,
    [switch]$AcceptEula
)

$ErrorActionPreference = 'Stop'
if (-not $PrepareOnly -and -not $AcceptEula) {
    throw 'Launching the isolated Minecraft server requires explicit EULA acceptance. Review https://aka.ms/MinecraftEULA and pass -AcceptEula only if you accept it. Use -PrepareOnly to inspect the test configuration without accepting or launching.'
}
$repositoryPath = [IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot))
if (-not $PresetPack) {
    $PresetPack = Join-Path (Split-Path -Parent $repositoryPath) 'reterraforged\forge\run\saves\Test\datapacks\reterraforged-preset.zip'
}
$sourcePack = (Resolve-Path -LiteralPath $PresetPack).Path
$runId = (Get-Date -Format 'yyyyMMdd-HHmmss') + '-' + [Guid]::NewGuid().ToString('N').Substring(0, 6)
$runDirectory = Join-Path $repositoryPath ('run\rtfSmoke\' + $runId)
$packDirectory = Join-Path $runDirectory 'world\datapacks'
New-Item -ItemType Directory -Path $packDirectory -Force | Out-Null
$destinationPack = Join-Path $packDirectory 'rtf-rivers-zero.zip'
Copy-Item -LiteralPath $sourcePack -Destination $destinationPack

Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [IO.Compression.ZipFile]::Open($destinationPack, [IO.Compression.ZipArchiveMode]::Update)
try {
    $entryName = 'data/reterraforged/reterraforged/worldgen/preset/preset.json'
    $entry = $archive.GetEntry($entryName)
    if ($null -eq $entry) {
        throw "The supplied pack is not an exported RTF preset: $sourcePack"
    }
    $reader = [IO.StreamReader]::new($entry.Open())
    try {
        $preset = $reader.ReadToEnd() | ConvertFrom-Json
    } finally {
        $reader.Dispose()
    }
    $preset.rivers.riverCount = 0
    $preset.world.continent.continentType = 'MULTI_IMPROVED'
    $entry.Delete()
    $replacement = $archive.CreateEntry($entryName)
    $writer = [IO.StreamWriter]::new($replacement.Open(), [Text.UTF8Encoding]::new($false))
    try {
        $writer.Write(($preset | ConvertTo-Json -Depth 50))
    } finally {
        $writer.Dispose()
    }
} finally {
    $archive.Dispose()
}

$serverProperties = @"
level-name=world
level-type=minecraft:normal
level-seed=617432698
initial-enabled-packs=vanilla,mod:forge,mod:reterraforged,mod:rivertale,file/rtf-rivers-zero.zip
server-ip=127.0.0.1
server-port=0
online-mode=false
view-distance=2
simulation-distance=2
spawn-monsters=false
spawn-animals=false
spawn-npcs=false
max-players=1
"@
Set-Content -LiteralPath (Join-Path $runDirectory 'server.properties') -Value $serverProperties -Encoding ascii
$configDirectory = Join-Path $runDirectory 'config'
New-Item -ItemType Directory -Path $configDirectory -Force | Out-Null
$rivertaleConfig = @"
regionSize=8
cellSize=2
samplesPerCell=1
mergeThreshold=10
minSlope=1
minPathLength=3
embankmentRadius=288
enableMetrics=true
"@
Set-Content -LiteralPath (Join-Path $configDirectory 'rivertale-common.toml') -Value $rivertaleConfig -Encoding ascii
if ($AcceptEula) {
    Set-Content -LiteralPath (Join-Path $runDirectory 'eula.txt') -Value 'eula=true' -Encoding ascii
}
Write-Output "Prepared isolated RTF smoke run: $runDirectory"
Write-Output "Server log: $(Join-Path $runDirectory 'logs\latest.log')"
Write-Output "Smoke result: $(Join-Path $runDirectory 'rtf-smoke-result.txt')"
Write-Output 'The copied preset uses MULTI_IMPROVED continents and native river count zero.'
if ($PrepareOnly) {
    return
}

$gradleArguments = @(
    '--no-daemon',
    'runServer',
    '-PrtfSmoke',
    "-PrunDirectory=$runDirectory",
    '-Prun_xmx=4G',
    '-Prun_xms=1G',
    '-Pjourneymap_enabled=false',
    '-Pjei_enabled=false',
    '-Pspark_enabled=false'
)
if ($Offline) {
    $gradleArguments = @('--offline') + $gradleArguments
}
$gradleExitCode = -1
$gradleFailure = $null
Push-Location -LiteralPath $repositoryPath
try {
    & (Join-Path $repositoryPath 'gradlew.bat') @gradleArguments
    $gradleExitCode = $LASTEXITCODE
} catch {
    $gradleFailure = $_.Exception.Message
    if ($null -ne $LASTEXITCODE) {
        $gradleExitCode = $LASTEXITCODE
    }
} finally {
    Pop-Location
}
if ($gradleFailure) {
    Write-Warning "Gradle reported: $gradleFailure"
}
$resultPath = Join-Path $runDirectory 'rtf-smoke-result.txt'
if (-not (Test-Path -LiteralPath $resultPath)) {
    throw "The server did not produce a smoke result. Inspect $(Join-Path $runDirectory 'logs\latest.log'). Gradle exit code: $gradleExitCode"
}
$result = Get-Content -LiteralPath $resultPath -Raw
Write-Output $result
if ($gradleFailure -or $gradleExitCode -ne 0 -or -not $result.StartsWith('PASS:')) {
    throw "RTF smoke verification failed. Result: $resultPath"
}
