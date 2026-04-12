param(
    [ValidateSet("debug", "release")]
    [string]$BuildType = "debug",

    [string]$Label = "",

    [string]$ProjectRoot = $PSScriptRoot,

    [string]$ArtifactsRoot = "",

    [switch]$GitAdd
)

$ErrorActionPreference = "Stop"

function Write-Info($msg) {
    Write-Host "[INFO] $msg" -ForegroundColor Cyan
}

function Write-Ok($msg) {
    Write-Host "[ OK ] $msg" -ForegroundColor Green
}

function Write-WarnMsg($msg) {
    Write-Host "[WARN] $msg" -ForegroundColor Yellow
}

function Fail($msg) {
    Write-Host "[FAIL] $msg" -ForegroundColor Red
    exit 1
}

$ProjectRoot = (Resolve-Path $ProjectRoot).Path

if ([string]::IsNullOrWhiteSpace($ArtifactsRoot)) {
    $ArtifactsRoot = Join-Path $ProjectRoot "artifacts\apk"
}

$GradleWrapper = Join-Path $ProjectRoot "gradlew.bat"
if (-not (Test-Path $GradleWrapper)) {
    Fail "gradlew.bat not found. Check ProjectRoot: $ProjectRoot"
}

$AppDir = Join-Path $ProjectRoot "app"
if (-not (Test-Path $AppDir)) {
    Fail "app directory not found: $AppDir"
}

$Now = Get-Date
$DateFolder = $Now.ToString("yyyy-MM-dd")
$Stamp = $Now.ToString("yyyy-MM-dd-HHmmss")

$ArchiveDir = Join-Path $ArtifactsRoot $DateFolder
New-Item -ItemType Directory -Force -Path $ArchiveDir | Out-Null

$TaskName = if ($BuildType -eq "debug") { ":app:assembleDebug" } else { ":app:assembleRelease" }

Write-Info "ProjectRoot: $ProjectRoot"
Write-Info "BuildType  : $BuildType"
Write-Info "Task       : $TaskName"
Write-Info "ArchiveDir : $ArchiveDir"

Push-Location $ProjectRoot
try {
    Write-Info "Starting Gradle build..."
    & $GradleWrapper $TaskName
    if ($LASTEXITCODE -ne 0) {
        Fail "Gradle build failed."
    }
    Write-Ok "Gradle build succeeded"
}
finally {
    Pop-Location
}

$ApkOutputDir = Join-Path $ProjectRoot "app\build\outputs\apk\$BuildType"
if (-not (Test-Path $ApkOutputDir)) {
    Fail "APK output directory not found: $ApkOutputDir"
}

$ApkFiles = Get-ChildItem -Path $ApkOutputDir -Recurse -File -Filter "*.apk" |
    Sort-Object LastWriteTime -Descending

if (-not $ApkFiles -or $ApkFiles.Count -eq 0) {
    Fail "No APK file found."
}

$SourceApk = $ApkFiles[0].FullName

$SafeLabel = $Label.Trim()
$BaseName = if ([string]::IsNullOrWhiteSpace($SafeLabel)) {
    "RP902App-$BuildType-$Stamp"
} else {
    "RP902App-$BuildType-$Stamp-$SafeLabel"
}

$DestApk = Join-Path $ArchiveDir ($BaseName + ".apk")
$MetaFile = Join-Path $ArchiveDir ($BaseName + ".txt")
$HashFile = Join-Path $ArchiveDir ($BaseName + ".sha256.txt")

Copy-Item $SourceApk $DestApk -Force
Write-Ok "APK archived: $DestApk"

$Hash = Get-FileHash -Path $DestApk -Algorithm SHA256
@"
SHA256: $($Hash.Hash)
File  : $(Split-Path $DestApk -Leaf)
Built : $Stamp
Type  : $BuildType
"@ | Set-Content -Path $HashFile -Encoding UTF8

Write-Ok "SHA256 file written: $HashFile"

$GitCommit = ""
try {
    Push-Location $ProjectRoot
    $GitCommit = (git rev-parse --short HEAD 2>$null)
    Pop-Location
}
catch {
    $GitCommit = ""
}

@"
Build date       : $Stamp
Build type       : $BuildType
Label            : $SafeLabel
Project root     : $ProjectRoot
Source APK       : $SourceApk
Archived APK     : $DestApk
Git commit       : $GitCommit
SHA256           : $($Hash.Hash)
"@ | Set-Content -Path $MetaFile -Encoding UTF8

Write-Ok "Metadata file written: $MetaFile"

if ($GitAdd) {
    try {
        Push-Location $ProjectRoot
        git add -- $DestApk $MetaFile $HashFile
        Pop-Location
        Write-Ok "Files added to git"
    }
    catch {
        Write-WarnMsg "git add failed. Add files manually if needed."
    }
}

Write-Host ""
Write-Host "=== DONE ===" -ForegroundColor Magenta
Write-Host "APK : $DestApk"
Write-Host "TXT : $MetaFile"
Write-Host "SHA : $HashFile"