[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
$RepoRoot = Split-Path -Parent $PSScriptRoot

function Invoke-Checked([scriptblock]$Command, [string]$Name) {
    & $Command
    if ($LASTEXITCODE -ne 0) { throw "$Name 失败，退出码 $LASTEXITCODE。" }
}

function Get-AndroidSdkPath {
    if ($env:ANDROID_SDK_ROOT) { return $env:ANDROID_SDK_ROOT }
    if ($env:ANDROID_HOME) { return $env:ANDROID_HOME }
    $line = Get-Content (Join-Path $RepoRoot "local.properties") | Where-Object { $_ -match '^sdk\.dir=' } | Select-Object -First 1
    if ($line) { return ($line.Split("=", 2)[1]).Replace("\\:", ":").Replace("\\\\", "\") }
    throw "未找到 Android SDK。"
}

function Get-Aapt2Path {
    $candidate = Get-ChildItem (Join-Path (Get-AndroidSdkPath) "build-tools") -Directory |
        Sort-Object { [version]($_.Name -replace "[^0-9.].*$", "") } -Descending |
        ForEach-Object { Join-Path $_.FullName "aapt2.exe" } |
        Where-Object { Test-Path $_ } | Select-Object -First 1
    if (-not $candidate) { throw "未找到 aapt2.exe。" }
    return $candidate
}

function Get-ApkMetadata([string]$ApkPath) {
    $badging = & (Get-Aapt2Path) dump badging $ApkPath 2>&1 | Out-String
    if ($LASTEXITCODE -ne 0) { throw "aapt2 读取 APK 失败。" }
    $match = [regex]::Match($badging, "package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'")
    if (-not $match.Success) { throw "无法提取 APK 元数据。" }
    return @{ PackageName = $match.Groups[1].Value; VersionCode = [long]$match.Groups[2].Value; VersionName = $match.Groups[3].Value }
}

$gradle = Join-Path $RepoRoot "gradlew.bat"
$v1GradleArgs = @(
    ":app:assembleDebug",
    "-PAPP_VERSION_CODE=100",
    "-PAPP_VERSION_NAME=1.0.0-test",
    "--console=plain"
)
$v2GradleArgs = @(
    ":app:assembleDebug",
    "-PAPP_VERSION_CODE=101",
    "-PAPP_VERSION_NAME=1.0.1-test",
    "--console=plain"
)
Push-Location $RepoRoot
try {
    Invoke-Checked { & $gradle @v1GradleArgs } "构建 Debug v1"
    $debugApk = Join-Path $RepoRoot "app\build\outputs\apk\debug\app-debug.apk"
    Invoke-Checked { adb install -r $debugApk } "安装 Debug v1"
    Invoke-Checked { & $gradle @v2GradleArgs } "构建 Debug v2"

    $dist = Join-Path $PSScriptRoot "dist\test\releases"
    New-Item -ItemType Directory -Path $dist -Force | Out-Null
    $targetApk = Join-Path $dist "campuspro-1.0.1-test.apk"
    Copy-Item -LiteralPath $debugApk -Destination $targetApk -Force
    $metadata = Get-ApkMetadata $targetApk
    if ($metadata.VersionCode -ne 101 -or $metadata.VersionName -ne "1.0.1-test") {
        throw "Debug v2 元数据不符合预期：$($metadata.VersionCode) / $($metadata.VersionName)"
    }
    $file = Get-Item $targetApk
    $manifest = [ordered]@{
        schemaVersion = 1
        packageName = $metadata.PackageName
        versionCode = $metadata.VersionCode
        versionName = $metadata.VersionName
        apkUrl = "http://127.0.0.1:8080/update-server/dist/test/releases/$($file.Name)"
        apkSizeBytes = $file.Length
        sha256 = (Get-FileHash $targetApk -Algorithm SHA256).Hash.ToLowerInvariant()
        publishedAt = [DateTimeOffset]::UtcNow.ToString("o")
        releaseNotes = @("本地升级链路测试", "验证下载、校验与系统安装器")
    }
    $manifestPath = Join-Path $dist "latest.json"
    $manifest | ConvertTo-Json -Depth 5 | Set-Content $manifestPath -Encoding utf8NoBOM
    $localSchema = (Get-Content (Join-Path $PSScriptRoot "manifest.schema.json") -Raw).Replace('"pattern": "^https://"', '"pattern": "^https?://"')
    if (-not (Test-Json -Json (Get-Content $manifestPath -Raw) -Schema $localSchema)) { throw "本地清单未通过 JSON Schema。" }
    Invoke-Checked { adb reverse tcp:8080 tcp:8080 } "配置 adb reverse"
} finally {
    Pop-Location
}

Write-Host "本地升级测试材料已准备完成。"
Write-Host "在仓库根目录启动：python -m http.server 8080"
Write-Host "然后在手机设置页点击“检查更新”。"
