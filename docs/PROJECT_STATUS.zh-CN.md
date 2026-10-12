# TechnicsPods 项目状态

最后更新：2026-10-12（公开 beta.3；更正设备身份及开发者表述）

## Beta.3 公开测试版

候选版本：`0.10.0-beta.3` / `versionCode 32`。这是首个公开测试版，已在 GitHub 以 **Pre-release** 发布。开发者为 **AixunZ**。公开包集合已生成并上传，包括维护者签名的 Release APK、匹配的源码归档和 mapping、`SHA256SUMS.txt` 以及 `delivery-manifest.json`。已验证的发布证书 SHA-256 为 `97598098835abf3b86d18b028333a3c06d575fd7a0f5e14e2a4d438b5b13292d`，发布 APK SHA-256 为 `c6257b7105a594d5d1814715bc41594089b0938814b8a37d2c93a3a065051986`。私有签名材料保留在仓库之外。

三个 Device 页面行 **选择 AZ100**、**耳机颜色** 和 **设备图片** 已在 beta.3/32 的小米 17 Ultra（代号 `nezha`）/ HyperOS 4，以及小米 11 Ultra（HyperOS 2 底层、HyperOS 3 上层移植包）上报告主要操作流程通过。`nezha` 与小米 17 Ultra 是同一台测试设备，其早期入口无响应记录已由最新复测结果取代，不再列为当前限制。历史 Android 17 / HyperOS `OS4.0.0.17.XPACNXM` 信息仅表示当时采集的系统基线，最新复测未重新采集精确构建号。本次发布流程没有可用 adb 实机，因此不宣称 instrumentation 执行或完整跨 ROM 硬件验收已经通过。

对于公开项目页面，维护者估算截至 2026-10-11 开发过程中使用了超过 5 亿 GPT tokens。维护者列出的模型包括 GPT-5.6-sol、GPT-6.0-Astra 和 GPT-6.1-sol。这是维护者提供的估算，不代表 OpenAI 背书，也未经独立审计。具体限制见 [beta.3 发布说明](BETA3_RELEASE.md)，产物边界见 [beta 交付](BETA_DELIVERY.md)。

## Beta.2 更新

候选版本：`0.10.0-beta.2` / versionCode 31。2026-10-11，用户确认手机上的 Settings 蓝牙详情 artwork 已替换。更早的报告确认两个系统入口的控制/状态更新、Settings 触感反馈、状态栏图标和 MiLink artwork 替换。Beta.2 包含真实的 Miuix Compose Home / Device / About 标签页、布局对齐调整、已接受请求的触感反馈和五种审阅过的官方 artwork 外观。Java 传输与系统控制语义仍是回归基线。

Device 页面 **选择 AZ100**、**耳机颜色** 和 **设备图片** 仍不能响应点击。上一次显式点击处理没有通过用户复测。设备选择、手动换色和图片导入仍无法通过这些行访问。自动颜色检测已经实现并通过单元测试，但尚未在手机上单独验收。用户要求暂时停止继续修复；本阶段只同步记录和源码快照。

已索引用户提供的 Audio Connect 5.0.1 (104) APK，并打包黑、蓝、金、银、紫色 artwork。Settings 使用绑定身份的单个大标题候选项并安全回退；成功渲染不代表已确定精确资源 ID，或已覆盖所有页面复用/回写/恢复场景。MiLink 替换已由用户确认，但放大 176×130 的官方耳机盒图片时会偏软；该来源和缩放方式保留。

本地证据仍为 124 个单元测试通过、Lint 0 个错误/60 个警告。Compose/集成测试已编译，但未通过 adb 执行。最新手机报告属于局部用户验收；没有提供新的 ROM/包版本、测试 APK 变体或哈希。见[当前交接](CURRENT_SESSION_HANDOFF.md)、[beta.2 发布说明](BETA2_RELEASE.md)和[验收结果](PUBLIC_BETA_ACCEPTANCE.md)。

## 第一阶段更新

用户于 2026-09-27 确认 Fusion Device Center 和蓝牙详情可见，以及关闭/ANC/环境音切换正常。下面关于尚未证明原生按钮写入的旧说法属于 8 月调查，不代表当前基线。

历史 beta.1：`0.10.0-beta.1` / versionCode 30。本阶段将系统控制请求集中到 Provider 运行时、能力报告和异步快照读取中。之后的用户报告确认两个系统控制/状态路径和状态栏图标；官方应用所有权及后台恢复仍需专门验收。见[公开测试验收](PUBLIC_BETA_ACCEPTANCE.md)。

下面列出的设备/ROM/固件仍是记录中的硬件基线。历史用户确认覆盖 Fusion 内页和关闭/ANC/环境音模式切换。当前 beta 源码已实现连接所有权、官方应用会话通告以及设备/设置 UI；这些功能的实机验收仍待完成。Settings 原生 ANC 强度级别被有意设为不可用，因为宿主编码尚未验证。

## 记录基线和历史证据

