# 用 IDEA 打开、运行和展示完整项目

这份说明按当前电脑准备：项目在 `C:\Users\Q\Desktop\java\版本3`，JDK、Maven、MySQL 在 `D:\mecodingtool`。IDEA 的具体按钮名称会随版本和中文语言包略有变化，文中同时给出常见英文名称。

完成后的使用方式是：**在 IDEA 中运行 Java 后端，在浏览器中操作数码商城页面。** 不需要把 HTML 文件单独拖进浏览器，也不需要安装前端开发工具。

> 改版前的基础版本已在这台电脑的 MySQL84（3306）完成首次建库、自动迁移和老板登录。如果桌面完整项目的 `config/application-local.yml` 仍存在且连接可用，导入 IDEA 后可直接运行，无需再次输入 root 密码。数码版启动时会执行新增迁移；本轮实测结果见 [测试与验证](05-测试与验证.md)。

## 第一次打开项目

1. 启动 IntelliJ IDEA。如果已经打开别的项目，选左上角 **File → Open（文件 → 打开）**；欢迎页则直接点 **Open**。
2. 选择 `C:\Users\Q\Desktop\java\版本3` 这个文件夹，点确定。不要只打开 `src`，也不要只双击某个 `.java` 文件。
3. 如果 IDEA 问“在当前窗口还是新窗口打开”，选新窗口比较便于保留版本1。如果出现信任项目提示，确认路径是这份自己准备的数码零售项目后选择信任。
4. 左侧 Project 应看到 `pom.xml`、`src`、`docs` 等内容。`pom.xml` 是 Maven 工程说明，IDEA 通常会自动识别并导入。
5. 等右下角的索引和 Maven 导入结束。第一次下载依赖会慢一些。此时红色提示未必代表 Java 代码有问题，先确认导入是否完成。

如果只看到普通文件、右侧没有 Maven 工具窗口：在 `pom.xml` 上点右键，找 **Add as Maven Project（添加为 Maven 项目）**。随后在 Maven 工具窗口点击 **Reload All Maven Projects（重新加载所有 Maven 项目）**。

## 选择本项目的 JDK 21

电脑原来的默认 Java 是 8，而完整项目使用 Java 21。只需要把这个 IDEA 项目设置正确，不必为了它卸载旧 JDK。

1. 进入 **File → Project Structure（文件 → 项目结构）**，Windows 默认快捷键通常是 `Ctrl+Alt+Shift+S`。
2. 左边选 **Project（项目）**。
3. 在 **SDK** 下拉框中选 JDK 21。如果没有，点 **Add SDK → JDK（添加 SDK → JDK）**。
4. 选这个目录：

   ```text
   D:\mecodingtool\jdk-21
   ```

   选择 JDK 的根目录，不选它里面的 `bin`，也不选择 `java.exe`。
5. **Language level（语言级别）** 选 **SDK default** 或 **21**。不需要启用 Preview。
6. 如果 **Modules → Dependencies（模块 → 依赖）** 中有单独的 Module SDK，选 **Project SDK**。
7. 点击 **Apply / OK（应用 / 确定）**。

本次检查的安装版本为 Temurin 21.0.13。项目只要求兼容 Java 21，不需要在代码中写死这个补丁版本。

## 让 Maven 也使用 JDK 21

Project SDK 和 Maven 使用的 JDK 是两个相关但可能分别配置的位置。若 Java 文件正常、Maven 却提示 `release version 21 not supported`，应检查这里。

打开 **File → Settings → Build, Execution, Deployment → Build Tools → Maven（文件 → 设置 → 构建、执行、部署 → 构建工具 → Maven）**：

- **Maven home path**：可以选 IDEA 的 **Bundled（内置）**，也可以选 `D:\mecodingtool\apache-maven-3.9.11`。
- **Importing → JDK for importer**：选 **Project JDK / JDK 21**。
- **Runner → JRE**：选 **Project JDK / JDK 21**。虽然这里的选项叫 JRE，实际可以使用完整 JDK。
- 不熟悉时保留默认用户 settings 和本地仓库位置，不要为了消除红线随意删掉依赖缓存。

应用设置后，在右侧 Maven 工具窗口重新加载项目。Maven 从 `pom.xml` 读取依赖，下载 Spring Boot、MyBatis、MySQL 驱动等 jar；这些依赖不需要逐个手动安装。

## 准备数据库连接

MySQL 服务和 Java 应用是两个独立进程。安装过 MySQL Configurator 不代表必须一直打开配置窗口；只要 `MySQL84` 服务运行即可。

项目使用数据库 `my_shop_v2`，日常连接账号由本地建库脚本准备。网站账号 `admin` 与 MySQL 的 `root` 是不同账号。

