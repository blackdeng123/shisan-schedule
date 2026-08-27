[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [uri]$ManifestUrl,
    [switch]$SkipApkDownload
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
if ($ManifestUrl.Scheme -ne "https") { throw "线上更新清单必须使用 HTTPS。" }
$schemaPath = Join-Path $PSScriptRoot "manifest.schema.json"

function Show-Headers($Response) {
    foreach ($name in @("Content-Type", "Content-Length", "Cache-Control", "ETag", "Last-Modified")) {
        $value = $Response.Headers[$name]
        Write-Host ("{0}: {1}" -f $name, $(if ($value) { $value } else { "<无>" }))
    }
}

Write-Host "检查清单：$ManifestUrl"
$manifestResponse = Invoke-WebRequest -Uri $ManifestUrl -Method Get -MaximumRedirection 5
if ($manifestResponse.StatusCode -ne 200) { throw "清单返回 HTTP $($manifestResponse.StatusCode)。" }
$contentType = [string]$manifestResponse.Headers["Content-Type"]
if (-not $contentType.StartsWith("application/json", [StringComparison]::OrdinalIgnoreCase)) {
    throw "清单 Content-Type 不是 application/json：$contentType"
}
if (-not (Test-Json -Json $manifestResponse.Content -SchemaFile $schemaPath)) { throw "清单未通过 JSON Schema。" }
$manifest = $manifestResponse.Content | ConvertFrom-Json
Show-Headers $manifestResponse

$apkUri = [uri]$manifest.apkUrl
if ($apkUri.Scheme -ne "https") { throw "APK URL 必须使用 HTTPS。" }
Write-Host "检查 APK：$apkUri"
$head = Invoke-WebRequest -Uri $apkUri -Method Head -MaximumRedirection 5
if ($head.StatusCode -ne 200) { throw "APK 返回 HTTP $($head.StatusCode)。" }
$contentLength = [long]($head.Headers["Content-Length"][0])
if ($contentLength -le 0) { throw "APK 响应缺少有效 Content-Length。" }
if ($contentLength -ne [long]$manifest.apkSizeBytes) { throw "APK Content-Length 与清单不一致。" }
Show-Headers $head

if (-not $SkipApkDownload) {
    $temp = Join-Path ([IO.Path]::GetTempPath()) ("campuspro-update-check-" + [guid]::NewGuid() + ".apk")
    try {
        Invoke-WebRequest -Uri $apkUri -OutFile $temp -MaximumRedirection 5
        $file = Get-Item $temp
        if ($file.Length -ne [long]$manifest.apkSizeBytes) { throw "下载文件大小与清单不一致。" }
        $hash = (Get-FileHash $temp -Algorithm SHA256).Hash.ToLowerInvariant()
        if ($hash -ne ([string]$manifest.sha256).ToLowerInvariant()) { throw "下载文件 SHA-256 与清单不一致。" }
    } finally {
        Remove-Item -LiteralPath $temp -Force -ErrorAction SilentlyContinue
    }
}

Write-Host "更新服务检查通过。"
