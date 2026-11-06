param([switch]$Rebuild, [int]$Port = 8080)
$ErrorActionPreference = 'Stop'
$script:ShopRoot = $PSScriptRoot
. (Join-Path $PSScriptRoot 'scripts\toolchain.ps1')
try {
    Set-Location -LiteralPath $PSScriptRoot
    $tools = Get-ShopToolchain
    $config = Join-Path $PSScriptRoot 'config\application-local.yml'
    if (!(Test-Path -LiteralPath $config) -and !$env:SHOP_DB_PASSWORD) {
        throw '尚未准备数据库。请先运行 setup-database.cmd，在本机输入 MySQL 管理员密码。'
    }
    $jar = Join-Path $PSScriptRoot 'target\my-shop-2.0.0.jar'
    $newestSource = Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'src\main') -Recurse -File |
        Sort-Object LastWriteTime -Descending | Select-Object -First 1
    $mustBuild = $Rebuild -or !(Test-Path -LiteralPath $jar)
    if (!$mustBuild) {
        $jarTime = (Get-Item -LiteralPath $jar).LastWriteTime
        $mustBuild = $newestSource.LastWriteTime -gt $jarTime -or (Get-Item -LiteralPath 'pom.xml').LastWriteTime -gt $jarTime
    }
    if ($mustBuild) { Invoke-ShopMaven -MavenArguments @('package', '-DskipTests') }
    Write-Host "使用 JDK：$($tools.Jdk)" -ForegroundColor Green
    Write-Host "启动成功出现 Started ShopApplication 后访问 http://localhost:$Port 。停止应用按 Ctrl+C。"
    & $tools.Java '-Dfile.encoding=UTF-8' '-jar' $jar "--server.port=$Port"
    if ($LASTEXITCODE -ne 0) { throw '应用启动或运行失败，请保留错误信息。' }
} catch {
    Write-Host $_.Exception.Message -ForegroundColor Red
    exit 1
}
