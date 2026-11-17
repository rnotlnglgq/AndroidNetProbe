# NetProbe

Android 网络信息可见性探针。用于在自己的设备上查看应用能读取哪些网络信息，以及授权前后的变化。本项目旨在帮助用户理解Android软件开发商具备的网络部分的遥测能力。

## 功能

| 功能系列 | 可查看内容 | 使用条件 |
|---|---|---|
| 网络概览 | 默认网络、VPN、计费状态、互联网验证 | 前台读取 |
| 接口与 IP | IPv4/IPv6、前缀、地址类型、接口状态、MTU | 按接口切换，支持复制地址和 CIDR |
| DNS 与路由 | DNS、Private DNS、NAT64、路由、HTTP 代理 | 当前网络的系统可见信息 |
| 运营商 | SIM/注册运营商、MCC/MNC、国家、漫游、Carrier ID、信号 | 基础字段直接读取，逐订阅信息需要电话权限 |
| 订阅记录 | 当前 SIM、显示名称、eSIM、系统保留的历史订阅 | 电话权限；历史记录需 Android 14+ 并手动读取 |
| 服务状态 | 网络制式、注册域、PLMN、无线信号 | 部分查询需要电话与位置权限 |
| Wi-Fi | SSID/BSSID、频率、安全类型、连接速率 | 精确位置权限和系统位置开关 |
| 系统属性 | 型号、版本、运营商及网络相关固定属性 | 手动执行 `getprop` 读取，范围由系统策略决定 |
| Native 接口 | Bionic `getifaddrs()` 的接口与地址视图 | 手动读取，测试功能 |
| 公网检测 | IPv4/IPv6 连通性、出口 IP、Cloudflare 节点 | 点击确认后连接 Cloudflare 或自有 HTTPS 服务 |
| 记录与导出 | 前台变化监测、权限对照、JSON 查看与导出 | 可选择遮挡版或完整数据 |

## 使用

从 [Releases](https://github.com/rnotlnglgq/AndroidNetProbe/releases) 获取 APK。Android 15 是主要使用目标，Android 13、14、15 模拟器已完成基础检查。

顶部页签按信息类别浏览，接口和订阅提供子页签。“显示完整值”用于查看、复制精确地址；“更多”提供 JSON、权限对照和公网配置。各栏标题标出运行时授权条件，API 来源与错误可在“技术详情”查看。

结果主要保存在内存，读取开关和端点配置保存在应用私有配置中。JSON 通过系统文件选择器保存，默认遮挡地址和运营商信息；选择云盘时，文件可能由云盘同步。

公网检测由用户确认后执行，接收方会看到出口 IP 和请求时间。自有服务的部署方式见 [服务端说明](server/README.md)。字段结构与人工示例见 [Schema](samples/schema-1.0.json) 和 [samples](samples/)。

## 自行修改

使用 Android Studio 打开项目。Release 构建默认使用仓库内的共享签名，便于修改版之间覆盖更新；Android 以相同签名接受这些更新，来源由安装者自行核对。本机 `release-signing.properties` 可覆盖默认签名。

CI 与版本 tag 发布流程见 [维护说明](.github/RELEASING.md)。

## 许可证

本项目采用 [GNU GPL v3.0](LICENSE)（GPL-3.0-only）。
