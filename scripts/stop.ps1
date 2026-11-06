$ErrorActionPreference = 'Stop'
try {
    $projectRoot = Split-Path -Parent $PSScriptRoot
    $projectJar = Join-Path $projectRoot 'target\my-shop-2.0.0.jar'
    # 只停止本项目 jar 方式启动的 Java，不停止其他 Java 项目或 MySQL 服务。
    $applications = @(Get-CimInstance Win32_Process -Filter "Name = 'java.exe'" | Where-Object {
        $_.CommandLine -and $_.CommandLine.Contains($projectJar) -and $_.CommandLine.Contains('-jar')
    })
    if (!$applications.Count) {
        Write-Host '没有发现由本项目启动脚本运行的Java程序。IDEA启动的程序请使用IDEA红色停止按钮。' -ForegroundColor Yellow
    }
    foreach ($application in $applications) {
        Stop-Process -Id $application.ProcessId -ErrorAction Stop
        Write-Host '数码店脚本进程已停止。现在可以用IDEA运行ShopApplication。' -ForegroundColor Green
    }
} catch { Write-Host $_.Exception.Message -ForegroundColor Red; exit 1 }