若 `config/application-local.yml` 已存在且已验证可连接，直接进入下一节，不需要再建一次库。若没有本地配置，可以直接双击项目根目录的 `setup-database.cmd`；也可以在项目根目录打开 PowerShell，执行：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File ".\scripts\setup-database.ps1"
```

按脚本提示在自己电脑上输入 MySQL 管理员密码。输入密码时不显示字符是正常情况。不要把密码发进聊天，也不要把包含密码的本地配置投到答辩大屏上。

脚本负责准备项目数据库、项目账号和本地配置；第一次启动 Java 后，Flyway 才根据迁移文件建立业务表。演示账号和商品由 Java 初始化逻辑加入。

本地配置位于：

```text
C:\Users\Q\Desktop\java\版本3\config\application-local.yml
```

这个文件不属于需要公开分享的源码。更换电脑时，要在新电脑重新配置数据库连接；复制 Java 文件不会把 MySQL 服务和数据库数据一起复制过去。

## 在 IDEA 中启动 Java

如果之前通过脚本体验网页，先双击项目根目录的 停止脚本启动.cmd，然后再在IDEA运行。IDEA运行的程序用IDEA红色停止按钮停止。

1. 在左侧展开：

   ```text
   src
     main
       java
         edu.shop
           ShopApplication
   ```

   IDEA 可能把 `edu.shop` 显示成合并的包名，也可能逐层显示 `edu`、`shop`，两种都是同一个位置。
2. 打开 `ShopApplication.java`，找到：

   ```java
   public static void main(String[] args) {
       SpringApplication.run(ShopApplication.class, args);
   }
   ```

3. 点击 `main` 方法左边的绿色三角，选 **Run 'ShopApplication.main()'（运行）**。没有 Spring 专用工具面板也能以普通 Java 主类方式运行。
4. 如果需要调整配置：点右上角运行配置下拉框 → **Edit Configurations（编辑配置）**，检查：

   | 选项 | 应选内容 |
   |---|---|
   | Main class | `edu.shop.ShopApplication` |
   | JDK / JRE | Project SDK，也就是 JDK 21 |
   | Use classpath of module | 当前数码零售 Maven 模块 |
   | Working directory | `$PROJECT_DIR$`，或完整项目根目录 `C:\Users\Q\Desktop\java\版本3` |

   工作目录必须是包含 `pom.xml` 和 `config` 的完整项目根目录，这样才能找到本地数据库配置。一般不需要把密码再填到 VM options 或 Program arguments。
5. 等待下方 Run 控制台出现 `Started ShopApplication`，并检查没有紧接着出现退出或数据库错误。
6. 打开浏览器访问：

   ```text
   http://localhost:8080
   ```

7. 用 README 中的演示账号登录，例如老板 `admin`，演示密码 `Admin@12345`。

日常操作请保持同一地址。`localhost` 与 `127.0.0.1` 都可以指向本机，但浏览器把它们视为不同站点，登录会话不会自动共用。

## 不用 IDEA 也能启动

在资源管理器中双击项目根目录的 `start.cmd`，或在项目根目录 PowerShell 执行：

```powershell
.\start.ps1
```

脚本使用 `D:\mecodingtool` 中的 JDK 和 Maven，不依赖当前命令行默认 Java 8。具体行为和错误提示以脚本为准。

脚本会在缺少 jar 或源文件更新后重新打包。需要强制重新打包，或者临时换一个 Web 端口时，可分别执行：

```powershell
.\start.ps1 -Rebuild
.\start.ps1 -Port 8081
```

第二条启动后访问 `http://localhost:8081`。更换端口只是解决不同 Web 进程的监听地址冲突，日常仍推荐一次运行一份应用。

同一时间通常只启动一份应用。已经用脚本启动后，再点 IDEA 的 Run，第二份可能提示 8080 端口占用。准备在 IDEA 中展示运行过程时，先停止脚本启动的那份。

## 停止、重启与保存代码

- **停止**：在 IDEA 下方 Run 窗口点红色方块。脚本启动则在相应控制台按 `Ctrl+C`。
- **重启**：先停止旧应用，再点击绿色三角重新运行。
- **修改 Java 后**：等待保存，重新运行应用，让新代码生效。此项目不要求掌握热部署才能使用。
- **关闭浏览器**：只关页面，不会自动停止 Java；关闭 IDEA 运行进程也不会删除 MySQL 数据。
- **数据库**：商品、订单等已经提交的业务数据在应用重启后仍保留。未完成的排队票据由启动恢复逻辑处理。

## 在 IDEA 里怎么展示调用关系

建议先选“商品查询”这条最短路径，再讲“结账事务”和“排队”。不用一次展开所有包。

