[CmdletBinding()]
param([string]$Jdk = 'D:\program_files\PyCharm 2024.3.1.1\jbr')
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$output = Join-Path $root 'core\build\standalone-tests'
New-Item -ItemType Directory -Force -Path $output | Out-Null
$sources = @(Get-ChildItem (Join-Path $root 'core\src\main\java') -Recurse -Filter '*.java' | ForEach-Object FullName)
$sources += Join-Path $root 'tools\CoreCheck.java'
& "$Jdk\bin\javac.exe" -encoding UTF-8 -d $output @sources
if ($LASTEXITCODE -ne 0) { throw 'Core compilation failed' }
& "$Jdk\bin\java.exe" -ea -cp $output CoreCheck
if ($LASTEXITCODE -ne 0) { throw 'Core checks failed' }
