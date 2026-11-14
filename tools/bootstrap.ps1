[CmdletBinding()]
param([string]$Proxy,[switch]$WithJdk)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$cache = Join-Path $root '.toolchain'
New-Item -ItemType Directory -Force -Path $cache | Out-Null
function Fetch([string]$Url, [string]$Name) {
    $dest = Join-Path $cache $Name
    if (Test-Path -LiteralPath $dest) { return $dest }
    $arguments = @('--fail', '--location', '--ssl-no-revoke', '--retry', '2', '--connect-timeout', '20', '--max-time', '300', '--silent', '--show-error')
    if ($Proxy) { $arguments += @('--proxy', $Proxy) }
    & curl.exe @arguments $Url --output "$dest.part"
    if ($LASTEXITCODE -ne 0) { throw "Download failed: $Url" }
    Move-Item -LiteralPath "$dest.part" -Destination $dest
    return $dest
}
$gradle = Fetch 'https://services.gradle.org/distributions/gradle-8.11.1-bin.zip' 'gradle-8.11.1-bin.zip'
$sha = Fetch 'https://services.gradle.org/distributions/gradle-8.11.1-bin.zip.sha256' 'gradle-8.11.1-bin.zip.sha256'
if ((Get-FileHash -LiteralPath $gradle -Algorithm SHA256).Hash -ne (Get-Content -LiteralPath $sha -Raw).Trim()) { throw 'Gradle SHA256 mismatch' }
if (!(Test-Path -LiteralPath "$cache\gradle-8.11.1\bin\gradle.bat")) {
    Expand-Archive -LiteralPath $gradle -DestinationPath $cache -Force
}
$platform = Fetch 'https://dl.google.com/android/repository/platform-35_r02.zip' 'platform-35_r02.zip'
$buildTools = Fetch 'https://dl.google.com/android/repository/build-tools_r35_windows.zip' 'build-tools_r35_windows.zip'
$sdk = Join-Path $cache 'android-sdk'
New-Item -ItemType Directory -Force -Path "$sdk\platforms", "$sdk\build-tools" | Out-Null
if (!(Test-Path -LiteralPath "$sdk\platforms\android-35\android.jar")) {
    $temp = Join-Path $cache 'platform-unpack'
    Expand-Archive -LiteralPath $platform -DestinationPath $temp -Force
    $folder = Get-ChildItem -LiteralPath $temp -Directory | Select-Object -First 1
    Move-Item -LiteralPath $folder.FullName -Destination "$sdk\platforms\android-35"
}
if (!(Test-Path -LiteralPath "$sdk\build-tools\35.0.0\aapt2.exe")) {
    $temp = Join-Path $cache 'build-tools-unpack'
    Expand-Archive -LiteralPath $buildTools -DestinationPath $temp -Force
    $folder = Get-ChildItem -LiteralPath $temp -Directory | Select-Object -First 1
    Move-Item -LiteralPath $folder.FullName -Destination "$sdk\build-tools\35.0.0"
}
$sdkPath = $sdk.Replace('\', '/')
if ($WithJdk -and !(Test-Path -LiteralPath "$cache\jdk\bin\jlink.exe")) {
    $jdk = Fetch 'https://aka.ms/download-jdk/microsoft-jdk-17-windows-x64.zip' 'microsoft-jdk17.zip'
    $temp=Join-Path $cache 'jdk-unpack'
    Expand-Archive -LiteralPath $jdk -DestinationPath $temp -Force
    $folder=Get-ChildItem -LiteralPath $temp -Directory | Select-Object -First 1
    Move-Item -LiteralPath $folder.FullName -Destination "$cache\jdk"
}
[IO.File]::WriteAllText((Join-Path $root 'local.properties'), "sdk.dir=$sdkPath`n", [Text.UTF8Encoding]::new($false))
Write-Host 'Toolchain ready. Run .\build.ps1'
