# 当前项目交接记录

同步日期：2026-10-11（Asia/Shanghai）。候选版本：`0.10.0-beta.2`，`versionCode 31`。

本记录汇总当前实现、用户反馈与交付边界。用户明确要求暂停继续修复，先保存所有改动与状态。本次仅同步文档、源码快照和交付元数据，未修改应用运行逻辑、版本号或既有 APK。后续继续实现需以用户新的指令为准。

## 最新实机结果

| 项目 | 当前状态 | 证据范围 |
| --- | --- | --- |
| Settings 蓝牙详情页图片 | 用户于 2026-10-11 确认替换成功 | 当前手机上的基本显示；未独立核对最新 Settings 版本或资源 ID |
| Settings / MiLink 两个系统入口控制及状态更新 | 用户此前确认正常 | 已报告的日常控制；不能等同全部取消、超时、断连和所有权场景验收 |
| Settings 点击震动 | 用户此前确认正常 | 基本点击反馈；关闭系统触觉、旧页面等全部边界未单独验收 |
| 状态栏耳机图标 | 用户此前确认正常 | 基本显示；蓝牙重启、多耳机和所有权完整验收仍待完成 |
| MiLink 控制中心耳机图片 | 用户确认替换成功，但有些模糊 | 官方盒图仅 176×130，未锐化、未替换来源 |
| 模块设备页“选择 AZ100” | 仍无响应 | 最后一版实机复测失败 |
| 模块设备页“耳机颜色” | 仍无响应 | 手动配色入口不可用 |
| 模块设备页“设备图片” | 仍无响应 | 用户无法通过该行进入导入/恢复等操作 |
| 自动颜色识别 | 已实现，尚未单独实机确认 | 官方 APK 逆向证据及单元测试；手动入口失败不等同自动识别失败 |
| UI 对齐、主题、旋转、字体缩放 | 已有布局改动，完整验收未完成 | 用户未逐项确认；Compose 测试仅编译 |

上一轮将三个入口描述为“已修复”不准确。单一 `Modifier.clickable` 改动已经保存在源码，但用户复测证明问题仍存在；根因尚未确认，不能继续标为修复完成。本轮遵照用户要求停止修复，不提出新的抓包要求。

最近报告没有给出所安装 APK 的 Debug/Release 类型及 SHA-256，也未重新确认 ROM、宿主包版本或耳机固件。保留已记录的 Xiaomi `nezha` / Android 17 / HyperOS `OS4.0.0.17.XPACNXM` / AZ100 `JNZ1ZE 1.040` 基线，不将反馈扩展为其他版本兼容证明。

## 已保存的实现

| 范围 | 主要文件 | 当前实现 |
| --- | --- | --- |
| 构建与入口 | `app/build.gradle.kts`、`gradle/libs.versions.toml`、`MainActivity.kt` | AGP 9.3.1 / Gradle 9.5 / SDK 37 / JDK 21，Java/Kotlin 字节码 17；Kotlin 2.4.0、Compose BOM 2026.05.01、Miuix 0.9.2 |
| 三栏 UI | `ui/TechnicsPodsAppUi.kt`、`TechnicsPodsUiState.kt`、`TechnicsPodsViewModel.kt` | Home / Device / About，系统/浅色/深色；生命周期状态源、卡片对齐、长名称处理、固定图片槽和信息背景卡；诊断为独立页面 |
| 三个入口尝试 | `TechnicsPodsAppUi.kt`、`MainActivity.kt`、`TechnicsPodsViewModel.kt` | 行点击与弹窗/权限/导入路径保留；实际点击无响应，暂停排查 |
| 官方五色素材 | `assets/az100-art/`、`tools/official-az100-*-selection.xml`、`official-az100-color-index.xml`、`NOTICE` | 黑/蓝/金/银/紫；main/left/right/case 槽与版本化清单；main 当前复用同色盒图，不是独立高清主图 |
| 图片存储与 IPC | `art/Az100ArtStore.java`、`Az100ArtManifest.java`、`Az100ArtImportResult.java`、`Az100ArtPolicy.java`、`Az100ArtProvider.java` | 受限只读 Provider、slot URI、哈希/尺寸/来源清单、缓存失效通知；导入与手动覆盖后端已实现但 UI 入口不可用 |
| 颜色协议与状态 | `protocol/Az100DeviceColor.java`、`Az100ReadFrames.java`、`Az100ReadResponses.java`、`Az100AllDataParser.java`、`diagnostic/Az100ReadOnlyClient.java`、`state/` | 查询 `0x0002`，颜色读取前确认型号 `0x00A0..0x00A3`；未知色不覆盖已确认色，持久化时间戳并通知图片更新 |
| 系统图片 | `hook/Az100ArtHook.java`、`Az100HookDeviceIdentity.java`、`HookEntry.java` | MiLink 已确认资源 ID；Settings AZ100 Fragment 单一保守大图候选；后台解码、身份绑定、宿主回写保护、Provider 刷新和恢复原图 |
| Settings 震动 | `hook/Az100SettingsBridgeHook.java`、`Az100SettingsHapticPolicy.java` | 接受的真实点击请求按 request ID 去重，View/设备/会话保护，尊重系统触觉设置 |
| 测试 | `app/src/test/`、`app/src/androidTest/` | 协议/字段新鲜度/身份/图片清单/触觉策略单元测试；Compose、生命周期、图片缓存和 Provider instrumentation 用例 |

