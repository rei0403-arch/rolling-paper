$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$sdkRoot = Join-Path $env:LOCALAPPDATA "Android\Sdk"
$studioJbr = "C:\Program Files\Android\Android Studio\jbr"

function Get-LatestDirectory {
    param([Parameter(Mandatory=$true)][string]$Path)
    if (-not (Test-Path $Path)) {
        throw "Missing directory: $Path"
    }
    Get-ChildItem -Path $Path -Directory |
        Sort-Object Name -Descending |
        Select-Object -First 1
}

$buildToolsDir = Get-LatestDirectory (Join-Path $sdkRoot "build-tools")
$platformDir = Get-LatestDirectory (Join-Path $sdkRoot "platforms")
$buildTools = $buildToolsDir.FullName
$platform = Join-Path $platformDir.FullName "android.jar"

$aapt2 = Join-Path $buildTools "aapt2.exe"
$d8 = Join-Path $buildTools "d8.bat"
$zipalign = Join-Path $buildTools "zipalign.exe"
$apksigner = Join-Path $buildTools "apksigner.bat"
$javac = Join-Path $studioJbr "bin\javac.exe"
$jar = Join-Path $studioJbr "bin\jar.exe"
$keytool = Join-Path $studioJbr "bin\keytool.exe"

function Invoke-Checked {
    param(
        [Parameter(Mandatory=$true)][string]$FilePath,
        [Parameter(ValueFromRemainingArguments=$true)][string[]]$Arguments
    )
    & $FilePath @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Command failed with exit code ${LASTEXITCODE}: $FilePath $($Arguments -join ' ')"
    }
}

foreach ($tool in @($aapt2, $d8, $zipalign, $apksigner, $javac, $jar, $keytool, $platform)) {
    if (-not (Test-Path $tool)) {
        throw "Missing required Android build file: $tool"
    }
}

$env:JAVA_HOME = $studioJbr
$env:PATH = "$studioJbr\bin;$env:PATH"

$buildDir = Join-Path $projectRoot "build"
$genDir = Join-Path $buildDir "generated"
$classesDir = Join-Path $buildDir "classes"
$dexDir = Join-Path $buildDir "dex"
$outDir = Join-Path $projectRoot "out"
$resZip = Join-Path $buildDir "compiled-res.zip"
$classesJar = Join-Path $buildDir "classes.jar"
$unsignedApk = Join-Path $buildDir "app-unsigned.apk"
$dexedApk = Join-Path $buildDir "app-dexed.apk"
$alignedApk = Join-Path $buildDir "app-aligned.apk"
$signedApk = Join-Path $outDir "rolling-paper-debug.apk"
$keystore = Join-Path $projectRoot "debug.keystore"

Remove-Item -Recurse -Force $buildDir, $outDir -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $genDir, $classesDir, $dexDir, $outDir | Out-Null

Invoke-Checked -FilePath $aapt2 -Arguments @(
    "compile",
    "--dir", (Join-Path $projectRoot "app\src\main\res"),
    "-o", $resZip
)

Invoke-Checked -FilePath $aapt2 -Arguments @(
    "link",
    "-I", $platform,
    "--manifest", (Join-Path $projectRoot "app\src\main\AndroidManifest.xml"),
    "--java", $genDir,
    "--min-sdk-version", "26",
    "--target-sdk-version", "36",
    "--version-code", "1",
    "--version-name", "1.0",
    "--auto-add-overlay",
    "-o", $unsignedApk,
    $resZip
)

$sources = @()
$sources += Get-ChildItem -Path (Join-Path $projectRoot "app\src\main\java") -Recurse -Filter *.java | ForEach-Object { $_.FullName }
$sources += Get-ChildItem -Path $genDir -Recurse -Filter *.java | ForEach-Object { $_.FullName }

Invoke-Checked -FilePath $javac -Arguments (@(
    "--release", "17",
    "-encoding", "UTF-8",
    "-classpath", $platform,
    "-d", $classesDir
) + $sources)

Invoke-Checked -FilePath $jar -Arguments @("cf", $classesJar, "-C", $classesDir, ".")

Invoke-Checked -FilePath $d8 -Arguments @(
    "--min-api", "26",
    "--lib", $platform,
    "--output", $dexDir,
    $classesJar
)

Copy-Item $unsignedApk $dexedApk
Invoke-Checked -FilePath $jar -Arguments @("uf", $dexedApk, "-C", $dexDir, "classes.dex")
Invoke-Checked -FilePath $zipalign -Arguments @("-f", "-p", "4", $dexedApk, $alignedApk)

if (-not (Test-Path $keystore)) {
    Invoke-Checked -FilePath $keytool -Arguments @(
        "-genkeypair",
        "-keystore", $keystore,
        "-storepass", "android",
        "-keypass", "android",
        "-alias", "androiddebugkey",
        "-keyalg", "RSA",
        "-keysize", "2048",
        "-validity", "10000",
        "-dname", "CN=Android Debug,O=Codex,C=KR"
    )
}

Invoke-Checked -FilePath $apksigner -Arguments @(
    "sign",
    "--ks", $keystore,
    "--ks-pass", "pass:android",
    "--key-pass", "pass:android",
    "--out", $signedApk,
    $alignedApk
)

Invoke-Checked -FilePath $apksigner -Arguments @("verify", "--verbose", $signedApk)

Write-Output "Built APK: $signedApk"
