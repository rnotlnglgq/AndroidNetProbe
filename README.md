# NetProbe

Android 网络信息可见性探针，当前版本 `0.1.0`。用于在自己的设备上查看普通应用能够读取的网络、运营商和系统信息，并比较授权前后的结果。

使用 Kotlin、Jetpack Compose 和少量 JNI，无需 Root。Android 15 是主要使用目标；此前已在 Android 13、14、15 模拟器完成基础检查，不代表所有设备和 ROM 均已验证。

## 功能

- 默认网络、IPv4/IPv6、DNS、路由、Private DNS、NAT64、接口地址与状态。
- 基础运营商信息；授权后查看活动订阅、SIM 名称、服务状态，以及 Android 14+ 系统保留的订阅记录。
- 授权后查看已连接 Wi-Fi 的 SSID/BSSID、信号和连接信息。
- 手动读取固定系统属性，以及测试性质的 Bionic `getifaddrs()` 接口视图。
- 前台网络变化监测、字段摘要、地址复制、权限对照和本地 JSON 导出。
- 用户确认后，通过 Cloudflare 或自有 HTTPS 端点检查 IPv4/IPv6 连通性与出口 IP。

API 来源、权限和错误可在“技术详情”查看。字段不可见、为空或被拒绝时会保留相应状态，不能把这份列表当成 Android 应用可获取信息的完整上限。

## 构建

用 Android Studio 打开仓库根目录，或使用 Gradle Wrapper。所需工具：

| 工具 | 版本 |
|---|---|
| JDK | 25 |
| Gradle / Android Gradle Plugin | 9.6.0 / 9.4.1 |
| Kotlin | 2.2.10 |
| minSdk / compileSdk / targetSdk | 26 / 37 / 37 |
| Android SDK Platform / Build Tools | 37.0 / 36.0.0 |
| NDK / CMake | 25.1.8937393 / 3.22.1 |

在 Android Studio SDK Manager 中安装对应组件并接受许可证。SDK 路径由 Android Studio 配置，或写入本机 `local.properties`：

```properties
sdk.dir=/path/to/Android/Sdk
```

```bash
./gradlew assembleDebug
```

生成 `app/build/outputs/apk/debug/app-debug.apk`。首次构建需下载 Gradle、依赖及可能缺少的 JDK。项目固定的 JVM 工具链为 JDK 25；也可让 Gradle 查找本机已安装的 JDK 25。

### Release 签名

签名配置和密钥不在仓库中。首次在自己的工作副本生成密钥：

```bash
python3 scripts/create-release-key.py
./gradlew assembleRelease
```

生成 `app/build/outputs/apk/release/app-release.apk`。脚本不会覆盖已有密钥；请安全备份 `.signing/` 和 `release-signing.properties`，后续使用同一密钥更新。缺少签名配置时，release 构建只生成未签名 APK，不使用 debug 密钥代签。debug 和 release 签名不同，不能互相覆盖安装。

native 库包含 armeabi-v7a、arm64-v8a、x86、x86_64。保留 16 KiB LOAD 对齐与 RELRO 保护；使用本机 NDK 的检查脚本可复核布局：

```bash
python3 scripts/check-native-elf.py app/build/outputs/apk/release/app-release.apk
```

## 使用与权限

顶部页签按信息类别浏览；接口与订阅提供子页签。点击“显示完整值”后可查看、复制精确地址，离开前台后恢复遮挡。“更多”提供 JSON 查看、导出、权限对照和公网配置。

| 权限 | 用途 |
|---|---|
| INTERNET、ACCESS_NETWORK_STATE | 本机网络状态及主动公网检测；安装时普通权限 |
| ACCESS_WIFI_STATE | Wi-Fi 信息；普通权限 |
| READ_PHONE_STATE | 活动订阅、历史订阅及部分网络制式查询；主动请求 |
| ACCESS_COARSE_LOCATION、ACCESS_FINE_LOCATION | 位置相关 Wi-Fi/服务状态字段；主动请求 |

Wi-Fi SSID/BSSID 需要精确位置权限与系统位置开关。历史订阅读取需要电话权限，不以位置授权为前提。栏目标题会标出运行时授权条件；技术详情区分字段本身与取得前置对象所需的权限。AndroidX 还会合并应用自定义的签名级 Receiver 保护权限。

P1 读取开关默认开启，只有已授权的字段才会在前台读取；启动不自动弹出危险权限请求。系统属性、Native 接口和历史订阅均手动读取。系统属性与 native 可见范围受设备策略影响，不提供额外权限。

## 数据与隐私

结果主要保存在内存，只有端点配置和读取开关写入应用私有配置。无分析 SDK、后台采集或快照上传。JSON 经系统文件选择器保存，默认删除敏感字段值；完整导出需明确选择。选择云盘目标可能由云盘同步文件。

公网检测需要点击确认，服务器会看到出口 IP 和请求时间；请求不携带本机快照。Cloudflare 是第三方服务，自有服务模板见 [server/README.md](server/README.md)。默认不发送探测请求。

数据结构使用 Schema `1.0`，每个字段记录值、状态、API 来源与权限条件。机器可读结构在 [docs/schema-1.0.json](docs/schema-1.0.json)，[samples/](samples/) 仅包含人工示例，不是真实设备导出。默认遮挡不等于完全匿名，设备型号、时间及拓扑元数据仍可能保留。

## 测试与限制

```bash
./gradlew testDebugUnitTest assembleDebugAndroidTest lintDebug lintRelease
python3 scripts/test-native-host.py
python3 -m unittest discover -s server -v
```

主机 JNI 测试需要 Linux、完整 JDK 和 C 编译器，使用模拟接口数据，不验证 Android 权限。设备已启动并同意测试时，可单独运行 `./gradlew connectedDebugAndroidTest`；这会在连接的兼容设备上执行测试。Android 13–15 的一次性冒烟不设为每次发布的固定要求。

跨 API 采样不是原子的；IPv6 分类不代表公网可达，SIM 空值不证明没有 SIM。历史订阅是系统仍保留的记录，不是完整换卡史。基于属性和 Bionic 的读取会因系统版本、ROM 或 SELinux 策略不同而变化。真实蜂窝、多 SIM、VPN、公网与厂商设备仍需实际验证。

## 许可证

暂未指定许可证。