| 想做的操作 | Windows 默认快捷键或菜单 | 用途 |
|---|---|---|
| 跳到一个方法的定义 | `Ctrl+B`，或按住 Ctrl 点击方法名 | 从 Controller 跳到 Service，再到 Mapper |
| 查谁调用了这个方法 | `Alt+F7`，或右键 Find Usages | 解释一个方法被哪些接口使用 |
| 按类名查找 | `Ctrl+N` | 直接输入 `OrderService` |
| 按文件名查找 | `Ctrl+Shift+N` | 查 `application.yml` 或文档 |
| 全局搜索文字 | `Ctrl+Shift+F` | 查接口路径、SQL、业务错误消息 |
| 给选中的代码整理格式 | `Ctrl+Alt+L` | 让缩进便于投屏阅读 |
| 放大字体 | 设置 Editor → Font，或开启鼠标滚轮缩放 | 提前调整到教室后排能看清 |

快捷键可能被其他键位方案或系统软件占用，这时用右键菜单即可。中文菜单通常也保留相近名称。

推荐打开这些标签：

1. `ShopApplication`：启动入口。
2. `CatalogController` → `CatalogService` → `CatalogMapper`：一次请求如何查询商品。
3. `DiscountPolicy` → `AbstractDiscountPolicy` → `RateDiscountPolicy`：接口、抽象类、继承和多态。
4. `OrderService`：事务、数据库行锁、订单、库存和积分。
5. `QueueService`：固定柜台分配、销售员权限、人工确认；再读 `CashierService` 的员工创建、四人上限和柜台安排。

`Mapper` 是接口，IDEA 可能找不到我们手写的实现类。它的实现由 MyBatis 在运行时生成，读本项目的 SQL 注解即可说明数据库操作，无需进入框架内部源码。

## 用断点观察一次商品查询

调试是理解代码的辅助方法，正式演示前可先练习：

1. 在 `CatalogController.products(...)` 方法可执行代码所在行的左侧栏点击，出现红点。
2. 用 **Debug（小虫子按钮）** 启动应用。
3. 在浏览器登录后打开商品页面，程序停到断点。
4. 看调试窗口中的 `keyword`、`categoryId`；按 **Step Into（通常 F7）** 进入本项目的业务方法，按 **Step Over（通常 F8）** 执行当前行，按 **Resume（通常 F9）** 继续。
5. 调试结束后取消断点，停止调试进程。

程序停在断点期间，浏览器一直等待是正常现象。第一次学习不要在扣库存的事务中长时间暂停，这会使数据库锁一直被占用，影响其他页面操作。

## 运行自动化测试

双击根目录的 `test.cmd`，或在项目根目录 PowerShell 运行：

```powershell
.\test.ps1
```

也可以在 IDEA 右侧 Maven 工具窗口展开 **Lifecycle**，运行 **verify**。在中文目录中优先用 `test.cmd`：它把覆盖率临时数据写到系统临时目录，避开部分 Java agent 对中文命令行路径的解析问题。测试使用项目的隔离测试配置，不应清空日常演示数据库。测试结果在 `target/surefire-reports`，覆盖率报告在 `target/site/jacoco/index.html`；实际执行结论以 `05-测试与验证.md` 为准。

如果机器已经缓存了全部依赖，`test.ps1 -Offline` 可要求 Maven 离线执行。离线时提示缺少依赖，说明缓存还不完整，应先正常联网下载，不要把这种情况当成业务测试失败。

## 常见现象与处理

| 现象 | 优先检查 |
|---|---|
| Java 21、`record`、三引号文本块报红 | Project SDK、Language level、Maven JDK 是否都为 21 |
| 找不到 `org.springframework...` | Maven 是否导入完成；重新加载 Maven，查看下载错误 |
| 没有绿色运行按钮 | 是否按整个 Maven 项目打开；是否使用 JDK；源码是否完成索引 |
| 启动时报数据库密码或权限错误 | 本地配置是否存在、工作目录是否正确、账号密码是否有效 |
| 找不到 `my_shop_v2` | 首次准备数据库是否成功 |
| 连接数据库超时 | `MySQL84` 是否运行、地址端口是否匹配 |
| `Port 8080 was already in use` | 是否有另一份脚本或 IDEA 进程已启动 |
| 浏览器显示连接失败 | Java 是否已启动成功，而不只是 IDEA 打开了文件 |
| 启动成功但没有演示账号 | 是否指向了预期项目库、演示种子配置是否启用；不要直接删除已有数据 |
| 登录后某些按钮不可见 | 当前账号角色是否拥有该功能 |
| 页面写操作提示令牌过期 | 刷新页面并重新登录，使用同一个访问地址 |

排查时保留完整错误类型和相关消息。不要把数据库密码、会话 Cookie 或本地配置文件内容直接粘到公开记录中。

