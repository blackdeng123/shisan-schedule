[CmdletBinding()]
param(
    [long]$VersionCode,
    [string]$VersionName,
    [string[]]$ReleaseNotes = @("修复已知问题并提升稳定性"),
    [switch]$DryRun
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
$RepoRoot = Split-Path -Parent $PSScriptRoot

function Read-Properties([string]$Path) {
    if (-not (Test-Path -LiteralPath $Path)) { throw "缺少配置文件：$Path" }
    $result = @{}
    Get-Content -LiteralPath $Path -Encoding UTF8 | ForEach-Object {
        $line = $_.Trim()
        if ($line -and -not $line.StartsWith("#")) {
            $parts = $line.Split("=", 2)
            if ($parts.Count -eq 2) { $result[$parts[0].Trim()] = $parts[1].Trim() }
        }
    }
    return $result
}

function Get-AndroidSdkPath {
    if ($env:ANDROID_SDK_ROOT) { return $env:ANDROID_SDK_ROOT }
    if ($env:ANDROID_HOME) { return $env:ANDROID_HOME }
    $local = Read-Properties (Join-Path $RepoRoot "local.properties")
    if ($local["sdk.dir"]) { return $local["sdk.dir"].Replace("\\:", ":").Replace("\\\\", "\") }
    throw "未找到 Android SDK，请配置 ANDROID_SDK_ROOT 或 local.properties。"
}

function Get-Aapt2Path {
    $buildTools = Join-Path (Get-AndroidSdkPath) "build-tools"
    $candidate = Get-ChildItem -LiteralPath $buildTools -Directory |
        Sort-Object { [version]($_.Name -replace "[^0-9.].*$", "") } -Descending |
        ForEach-Object { Join-Path $_.FullName "aapt2.exe" } |
        Where-Object { Test-Path -LiteralPath $_ } |
        Select-Object -First 1
    if (-not $candidate) { throw "Android build-tools 中未找到 aapt2.exe。" }
    return $candidate
}

function Get-ApkMetadata([string]$ApkPath) {
    $badging = & (Get-Aapt2Path) dump badging $ApkPath 2>&1 | Out-String
    if ($LASTEXITCODE -ne 0) { throw "aapt2 无法读取 APK：$badging" }
    $match = [regex]::Match($badging, "package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'")
    if (-not $match.Success) { throw "无法从 APK 提取包名和版本。" }
    return @{
        PackageName = $match.Groups[1].Value
        VersionCode = [long]$match.Groups[2].Value
        VersionName = $match.Groups[3].Value
    }
}

function Assert-LastExitCode([string]$Operation) {
    if ($LASTEXITCODE -ne 0) { throw "$Operation 失败，退出码 $LASTEXITCODE。线上清单未更新。" }
}

$config = Read-Properties (Join-Path $RepoRoot "update.properties")
foreach ($key in @("manifestUrl", "publicBaseUrl")) {
    if (-not $config[$key]) { throw "update.properties 缺少 $key。" }
}
if (-not $config["manifestUrl"].StartsWith("https://") -or -not $config["publicBaseUrl"].StartsWith("https://")) {
    throw "正式发布地址必须使用 HTTPS。"
}

$gradleArgs = @(":app:assembleRelease", "--console=plain")
if ($PSBoundParameters.ContainsKey("VersionCode")) { $gradleArgs += "-PAPP_VERSION_CODE=$VersionCode" }
if ($PSBoundParameters.ContainsKey("VersionName")) { $gradleArgs += "-PAPP_VERSION_NAME=$VersionName" }
Push-Location $RepoRoot
try {
    & (Join-Path $RepoRoot "gradlew.bat") @gradleArgs
    Assert-LastExitCode "Release 构建"
} finally {
    Pop-Location
}

$builtApk = Join-Path $RepoRoot "app\build\outputs\apk\release\app-release.apk"
if (-not (Test-Path -LiteralPath $builtApk)) { throw "未找到 Release APK：$builtApk" }
$metadata = Get-ApkMetadata $builtApk
$safeVersionName = $metadata.VersionName -replace "[^A-Za-z0-9._-]", "_"
$dist = Join-Path $PSScriptRoot "dist\releases"
New-Item -ItemType Directory -Path $dist -Force | Out-Null
$apkName = "campuspro-$safeVersionName.apk"
$distApk = Join-Path $dist $apkName
Copy-Item -LiteralPath $builtApk -Destination $distApk -Force
$apkFile = Get-Item -LiteralPath $distApk
$hash = (Get-FileHash -LiteralPath $distApk -Algorithm SHA256).Hash.ToLowerInvariant()
$baseUrl = $config["publicBaseUrl"].TrimEnd("/")
$manifest = [ordered]@{
    schemaVersion = 1
    packageName = $metadata.PackageName
    versionCode = $metadata.VersionCode
    versionName = $metadata.VersionName
    apkUrl = "$baseUrl/$apkName"
    apkSizeBytes = $apkFile.Length
    sha256 = $hash
    publishedAt = [DateTimeOffset]::UtcNow.ToString("o")
    releaseNotes = @($ReleaseNotes)
}
$manifestPath = Join-Path $dist "latest.json"
$manifest | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $manifestPath -Encoding utf8NoBOM
$schemaPath = Join-Path $PSScriptRoot "manifest.schema.json"
if (-not (Test-Json -Json (Get-Content $manifestPath -Raw) -SchemaFile $schemaPath)) {
    throw "生成的 latest.json 未通过 JSON Schema。"
}

Write-Host "已生成 $distApk"
Write-Host "已生成 $manifestPath"
if ($DryRun) {
    Write-Host "DryRun 完成：未推送服务器。"
    exit 0
}

# 以下仅正式发布需要：SSH 推送配置
foreach ($key in @("sshKeyPath", "sshHost", "remoteUpdateDir")) {
    if (-not $config[$key]) { throw "update.properties 缺少 $key。" }
}
if (-not (Get-Command scp -ErrorAction SilentlyContinue)) {
    throw "未找到 scp，请启用 Windows OpenSSH 客户端（设置 > 系统 > 可选功能）。"
}
$remoteHost = $config["sshHost"]
$remoteDir = $config["remoteUpdateDir"]
$sshKey = $config["sshKeyPath"]
if (-not (Test-Path -LiteralPath $sshKey)) { throw "SSH 私钥不存在：$sshKey" }
# BatchMode 保证脚本无交互；accept-new 首次连接自动信任主机指纹，避免阻塞发布。
$sshOptions = @("-i", $sshKey, "-o", "BatchMode=yes", "-o", "StrictHostKeyChecking=accept-new")
& ssh @sshOptions $remoteHost "mkdir -p '$remoteDir'"
Assert-LastExitCode "创建远端发布目录"
& scp @sshOptions $distApk $manifestPath "${remoteHost}:${remoteDir}"
Assert-LastExitCode "推送更新文件到服务器"
Write-Host "已推送至 ${remoteHost}:${remoteDir}"
Write-Host "发布完成：$($config['manifestUrl'])"
