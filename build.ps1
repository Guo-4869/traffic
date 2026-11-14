[CmdletBinding()]
param(
    [string]$Jdk,
    [string]$Proxy,
    [string[]]$Tasks = @(':core:test', ':app:testDebugUnitTest', ':app:assembleDebug', ':app:lintDebug')
)
$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
if (!$Jdk) { $Jdk = $env:JAVA_HOME }
if (!$Jdk -or !(Test-Path -LiteralPath "$Jdk\bin\javac.exe")) {
    $Jdk = Join-Path $root '.toolchain\jdk'
}
if (!(Test-Path -LiteralPath "$Jdk\bin\jlink.exe")) { throw 'A complete JDK 17+ including jlink is required. Run tools\bootstrap.ps1 -WithJdk or pass -Jdk.' }
$env:JAVA_HOME = $Jdk
$env:GRADLE_USER_HOME = Join-Path $root '.toolchain\gradle-home'
$env:ANDROID_USER_HOME = Join-Path $root '.toolchain\android-user'
$gradle = Join-Path $root '.toolchain\gradle-8.11.1\bin\gradle.bat'
if (!(Test-Path -LiteralPath $gradle)) { throw 'Run tools\bootstrap.ps1 first, or use the Gradle wrapper.' }
$arguments = @('--no-daemon', '--console=plain', '-p', $root)
if ($Proxy) {
    $uri = [Uri]$Proxy
    $arguments += @("-Dhttp.proxyHost=$($uri.Host)", "-Dhttp.proxyPort=$($uri.Port)", "-Dhttps.proxyHost=$($uri.Host)", "-Dhttps.proxyPort=$($uri.Port)")
}
& $gradle @arguments @Tasks
if ($LASTEXITCODE -ne 0) { throw "Gradle failed ($LASTEXITCODE)" }
if (Test-Path -LiteralPath "$root\app\build\outputs\apk\debug\app-debug.apk") {
    New-Item -ItemType Directory -Force -Path "$root\dist" | Out-Null
    Copy-Item -LiteralPath "$root\app\build\outputs\apk\debug\app-debug.apk" -Destination "$root\dist\Traffic-0.2.0-debug.apk" -Force
    Get-FileHash -LiteralPath "$root\dist\Traffic-0.2.0-debug.apk" -Algorithm SHA256
}
