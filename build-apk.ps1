$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
if (-not $env:JAVA_HOME -and (Test-Path 'C:\Program Files\Android\Android Studio\jbr')) {
    $env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
}
if (-not $env:ANDROID_HOME -and (Test-Path "$env:LOCALAPPDATA\Android\Sdk")) {
    $env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
}
if (-not $env:ANDROID_HOME) { throw 'Zainstaluj Android Studio i Android SDK Platform 35. Ustaw ANDROID_HOME na katalog SDK.' }
$gradleVersion = '8.9'
$toolsPath = Join-Path $PSScriptRoot '.build-tools'
$archivePath = Join-Path $toolsPath "gradle-$gradleVersion-bin.zip"
$gradlePath = Join-Path $toolsPath "gradle-$gradleVersion\bin\gradle.bat"
if (-not (Test-Path $gradlePath)) {
    New-Item -ItemType Directory -Force $toolsPath | Out-Null
    $url = "https://services.gradle.org/distributions/gradle-$gradleVersion-bin.zip"
    Invoke-WebRequest $url -OutFile $archivePath
    $expected = (Invoke-WebRequest "$url.sha256").Content.Trim().ToLower()
    $actual = (Get-FileHash $archivePath -Algorithm SHA256).Hash.ToLower()
    if ($expected -ne $actual) { throw 'Niepoprawna suma SHA256 pobranego Gradle.' }
    Expand-Archive $archivePath -DestinationPath $toolsPath -Force
}
& $gradlePath --no-daemon assembleDebug
if ($LASTEXITCODE -ne 0) { throw 'Budowa APK nie powiodla sie. Sprawdz komunikaty powyzej.' }
Write-Host "Gotowy APK: $PSScriptRoot\app\build\outputs\apk\debug\app-debug.apk"
