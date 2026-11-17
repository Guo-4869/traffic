[CmdletBinding()]
param(
    [string]$Tag = 'v0.3.0',
    [string]$Commit,
    [string]$Proxy,
    [switch]$CheckOnly
)
$ErrorActionPreference='Stop'
$env:GIT_TERMINAL_PROMPT='0'
$env:GCM_INTERACTIVE='never'
# Credentials stay in memory and are sent only to GitHub's API; never log or save them.
$credentials=@{}
$lines="protocol=https`nhost=github.com`n`n" | git -c credential.interactive=never credential fill
if($LASTEXITCODE -ne 0){throw 'GitHub authentication unavailable'}
foreach($line in $lines){$pair=$line -split '=',2;if($pair.Count -eq 2){$credentials[$pair[0]]=$pair[1]}}
if(!$credentials.password){throw 'GitHub token unavailable'}
$headers=@{Authorization="Bearer $($credentials.password)";Accept='application/vnd.github+json';'X-GitHub-Api-Version'='2022-11-28'}
$network=@{}
if($Proxy){$network.Proxy=$Proxy}
$base='https://api.github.com/repos/Guo-4869/traffic'
if($CheckOnly){
    $repo=Invoke-RestMethod -Uri $base -Headers $headers @network
    Write-Output "Repository access: $($repo.full_name); push=$($repo.permissions.push)"
    return
}
if(!$Commit){throw 'An exact tested commit is required'}
$root=Split-Path -Parent $PSScriptRoot
$version=$Tag.TrimStart('v')
$artifact=Join-Path $root "dist/Traffic-$version-debug.apk"
$checksum="$artifact.sha256"
if(!(Test-Path -LiteralPath $artifact) -or !(Test-Path -LiteralPath $checksum)){throw 'APK or checksum missing'}
$notes=Get-Content -LiteralPath (Join-Path $root "docs/releases/$Tag.md") -Raw
$body=@{tag_name=$Tag;target_commitish=$Commit;name="Traffic $version - Chinese and English";body=$notes;draft=$true;prerelease=$true} | ConvertTo-Json
$release=Invoke-RestMethod -Uri "$base/releases" -Method Post -Headers $headers -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($body)) @network
Write-Output "Draft release created: $($release.id)"
foreach($file in @($artifact,$checksum)){
    $name=[Uri]::EscapeDataString([IO.Path]::GetFileName($file))
    $upload=$release.upload_url.Split('{')[0]+"?name=$name"
    $asset=Invoke-RestMethod -Uri $upload -Method Post -Headers $headers -ContentType 'application/octet-stream' -InFile $file @network
    Write-Output "Uploaded: $($asset.name) ($($asset.size) bytes)"
}
# Publish only after both assets are uploaded successfully.
$published=Invoke-RestMethod -Uri "$base/releases/$($release.id)" -Method Patch -Headers $headers -ContentType 'application/json' -Body '{"draft":false}' @network
if($published.draft){throw 'Release is still a draft'}
Write-Output "Published: $($published.html_url)"