- 当前候选：`0.10.0-beta.3` / `versionCode 32`；历史 beta.2 code 31、beta.1 code 30
- 原始测试设备：小米 17 Ultra（代号 `nezha`）；当时采集的系统基线为 Android 17、HyperOS `OS4.0.0.17.XPACNXM`，不代表最新复测重新核验了这些版本
- 耳机：Technics EAH-AZ100，固件 `JNZ1ZE 1.040`
- 抓包所用官方应用：Technics Audio Connect 4.4.0
- 历史路由日志：`LSPosed_20260831_015717.zip`；最新渲染反馈为用户 2026-10-11 报告

## 兼容性证据

| 主机 | 证据 | 范围 |
|---|---|---|
| 小米 17 Ultra（`nezha`）/ HyperOS 4 | beta.3/32 实机完整 Device 页面主要流程通过；同一设备早期失败记录已被最新复测取代 | 设备页流程；不扩展到未覆盖生命周期、主题、旋转或其他 ROM；历史精确系统版本未重新采集 |
| 小米 11 Ultra（HyperOS 2 底层、HyperOS 3 上层移植包） | beta.3/32 实机完整 Device 页面主要流程通过 | 该设备和移植包的设备页流程；其他变体未知 |
| `com.milink.service` `18.1.2.2.2608192125` / code `180010202` | 历史宿主清单；之后用户报告控制/状态与 artwork | 记录中的宿主版本；最新包版本未重新采集 |
| `com.android.settings` `17` / code `37` | 历史宿主清单；用户控制/触感报告及 2026-10-11 图片成功 | 记录中的宿主版本；最新包版本未重新采集 |
| `com.xiaomi.bluetooth` `17` / code `37` | `success_half_modules.log`、Surface 进程就绪 | 记录中的运行时宿主版本；beta 尚未测试 |
| `com.android.systemui` `17.03.260226.r` / code `202602260` | 历史清单；用户确认状态栏图标 | 已确认基本渲染；多耳机/启动场景尚未完整验收 |
| `com.android.bluetooth`、`com.miui.home` | 当前证据未确定精确包版本 | 不作包版本兼容性声明 |
| 其他 Android/HyperOS 构建 | 仓库中没有证据 | 未知；预期保护性放行/安全 no-op |

## 已正常工作的部分

### 传输和协议

- 独立 RFCOMM 会话使用 UUID `00000000-0000-0000-0099-aabbccddeeff`。
- 已实现 RACE 流重组、响应匹配、超时、取消和 socket 清理。
- 已确认读取：型号 ID、左右耳机电量、充电盒电量、外部控制模式和记忆级别、环境音子模式、自适应 ANC、固件和中继可用性。
- 外部控制编码已确认：模式 `0=关闭`、`1=降噪`、`2=环境音`；ANC 级别是直接的 `1..100` 字节。
- 自适应 ANC 和环境音子模式构建器/解析器已类型化并通过 fixture 测试。
- 断连处理保留最近值并单独标记新鲜度，不伪造零电量。

### 安全控制和状态

- 控制客户端执行“读取当前状态 → 类型化写入 → ACK → 强制回读”。
- 写入后出错时执行尽力恢复。
- 后台监控明确由前台服务运行并限速。
- `Az100StateCache`、`Az100StateRepository` 和受限 Provider 在进程间传递部分状态。
- 诊断页提供只读采样和受保护的模式/级别事务；复用生产事务，并移除单独的固定 75 往返客户端。

### HyperOS 系统集成

- `com.android.bluetooth` 已接收并存储 AZ100 电量值；不需要注入电量。
- `Az100HyperOsBridgeHook` 将 AZ100 身份和缓存的电量/ANC 状态映射到已验证的 MiLink/HyperOS 界面。
- `Az100SettingsBridgeHook` 为 AZ100 修补旧版 Settings 耳机页中的身份、连接检查、电量槽和只读 ANC/状态渲染。
- Fusion Device Center 路由已在硬件上验证。`Az100FusionDeviceRouteHook`：
  - 为 AZ100 修补空耳机服务查找（`protocol 393216`）；
  - 识别标记为 `SOUND_BLUETOOTH_CONTROL` 的 ROM `wc.a.q()` 显示配置；
  - 在 `MainCardView.k()` 中替换普通蓝牙显示配置；
  - 等待异步 `fc.t` 控制器，并在卡片创建后重试 `fc.t.b0()`；
  - 进入 `com.miui.circulateplus.world.headset.HeadsetDetailFragment`。
- 状态栏回调发现对记录中的 A2DP 签名是精确的，基本渲染已由用户确认。profile 启动协调和多耳机行为仍待专门验收。
- MiLink 使用已验证的图片 ID；Settings 使用绑定身份的保守大标题选择器。两处图片替换均在原始记录手机上由用户确认，仍待更多生命周期/切换设备场景。
- 协议在型号确认后读取 AZ100 颜色并保留自动外观选择。手动覆盖和图片来源路径已在上述两台 beta.3/32 主机上报告可用；自动颜色尚未单独验收。

最终路由日志包含：

