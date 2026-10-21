# JavaFX 依赖下载脚本（Windows PowerShell 可直接执行）
# 用途：把 JavaFX 运行时 jar 拉取到项目 lib/ 目录，供编译与运行 GUI 使用。
# 用法： .\scripts\download-javafx.ps1
#       .\scripts\download-javafx.ps1 -Version 21

param(
    [string]$Version = "25"
)

$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"

$modules = @("javafx-base", "javafx-graphics", "javafx-controls")
$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$libDir = Join-Path $root "lib"
New-Item -ItemType Directory -Force -Path $libDir | Out-Null

Write-Host "目标目录: $libDir   版本: $Version"

foreach ($module in $modules) {
    foreach ($classifier in @("", "-win")) {
        $jar = "$module-$Version$classifier.jar"
        $url = "https://repo1.maven.org/maven2/org/openjfx/$module/$Version/$jar"
        $dest = Join-Path $libDir $jar
        try {
            Invoke-WebRequest -Uri $url -OutFile $dest -UseBasicParsing -TimeoutSec 120
            $size = [math]::Round((Get-Item $dest).Length / 1MB, 2)
            Write-Host "OK   $jar ($size MB)"
        }
        catch {
            Write-Host "跳过 $jar （该版本可能不存在此构件）"
        }
    }
}

Write-Host "完成。若仍提示 javafx 相关错误，请重新加载 VS Code 窗口（Reload Window）。"
