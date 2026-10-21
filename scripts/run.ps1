<#
.SYNOPSIS
    Unified entry point for this project. Fixes Windows console mojibake when running Java.

.DESCRIPTION
    Root cause: the Windows ANSI code page is GB2312. When java's stdout is piped or
    redirected, the JVM writes GB2312 bytes while the terminal decodes UTF-8 -> mojibake.
    Note: -Dfile.encoding=UTF-8 does NOT affect stdout (verified); you must set
    stdout.encoding / stderr.encoding explicitly (JDK 19+).

.PARAMETER Task
    build | main | test | smoke | innovation | human | gui          (default: main)

.PARAMETER Jdk
    JDK home directory (>= 25). Auto-detected when omitted.

.PARAMETER Log
    Also append the output to target\run.log (UTF-8).

.EXAMPLE
    .\scripts\run.ps1
    .\scripts\run.ps1 build
    .\scripts\run.ps1 gui
#>
param(
    [ValidateSet('build', 'main', 'test', 'smoke', 'innovation', 'human', 'gui')]
    [string]$Task = 'main',
    [string]$Jdk = '',
    [switch]$Log
)

$ErrorActionPreference = 'Stop'

# ---------- 1. Force UTF-8 everywhere in the host ----------
try { chcp 65001 | Out-Null } catch { }
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.Encoding]::UTF8

$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

# ---------- 2. Locate a JDK >= 25 ----------
function Get-JdkVersion($exe) {
    if (-not (Test-Path $exe)) { return 0 }
    # PowerShell 5.1 对原生命令 stderr 的处理不可靠，统一交给 cmd 合并捕获
    $out = (cmd /c "`"$exe`" -version 2>&1" | Out-String)
    if ($out -match '"(\d+)(\.(\d+))?') {
        $major = [int]$Matches[1]
        if ($major -eq 1 -and $Matches[3]) { $major = [int]$Matches[3] }
        return $major
    }
    return 0
}

$candidates = @()
if ($Jdk -ne '') { $candidates += (Join-Path $Jdk 'bin\java.exe') }
if ($env:JAVA_HOME) { $candidates += (Join-Path $env:JAVA_HOME 'bin\java.exe') }

# VS Code ships its own Temurin runtime (redhat.java extension)
$vscodeExt = Join-Path $env:USERPROFILE '.vscode\extensions'
if (Test-Path $vscodeExt) {
    $candidates += @(Get-ChildItem -Path $vscodeExt -Recurse -Depth 5 -Filter 'java.exe' `
        -ErrorAction SilentlyContinue | Where-Object { $_.FullName -like '*redhat.java*' } |
        ForEach-Object { $_.FullName })
}
# Common JDK install roots
foreach ($base in @('C:\Program Files\Java', 'C:\Program Files\Eclipse Adoption',
    'C:\Program Files\Microsoft', 'C:\Program Files\BellSoft')) {
    if (Test-Path $base) {
        $candidates += @(Get-ChildItem -Path $base -Recurse -Depth 3 -Filter 'java.exe' `
            -ErrorAction SilentlyContinue | ForEach-Object { $_.FullName })
    }
}

$javaExe = $null
$best = 0
foreach ($candidate in ($candidates | Select-Object -Unique)) {
    $version = Get-JdkVersion $candidate
    if ($version -ge 25 -and $version -gt $best) { $best = $version; $javaExe = $candidate }
}

if (-not $javaExe) {
    Write-Host "[错误] 没有找到 JDK 25 及以上版本。" -ForegroundColor Red
    Write-Host "       已尝试的路径：" -ForegroundColor Red
    foreach ($candidate in ($candidates | Select-Object -Unique)) { Write-Host "         $candidate" }
    Write-Host "       解决方式：安装 JDK 25 后用 -Jdk 指定，例如" -ForegroundColor Yellow
    Write-Host "         .\scripts\run.ps1 -Jdk 'C:\Program Files\Java\jdk-25'" -ForegroundColor Yellow
    exit 2
}

$javacExe = Join-Path (Split-Path -Parent $javaExe) 'javac.exe'
Write-Host "JDK   : $javaExe  (Java $best)" -ForegroundColor DarkGray
Write-Host "编码   : 终端 $([Console]::OutputEncoding.WebName), 代码页 $((chcp) -replace '.*: ')" -ForegroundColor DarkGray

# ---------- 3. Dependency classpath ----------
$fxCp = (Get-ChildItem -Path lib -Filter *.jar -ErrorAction SilentlyContinue | ForEach-Object { $_.FullName }) -join ';'

# The actual mojibake fix: file.encoding does not affect stdout
$javaFlags = @('-Dfile.encoding=UTF-8', '-Dstdout.encoding=UTF-8', '-Dstderr.encoding=UTF-8')

function Invoke-JavaTask([string[]]$taskArgs, [string]$cp) {
    $all = $javaFlags + @('-cp', $cp) + $taskArgs
    if ($Log) {
        & $javaExe $all 2>&1 | ForEach-Object {
            Write-Output $_
            $_ | Out-File -Append -Encoding utf8 (Join-Path $root 'target\run.log')
        }
    }
    else {
        & $javaExe $all
    }
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}

switch ($Task) {
    'build' {
        New-Item -ItemType Directory -Force -Path 'target\classes' | Out-Null
        $src = Get-ChildItem -Path src -Recurse -Filter *.java | ForEach-Object { $_.FullName }
        $javacArgs = @('--release', '25', '-encoding', 'UTF-8', '-Xlint:all', '-cp', $fxCp, '-d', 'target\classes') + $src
        & $javacExe $javacArgs
        if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
        Write-Host "编译完成: $($src.Count) 个源文件 -> target\classes" -ForegroundColor Green
    }
    'gui' {
        if (-not $fxCp) { Write-Host '[错误] 缺少 JavaFX，请先运行 .\scripts\download-javafx.ps1' -ForegroundColor Red; exit 3 }
        if (-not (Test-Path 'target\classes\com\trading\Main.class')) {
            Write-Host '[错误] 尚未编译，请先运行 .\scripts\run.ps1 build' -ForegroundColor Red; exit 4
        }
        $all = $javaFlags + @('--module-path', $fxCp, '--add-modules', 'javafx.controls',
            '--enable-native-access=javafx.graphics', '-cp', 'target\classes',
            'com.trading.ui.TradingPlatformApplication')
        & $javaExe $all
    }
    'main' { Invoke-JavaTask @('com.trading.Main') "target\classes;$fxCp" }
    'human' { Invoke-JavaTask @('com.trading.Main', '--human') "target\classes;$fxCp" }
    'test' { Invoke-JavaTask @('com.trading.test.CoreEngineTest') "target\classes;$fxCp" }
    'smoke' { Invoke-JavaTask @('com.trading.test.SimulationSmokeTest') "target\classes;$fxCp" }
    'innovation' { Invoke-JavaTask @('com.trading.test.InnovationDemoTest') "target\classes;$fxCp" }
}