```text
Found named system Headset panel;factory=q;label=SOUND_BLUETOOTH_CONTROL
Routed AZ100 MainCardView to Headset display config
AZ100 Headset controller call=fc.t:b0()
Routed AZ100 MainCardView request to HeadsetContentController
```

## 尚未正常工作的部分

- Fusion Device Center 外层 AZ100 卡片不显示蓝色手机宿主标记。这是独立于内层面板路由的设备宿主/状态模型。
- 两个当前系统入口的控制/状态路径已由用户确认。待处理/错误显示、官方所有权以及取消/生命周期边界仍需专门测试。
- MiLink artwork 仍偏软；官方耳机盒素材只有 176×130。未加入锐化或其他来源。
- 充电标志和离线/过期状态需要更多 ROM 专项验证。
- 其他 HyperOS 构建、Android 版本和 AZ100 固件的兼容性未知。
- Settings 原生 ANC 强度级别编码尚未验证；beta 不提供臆造的转换或写入路径。此限制取代 v29 的级别转发行为，避免把宿主滑块值当作已验证的 AZ100 百分比。
- 维护者发布签名、本地公开打包和 GitHub Pre-release 上传均已完成。可选签名环境变量和未签名/minified 测试产物仍可用于复现。
- 官方 APK 检查确定颜色读取门控使用 AZ100 型号 ID `0x00A0..0x00A3`。这不代表每个通用控制握手路径都执行严格型号验证；见 `OFFICIAL_COLOR_FINDINGS.md` 与 `CONTROL_TEST.md`。

## Fusion 路由调查摘要

问题通过分阶段 LSPosed 日志逐步缩小，而非盲目猜测方法：

1. 更早的构建证明 AZ100 服务查找和 `fc.t` 控制器捕获有效，但 Fusion 仍膨胀 `BluetoothCardView`。
2. v24/v25 显示 `MainCardView.k()` 已被钩住，但 `findHeadsetDisplay()` 返回 `null`。
3. 记录 ROM 的显示工厂标签。耳机配置为 `SOUND_BLUETOOTH_CONTROL`，而初始的 `headset/earphone/earbud` 资源匹配器无法识别它。
4. v26 增加精确标签并成功将 `MainCardView` 路由到系统耳机显示配置。
5. 下一份日志显示时序间隙：卡片创建早于 `fc.t` 捕获。v27 增加有界延迟重试 `fc.t.b0()`。
6. 最终日志确认 `fc.t.b0()` 和 `HeadsetDetailFragment.onCreate()`。Fusion 内层入口现在可进入控制页面。

该修复有意保留非 AZ100 设备和未知 ROM 签名的原始行为。未重新确认控制器初始化时序前，不要移除延迟重试。

## 下一步验收顺序

公开 beta 已发布。后续维护应保留当前用户确认的系统路径并按以下顺序处理：

1. 在小米 17 Ultra（`nezha`）和小米 11 Ultra 上补充精确构建信息及可复现证据，并执行 Compose 点击用例；不把两台设备已报告的主要流程结果扩展为完整回归通过。
2. 分别补充验证自动颜色、手动覆盖/导入边界，以及 Settings/MiLink 设备切换、异步宿主写入和原图恢复。
3. 使用多耳机同时连接确认自动管理、官方应用所有权、取消/重连和状态栏所有权。
4. 完成 `PUBLIC_BETA_ACCEPTANCE.md` 中剩余的主题/字号/旋转/后台/未知 ROM 场景。
5. 确定 Settings 原生级别编码后再启用其写入；维护者签名和公开上传已是独立完成项。

## 安全规则

- 绝不添加任意厂商 HEX 输入、固件更新、重置或配对命令。
- 每次实时写入都保留官方应用所有权和独占会话检查。
- 将原始抓包 ZIP 视为敏感数据；派生 fixture 必须删除姓名、MAC 地址和无关 payload。
- 未知返回类型、非 AZ100 设备和过期状态必须放行到系统实现。

## 构建验证

上一轮 beta.2 会话完成了以下本地检查（交付清单时间戳为 UTC；最终包于 2026-10-11 在亚洲/上海创建）：

```text
:app:testDebugUnitTest
:app:lintDebug
:app:assembleDebug
:app:assembleRelease
:app:compileDebugAndroidTestKotlin
```

APK：`app/build/outputs/apk/debug/app-debug.apk`

124 个单元测试通过；Lint 为 0 个错误、60 个警告。Debug 和 minified Release 已构建并检查本地测试签名。Instrumentation 用例（包括新增的 Device 行点击用例）已编译但没有通过 adb 运行；打包的测试 APK 早于新增点击用例。用户报告了小米 17 Ultra（`nezha`）和小米 11 Ultra 两台 beta.3/32 主机的设备特定实机结果，同一台 `nezha` 设备的早期入口失败记录已由最新复测取代；不宣称完整跨 ROM 清单或 adb 支持的验收通过。本次状态同步不重新构建或替换 APK。当前产物/源码边界见 `BETA_DELIVERY.md`。
