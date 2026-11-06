param([switch]$Offline)
$ErrorActionPreference = 'Stop'
$script:ShopRoot = $PSScriptRoot
. (Join-Path $PSScriptRoot 'scripts\toolchain.ps1')
$coverageScratch = Join-Path ([IO.Path]::GetTempPath()) ('shop-coverage-' + [Guid]::NewGuid().ToString('N'))
try {
    Set-Location -LiteralPath $PSScriptRoot
    New-Item -ItemType Directory -Path $coverageScratch | Out-Null
    # Windows 下 Java agent 可能误读中文命令行路径；覆盖率临时数据用系统临时目录。
    $coverageData = Join-Path $coverageScratch 'jacoco.exec'
    $arguments = @('verify', "-Djacoco.destFile=$coverageData", "-Djacoco.dataFile=$coverageData")
    if ($Offline) { $arguments += '-o' }
    Invoke-ShopMaven -MavenArguments $arguments
    if (Test-Path -LiteralPath $coverageData) {
        Copy-Item -LiteralPath $coverageData -Destination (Join-Path $PSScriptRoot 'target\jacoco.exec') -Force
    }
    Write-Host '测试完成。测试结果在 target\surefire-reports，覆盖率在 target\site\jacoco\index.html。' -ForegroundColor Green
} catch { Write-Host $_.Exception.Message -ForegroundColor Red; exit 1 }
finally {
    if (Test-Path -LiteralPath $coverageScratch) {
        # 仅清理本次创建且位于系统临时目录内的固定前缀目录。
        $resolvedScratch = [IO.Path]::GetFullPath($coverageScratch)
        $tempRoot = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
        if ($resolvedScratch.StartsWith($tempRoot, [StringComparison]::OrdinalIgnoreCase) -and (Split-Path -Leaf $resolvedScratch).StartsWith('shop-coverage-')) {
            Remove-Item -LiteralPath $resolvedScratch -Recurse -Force
        }
    }
}