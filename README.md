# TechnicsPods

> **中文为默认语言 · [English version](README_EN.md)**

TechnicsPods 是一个面向小米 HyperOS 的 LSPosed 模块，让 Technics EAH-AZ100 在系统蓝牙详情、Fusion Device Center 和相关 HyperOS 页面中以系统耳机的形式显示，并提供状态读取与受保护的降噪模式控制。

本项目是独立实现，感谢 [HuaweiPods](https://github.com/Nshpiter/HuaweiPods) 提供公开的研究参考和启发；TechnicsPods 不包含 HuaweiPods 或 Technics Audio Connect 的代码。

## 当前版本

- **版本**：`0.10.0-beta.3`（`versionCode 32`）
- **发布类型**：GitHub **Pre-release**，这是首个公开测试版
- **包名**：`dev.technics.pods`
- **适用耳机**：Technics EAH-AZ100
- **开发者**：**AixunZ**
- **公开下载**：[GitHub Releases](https://github.com/AixunZ/TechnicsPods/releases/tag/v0.10.0-beta.3)

beta.3 是公开测试版本，不代表所有 ROM、固件或系统页面都已完成实机验收。安装前请确认设备支持 LSPosed 回滚，并保留日志以便排查。

## 功能与证据等级

| 能力 | 当前状态 | 证据等级 |
| --- | --- | --- |
| 识别 AZ100 并显示系统耳机身份 | 已实现 | 已验证 |
| 读取左右耳、充电盒电量及降噪状态 | 已实现 | 已验证 |
| 系统蓝牙详情中的降噪模式切换 | 已实现，采用读前确认、ACK 和读回保护 | 已验证/需按设备复测 |
| Fusion Device Center 进入系统耳机详情页 | 已实现延迟路由 | 已验证于记录的 ROM 配置 |
| 状态栏无线耳机图标归属 | 已实现多 profile 状态协调 | 已验证于已知回调签名 |
| MiLink/Settings AZ100 设备图像替换 | 已实现保守资源匹配 | 已在测试机确认渲染 |
| Device 页面手动设备、颜色和图片选择 | 小米 17 Ultra（`nezha`）和小米 11 Ultra 已报告主要流程可用 | 按设备验证 |
| 自动颜色识别和资源选择 | 已实现，尚未单独实机验收 | 未完成实机验证 |
| 其他 ROM、固件和完整生命周期场景 | 尚未覆盖 | 未验证 |

所有 Hook 都会优先放行非 AZ100 设备和未知签名。ROM 更新后，混淆的 MiLink 类名可能改变，需要重新探测。

## 兼容性记录

以下记录只描述已经提供证据的组合，不代表其他设备必然兼容：

| 设备/系统 | beta.3（32）结果 |
| --- | --- |
| 小米 17 Ultra（代号 `nezha`）/ HyperOS 4 | 三个 Device 页面入口的完整主要流程已通过 |
| 小米 11 Ultra / HyperOS 2 底层、HyperOS 3 上层移植包 | 三个 Device 页面入口的完整主要流程已通过 |
| 其他 HyperOS/Android 版本、AZ100 固件或未列出的设备 | 未验证 |

“完整主要流程”仅指上述 Device 页面入口及其主要操作路径；主题、旋转、字体缩放、后台恢复、多设备和所有异常路径仍需单独验收。已记录的 AZ100 固件基线为 `JNZ1ZE 1.040`。

`nezha` 是小米 17 Ultra 的设备代号，两者为同一台测试设备。该设备早期记录的系统为 Android 17 / `OS4.0.0.17.XPACNXM`；早期入口无响应记录已由后续 beta.3/32 主要流程通过的反馈更新，不代表另一台仍失败的设备。最新反馈未重新核对精确系统构建号。

## 下载、校验与安装

从 [v0.10.0-beta.3 Pre-release](https://github.com/AixunZ/TechnicsPods/releases/tag/v0.10.0-beta.3) 下载：

- `TechnicsPods-0.10.0-beta.3-release.apk`（公开安装包）
- `TechnicsPods-0.10.0-beta.3-source.zip`（源码归档）
- `TechnicsPods-0.10.0-beta.3-mapping.txt`（R8 mapping）
- `SHA256SUMS.txt`、`delivery-manifest.json`（校验与交付清单）

GitHub 自动生成的 source ZIP/TAR 也会出现在页面中。debug、test-signed、unsigned 和 instrumentation APK 仅用于本地测试，不是默认下载包。

已发布 APK 的 SHA-256：

```text
c6257b7105a594d5d1814715bc41594089b0938814b8a37d2c93a3a065051986
```

维护者签名证书 SHA-256：

```text
97598098835abf3b86d18b028333a3c06d575fd7a0f5e14e2a4d438b5b13292d
```

Windows 校验：

```powershell
Get-FileHash -Algorithm SHA256 .\TechnicsPods-0.10.0-beta.3-release.apk
```

有 Android SDK Build Tools 时，可进一步运行：

```powershell
apksigner verify --verbose --print-certs .\TechnicsPods-0.10.0-beta.3-release.apk
```

请将哈希与 `SHA256SUMS.txt` 核对，将证书指纹与 `delivery-manifest.json` 核对。包名、版本、哈希或签名不一致时，不应安装。

安装步骤：

1. 安装已校验的 APK，在 LSPosed 中启用 TechnicsPods。
2. 为模块启用以下作用域：`com.panasonic.technicsaudioconnect`、`com.android.bluetooth`、`com.android.systemui`、`com.android.settings`、`com.miui.home`、`com.milink.service`、`com.xiaomi.bluetooth`。
3. 修改作用域后重启手机，再通过系统蓝牙设置配对并连接 EAH-AZ100。
4. 日常使用可从蓝牙设备详情或 Fusion Device Center 控制模式；官方应用共存请按[公开测试验收清单](docs/PUBLIC_BETA_ACCEPTANCE.zh-CN.md)单独测试。独占通信基线测试前，请先强行停止 Technics Audio Connect。

作用域的唯一来源是 APK 中的 `META-INF/xposed/scope.list`。beta.1/beta.2 使用过本地 debug/test 证书，无法直接覆盖安装到维护者密钥签名的 beta.3；如遇证书不一致，请备份诊断资料后卸载旧候选版再安装 beta.3。后续公开版本必须保持同一维护者密钥，才能正常覆盖升级。

## 已知限制

- 原生 Settings ANC 强度级别尚未确认，不会被模块宣称或写入。
- Fusion 外层蓝色手机主机标记、六个 HyperOS 电池槽的完整充电语义仍待验证。
- 自动颜色、官方应用所有权切换、后台恢复、多设备资源和未知 ROM 尚未完成完整实机验收。
- 当前发布流程没有可用 adb 实机，因此 instrumentation 测试和完整物理验收未执行；公开文档不会将其描述为已通过。

## 文档

- [项目状态与证据（英文）](docs/PROJECT_STATUS.md) / [中文](docs/PROJECT_STATUS.zh-CN.md)
- [beta.3 发布说明（英文）](docs/BETA3_RELEASE.md) / [中文](docs/BETA3_RELEASE.zh-CN.md)
- [公开测试验收（英文）](docs/PUBLIC_BETA_ACCEPTANCE.md) / [中文](docs/PUBLIC_BETA_ACCEPTANCE.zh-CN.md)
- [beta 交付与签名](docs/BETA_DELIVERY.md)
- [安全控制测试](docs/CONTROL_TEST.md)
- [只读诊断](docs/READ_ONLY_DIAGNOSTIC.md)
- [后台状态监视](docs/BACKGROUND_STATE.md)
- [协议与系统桥接](docs/PROTOCOL_SCHEMA.md) · [系统桥接探测](docs/SYSTEM_BRIDGE_PROBE.md)
- [官方颜色与资源证据](docs/OFFICIAL_COLOR_FINDINGS.md) · [当前交接与未解决问题](docs/CURRENT_SESSION_HANDOFF.md)
- [版本历史](CHANGELOG.md)

beta.1/beta.2 历史文档保留开发时间线，不代表公开 beta.3 的当前产物状态。

## 从源码构建

构建环境为 JDK 21、Android SDK Platform 37 和 Build Tools 36.1.0。AGP 9.3.1 使用 Kotlin 2.4.0，Java/Kotlin 字节码目标为 17。配置 `JAVA_HOME` 和 `ANDROID_HOME`（或未跟踪的 `local.properties`）后执行以下命令。SDK、工具链、keystore 和密码均不提交到仓库。

```powershell
$env:GRADLE_USER_HOME = Join-Path $PWD '.gradle-user-home'
$env:ANDROID_USER_HOME = Join-Path $PWD '.android-user-home'
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease :app:assembleDebugAndroidTest --no-daemon
```

发布签名通过 `TECHNICSPODS_KEYSTORE`、`TECHNICSPODS_STORE_PASSWORD`、`TECHNICSPODS_KEY_ALIAS` 和 `TECHNICSPODS_KEY_PASSWORD` 环境变量配置。没有发布密钥时，`assembleRelease` 只会生成不能作为公开分发包的 unsigned APK。打包边界见 [beta 交付与签名](docs/BETA_DELIVERY.md)。

## 开发致谢

截至 2026-10-11，维护者估算本项目开发过程中累计使用了超过 5 亿 GPT tokens。参与的模型包括 GPT-5.6-sol、GPT-6.0-Astra 和 GPT-6.1-sol。这是维护者提供的估算，不代表 OpenAI 背书，也未经独立审计。

## 许可证与来源

TechnicsPods 代码采用 GPL-3.0 许可证。HyperOS 集成思路参考了 HuaweiPods、SonyPods 及其 OppoPods 衍生项目；Technics Audio Connect 代码未被打包。审核过的五种 AZ100 官方外观资源作为第三方资产随项目提供，其来源、签名和资源哈希记录在 `NOTICE` 及 artwork 清单中，不将其重新标记为 GPL 代码。
