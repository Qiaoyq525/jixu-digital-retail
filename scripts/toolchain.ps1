# 只影响当前 PowerShell 进程，不修改电脑原来的 Java 8 或全局环境变量。
function Get-ShopToolchain {
    $toolsDirectory = if ($env:SHOP_TOOLS_HOME) { $env:SHOP_TOOLS_HOME } else { 'D:\mecodingtool' }
    $jdkDirectory = Join-Path $toolsDirectory 'jdk-21'
    $mavenDirectory = Join-Path $toolsDirectory 'apache-maven-3.9.11'
    $mysqlExecutable = Join-Path $toolsDirectory 'mysql-8.4\bin\mysql.exe'
    $javaExecutable = Join-Path $jdkDirectory 'bin\java.exe'
    if (!(Test-Path -LiteralPath $javaExecutable)) { throw "找不到 JDK 21：$javaExecutable；可设置 SHOP_TOOLS_HOME。" }
    if (!(Test-Path -LiteralPath (Join-Path $mavenDirectory 'bin\m2.conf'))) { throw "找不到 Maven：$mavenDirectory" }
    $repository = if ($env:SHOP_MAVEN_REPO) { $env:SHOP_MAVEN_REPO } else {
        Join-Path ([Environment]::GetFolderPath('UserProfile')) '.m2\repository'
    }
    return @{ Java = $javaExecutable; Jdk = $jdkDirectory; Maven = $mavenDirectory; Mysql = $mysqlExecutable; Repository = $repository }
}

function Invoke-ShopMaven {
    param([string[]]$MavenArguments)
    $toolchain = Get-ShopToolchain
    $env:JAVA_HOME = $toolchain.Jdk
    # 直接调用 Maven 的 Java 启动器，可避免不同终端中 mvn.cmd 和 Java 路径混淆。
    $arguments = @(
        '-Dfile.encoding=UTF-8', '-Dstdout.encoding=UTF-8', '-Dstderr.encoding=UTF-8', "-Dmaven.multiModuleProjectDirectory=$script:ShopRoot",
        "-Dmaven.home=$($toolchain.Maven)",
        "-Dclassworlds.conf=$(Join-Path $toolchain.Maven 'bin\m2.conf')",
        '-classpath', (Join-Path $toolchain.Maven 'boot\*'),
        'org.codehaus.plexus.classworlds.launcher.Launcher',
        "-Dmaven.repo.local=$($toolchain.Repository)", '--no-transfer-progress'
    ) + $MavenArguments
    & $toolchain.Java @arguments
    if ($LASTEXITCODE -ne 0) { throw 'Maven 执行失败，请查看上面的第一条 ERROR 信息。' }
}
