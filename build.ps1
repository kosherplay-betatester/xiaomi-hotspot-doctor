# Builds HotspotBand.apk with the plain Android SDK tools (no Gradle).
param(
    [string]$Sdk = $(if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { "$env:LOCALAPPDATA\Android\Sdk" }),
    [string]$Platform = 'android-37.2',
    [string]$BuildTools = '37.0.0',
    [switch]$Install
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$out = Join-Path $root 'build'
$androidJar = Join-Path $Sdk "platforms\$Platform\android.jar"
$tools = Join-Path $Sdk "build-tools\$BuildTools"
$libs = @(Get-ChildItem (Join-Path $root 'libs') -Filter 'shizuku-*.jar' | ForEach-Object FullName)
$keystore = Join-Path $root 'hotspotband.keystore'

function Step([string]$Name, [scriptblock]$Action) {
    Write-Host "== $Name" -ForegroundColor Cyan
    & $Action
    if ($LASTEXITCODE -ne 0) { throw "$Name failed (exit $LASTEXITCODE)" }
}

if (Test-Path $out) { Remove-Item -Recurse -Force $out }
New-Item -ItemType Directory -Path "$out\classes", "$out\gen", "$out\dex" | Out-Null

Step 'aapt2 compile' { & "$tools\aapt2.exe" compile --dir (Join-Path $root 'res') -o "$out\res.zip" }
Step 'aapt2 link' {
    & "$tools\aapt2.exe" link -o "$out\unsigned.apk" -I $androidJar `
        --manifest (Join-Path $root 'AndroidManifest.xml') --java "$out\gen" `
        --min-sdk-version 30 --target-sdk-version 35 --version-code 2 --version-name 1.1 "$out\res.zip"
}

$sources = @(Get-ChildItem -Recurse (Join-Path $root 'src'), "$out\gen" -Filter *.java | ForEach-Object FullName)
$classpath = (@($androidJar) + $libs) -join ';'
Step 'javac' { javac -source 17 -target 17 -Xlint:-options -nowarn -encoding UTF-8 -classpath $classpath -d "$out\classes" @sources }

$classFiles = @(Get-ChildItem -Recurse "$out\classes" -Filter *.class | ForEach-Object FullName)
Step 'd8' { & "$tools\d8.bat" --release --min-api 30 --lib $androidJar --output "$out\dex" @classFiles @libs }

Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::Open("$out\unsigned.apk", 'Update')
[System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, "$out\dex\classes.dex", 'classes.dex') | Out-Null
$zip.Dispose()

Step 'zipalign' { & "$tools\zipalign.exe" -f -p 4 "$out\unsigned.apk" "$out\aligned.apk" }

if (-not (Test-Path $keystore)) {
    Step 'keytool' {
        # keytool prints progress on stderr, which Windows PowerShell 5 turns into a terminating error.
        $ErrorActionPreference = 'Continue'
        keytool -genkeypair -keystore $keystore -storepass hotspotband -keypass hotspotband -alias hotspotband `
            -keyalg RSA -keysize 2048 -validity 10000 -dname 'CN=Hotspot Band' 2>&1 | Out-Null
    }
}
$apk = Join-Path $root 'HotspotBand.apk'
Step 'apksigner' {
    & "$tools\apksigner.bat" sign --ks $keystore --ks-pass pass:hotspotband --ks-key-alias hotspotband `
        --out $apk "$out\aligned.apk"
}
Write-Host "Built $apk" -ForegroundColor Green

if ($Install) {
    Step 'adb install' { adb install -r $apk }
}