文件路径均相对 `app/src/main/java/dev/technics/pods/`，其余明确标注的根目录路径除外。既有协议事务、连接管理、官方 App 让出和状态栏逻辑没有在本次状态同步中修改。主界面保持无日常模式控制按钮，系统页面承担日常控制；没有新增 Material3 外观切换、EQ、空间音频或其他型号支持。

官方图片来源为 Technics Audio Connect 5.0.1（104）。独立盒图为 176×130，耳机图为 274×284，透明边距与软阴影为源素材属性。资源清单和 NOTICE 记录来源及第三方权利，官方图片不声明为项目 GPL 代码。

## 验证与交付边界

- 上一轮本机结果：124 项单元测试通过，Lint 0 错误 / 60 警告，Debug 与混淆 Release 构建成功；保持原有结果，不将本轮文档更新算作一次新构建。
- Compose 点击用例 `deviceSettingsRowsOpenAndDispatchTheirActions` 已加入且编译成功，但没有在设备或模拟器上执行。其他 instrumentation、主题/旋转/大字体截图也不能声称本机已通过。
- 用户反馈属于部分实机验收。官方 App 前后台/崩溃让出、锁屏/重启恢复、多设备身份、异步图片回写/恢复、未知 ROM 与安装升级的完整清单未完成。
- 提供的 `technics-az100-capture-20261011-003021.jsonl` 只有 20 条 META（蓝牙 14、官方 App 4、模块 2），没有 Settings 或 `TechnicsPods-Art` 图片记录，不能从该文件确认图片资源 ID。原始抓包及截图不进入公开源码快照。
- 当前通用控制握手和颜色查询的型号校验范围应分别描述；官方 APK 的 `0x00A0..0x00A3` 证据不能当作所有控制路径都强制型号校验。Settings `updateAncLevel` 宿主编码仍未确认，当前 beta 不发送其等级写入。

交付目录为 `dist/0.10.0-beta.2`，既有 APK 保留原字节：

| 产物 | SHA-256 / 状态 |
| --- | --- |
| Debug APK | `ea314234ca1257ae814ba600bd576edb26168d6dfb84b4267aa022e632dd5fdf` |
| 测试签名 Release APK | `24142cd432a197446ff05c22563821840cd22f0b56906f1003185ef5b3440004` |
| Debug 日志门控 | Debug APK 早于最后一次 `diagnostics` 分支改动；已确认其字节码会直接调用 `logSettingsImages`，当前源码则检查诊断开关 |
| instrumentation APK | 产物早于新增点击用例，DEX 不含该用例；源用例编译成功不代表测试 APK 已更新 |
| 源码 ZIP | 本轮同步为当前工作区快照；包含全部项目实现与本轮文档，排除工具链/SDK/原始日志/本机配置/密钥/临时 `$out` |
| 旧源码 ZIP | 保存到忽略目录 `.diagnostics/status-sync-20261011/previous-source.zip`；旧快照早于最后一轮 Hook/UI/测试改动 |

源码快照以 Git 基线 `2d34e52b6730bebe027410e89c2ac9dc7778fcc1` 为基础包含未提交改动，本轮没有新增提交。快照是当前源码留存，不声称与每个旧 APK 完全对应。原交付元数据因 `-SkipSource` 留有 `sourceRevision=null` / `sourceDirty=false`，本轮据实更新，并用单独字段保存部分实机反馈；完整验收执行标志仍为 false。源码 ZIP 和元数据变更后更新 SHA256SUMS，APK 哈希不变。维护者正式签名与上传未进行。

## 后续恢复工作时

第一项未解决工作是复现三个行点击失败；应在运行测试和手机复测后再宣称修复。之后验证自动颜色与可用的手动选择/导入、图片身份与生命周期，以及官方 App 共存和后台恢复。暂时不执行这些工作。完整清单见 [PUBLIC_BETA_ACCEPTANCE.md](PUBLIC_BETA_ACCEPTANCE.md)，当前发布说明见 [BETA2_RELEASE.md](BETA2_RELEASE.md)。
