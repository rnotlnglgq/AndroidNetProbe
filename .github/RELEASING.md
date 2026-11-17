# 版本发布

## 日常检查

面向 `main` 的 PR、`main` 推送和手动 CI 会执行 Android 构建、JVM 测试、主机 JNI、服务器测试与 Lint，并验证 APK 签名、版本和 native 布局。Actions 构建产物保留 14 天。

CI 固定 Ubuntu 24.04、JDK 25、SDK Platform 37.0、Build Tools 36.0.0、NDK 25.1.8937393、CMake 3.22.1。Android 13–15 模拟器检查按需要另行安排。

## 发布包

应用名称为 NetProbe，包名为 `com.rnotlnglgq.netprobe`。正式附件为 `NetProbe-X.Y.Z-arm64-v8a.apk`、`NetProbe-X.Y.Z-armeabi-v7a.apk` 和 `NetProbe-X.Y.Z-universal.apk`。前两个各含对应 ARM 架构，universal 覆盖 arm64-v8a、armeabi-v7a、x86 和 x86_64，均使用 release 构建。当前最低 Android 8 / API 26，主要使用目标为 Android 15。

debug 与 release 为内部构建类型，公开 Release 提供上述三个 APK，语言由包内资源选择。debug APK 保留在 CI 产物中。构建信息逐包记录名称、包名、SDK、构建类型、ABI、签名与摘要。三个包沿用同一 versionCode，可按设备架构选择。

普通本机构建与日常 CI 默认只生成 universal；tag 发布流程在完整检查后，额外执行 `assembleRelease -PnetprobeAbi=arm64-v8a` 与 `assembleRelease -PnetprobeAbi=armeabi-v7a`。产物先保存在 `app/build/distributions/`，逐包验证后写入 `dist/`。

## 共享签名

项目采用公开共享签名。`signing/public-research.p12` 和 `signing/public-signing.properties` 提供既有 release 身份，证书指纹位于 `signing/certificate-sha256.txt`。取得这些文件的人都能签署可覆盖现有 release 的修改版，安装者自行核对来源。

Gradle 优先读取本机 `release-signing.properties`，随后使用仓库共享配置。自有项目可通过本机配置选择自己的签名。Actions release 检查要求 APK 使用仓库记录的证书。签名密钥与密码按项目策略公开，Actions 使用 `GITHUB_TOKEN` 发布文件。

## 发版步骤

1. 在 `app/build.gradle.kts` 设置 `versionName`，递增 `versionCode`，合入 `main`。
2. 为该提交创建 `vX.Y.Z` tag 并提交到远端，例如版本 `0.1.0` 对应 `v0.1.0`。
3. Release workflow 检查 tag 与源码版本、提交归属和祖先版本号，完成全部构建检查后打包。
4. 发布 job 下载已检查的文件，核对 SHA256，创建草稿并上传 APK、`SHA256SUMS`、`build-info.json`，随后公开 Release。

附件中的构建信息记录源码提交、版本、签名证书和工具链。`SHA256SUMS` 覆盖 APK 与构建信息文件。

已存在的 Release 会使创建步骤退出。发布期间失败留下的草稿可人工检查、处理后重跑。发布后的附件保持原样，需要修订时使用新版本 tag。

## 仓库设置

建议为 `main` 启用 PR 与必需 CI 检查，并通过 ruleset 管理 `v*` tag 的创建、移动和删除。PR/构建 job 使用 `contents: read`；发布 job 使用 `contents: write`。Action 依赖固定到上游提交 SHA，更新时核对对应仓库的变更。

首次启用工作流后，在 Actions 页面确认 SDK 安装与构建结果。Android 15 手工安装可进一步验证覆盖更新和运行行为。
