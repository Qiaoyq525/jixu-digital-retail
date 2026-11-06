param([string]$DatabaseHost = '127.0.0.1', [int]$DatabasePort = 3306)
$ErrorActionPreference = 'Stop'
$script:ShopRoot = Split-Path -Parent $PSScriptRoot
. (Join-Path $PSScriptRoot 'toolchain.ps1')
$temporaryOptions = $null
$setupStatusPath = Join-Path $PSScriptRoot '..\config\setup-status.json'
try {
    $tools = Get-ShopToolchain
    if (!(Test-Path -LiteralPath $tools.Mysql)) { throw "找不到 MySQL 客户端：$($tools.Mysql)" }
    if ($DatabaseHost -ne '127.0.0.1' -or $DatabasePort -lt 1 -or $DatabasePort -gt 65535) {
        throw '此课程脚本仅用于本机 127.0.0.1，端口范围为 1 至 65535。'
    }
    $configDirectory = Join-Path $script:ShopRoot 'config'
    New-Item -ItemType Directory -Path $configDirectory -Force | Out-Null
    $configPath = Join-Path $configDirectory 'application-local.yml'
    if (Test-Path -LiteralPath $configPath) {
        Write-Host '本地数据库配置已经存在。通常直接运行 start.cmd 即可。' -ForegroundColor Green
        Write-Host '如确需重建连接配置，请先备份此文件，再将它改名；脚本不会删除任何业务表。'
        exit 0
    }
    Write-Host '即将创建 my_shop_v2 数据库和 shop_app_v2 专用账号；不删除已有数据。'
    $securePassword = Read-Host '请输入 MySQL root 密码（输入不显示，也不用发到聊天里）' -AsSecureString
    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword)
    try { $adminPassword = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer) }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer) }
    $escapedPassword = $adminPassword.Replace([string][char]92, ([string][char]92 + [char]92)).Replace('"', ([string][char]92 + '"')).Replace("`r", '\r').Replace("`n", '\n')
    # MySQL Windows 客户端可能误读命令行中的中文路径。临时密码文件放在系统临时目录。
    $temporaryOptions = Join-Path ([IO.Path]::GetTempPath()) ('shop-mysql-admin-' + [Guid]::NewGuid().ToString('N') + '.cnf')
    $options = "[client]`nuser=root`npassword=`"$escapedPassword`"`nhost=$DatabaseHost`nport=$DatabasePort`nprotocol=tcp`n"
    [IO.File]::WriteAllText($temporaryOptions, $options, (New-Object Text.UTF8Encoding($false)))
    $random = [Security.Cryptography.RandomNumberGenerator]::Create()
    $bytes = New-Object byte[] 24
    try { $random.GetBytes($bytes) } finally { $random.Dispose() }
    $applicationPassword = [Convert]::ToBase64String($bytes)
    $sql = @"
CREATE DATABASE IF NOT EXISTS my_shop_v2 CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER IF NOT EXISTS 'shop_app_v2'@'localhost' IDENTIFIED BY '$applicationPassword';
ALTER USER 'shop_app_v2'@'localhost' IDENTIFIED BY '$applicationPassword';
GRANT SELECT,INSERT,UPDATE,DELETE,CREATE,ALTER,INDEX,REFERENCES,DROP ON my_shop_v2.* TO 'shop_app_v2'@'localhost';
CREATE USER IF NOT EXISTS 'shop_app_v2'@'127.0.0.1' IDENTIFIED BY '$applicationPassword';
ALTER USER 'shop_app_v2'@'127.0.0.1' IDENTIFIED BY '$applicationPassword';
GRANT SELECT,INSERT,UPDATE,DELETE,CREATE,ALTER,INDEX,REFERENCES,DROP ON my_shop_v2.* TO 'shop_app_v2'@'127.0.0.1';
"@
    # SQL 走标准输入，管理员密码走临时选项文件，密码均不会出现在进程命令行中。
    $processInfo = New-Object Diagnostics.ProcessStartInfo
    $processInfo.FileName = $tools.Mysql
    $processInfo.Arguments = "--defaults-extra-file=`"$temporaryOptions`" --default-character-set=utf8mb4 --connect-timeout=8"
    $processInfo.UseShellExecute = $false
    $processInfo.CreateNoWindow = $true
    $processInfo.RedirectStandardInput = $true
    $processInfo.RedirectStandardOutput = $true
    $processInfo.RedirectStandardError = $true
    $process = [Diagnostics.Process]::Start($processInfo)
    $process.StandardInput.WriteLine($sql)
    $process.StandardInput.Close()
    $outputTask = $process.StandardOutput.ReadToEndAsync()
    $errorTask = $process.StandardError.ReadToEndAsync()
    $process.WaitForExit()
    if ($process.ExitCode -ne 0) {
        $safeError = $errorTask.Result.Replace($applicationPassword, '***')
        if ($adminPassword) { $safeError = $safeError.Replace($adminPassword, '***') }
        throw "数据库准备失败。请检查 root 密码和 MySQL84 服务。$safeError"
    }
    $yaml = @"
# 本地私有配置，不要提交或随源码分享。网站登录密码与此数据库密码无关。
spring:
  datasource:
    url: 'jdbc:mysql://127.0.0.1:$DatabasePort/my_shop_v2?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&connectionTimeZone=Asia/Shanghai&sslMode=PREFERRED'
    username: shop_app_v2
    password: '$applicationPassword'
"@
    [IO.File]::WriteAllText($configPath, $yaml, (New-Object Text.UTF8Encoding($false)))
    if (Test-Path -LiteralPath $setupStatusPath) { Remove-Item -LiteralPath $setupStatusPath -Force }
    Write-Host '数据库和连接账号准备完成！现在运行 start.cmd，Java 会自动建表并加入演示账号。' -ForegroundColor Green
} catch {
    $safeSetupMessage = $_.Exception.Message
    if ($applicationPassword) { $safeSetupMessage = $safeSetupMessage.Replace($applicationPassword, '***') }
    if ($adminPassword) { $safeSetupMessage = $safeSetupMessage.Replace($adminPassword, '***') }
    try {
        $statusJson = @{ status = 'FAILED'; message = $safeSetupMessage } | ConvertTo-Json
        [IO.File]::WriteAllText($setupStatusPath, $statusJson, (New-Object Text.UTF8Encoding($false)))
    } catch { }
    Write-Host $safeSetupMessage -ForegroundColor Red
    exit 1
}
finally {
    if ($temporaryOptions -and (Test-Path -LiteralPath $temporaryOptions)) {
        Remove-Item -LiteralPath $temporaryOptions -Force
    }
    $adminPassword = $null; $escapedPassword = $null; $applicationPassword = $null; $options = $null; $sql = $null
}
