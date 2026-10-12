# 公开测试验收清单

本清单描述已发布的 `0.10.0-beta.3` / versionCode 32 首个公开测试版本。最新 beta.3/32 反馈覆盖明确列出的 Device 页面路径；以下清单仍是完整验收目标，不能据此宣称跨 ROM、生命周期或 instrumentation 已全部通过。

## 已记录的验收状态 —— 2026-10-11

| 项目 | 证据和当前状态 |
| --- | --- |
| Settings 与 MiLink 控制/状态更新 | 用户报告两个系统入口均正常 |
| Settings 触感反馈 | 用户报告正常；拒绝、重复、系统触感关闭和生命周期场景未单独报告 |
| 状态栏耳机图标 | 用户报告正常；蓝牙重启和多耳机场景未单独报告 |
| MiLink / 控制中心 artwork | 用户确认已替换；显示图像偏软，尚未锐化 |
| Settings 蓝牙详情 artwork | 用户在最近一次测试中确认已替换 |
| Device 页面“选择 AZ100”“耳机颜色”“设备图片” | beta.3/32 在小米 17 Ultra / HyperOS 4 和小米 11 Ultra（HyperOS 2 底层、HyperOS 3 上层移植包）上报告三条路径可用；原 `nezha` 记录仍无响应 |
| 自动识别 AZ100 颜色 | 已实现，但用户尚未单独验收 |
| 手动设备/颜色/图片选择 | 在上述两台 beta.3/32 设备上报告可用；原 `nezha` 记录无法通过入口验收 |
| 小米 17 Ultra / HyperOS 4 的 Device 页面 | beta.3/32 已通过完整 Device 页面主要流程；不扩展到未覆盖的生命周期和其他 ROM |
| 小米 11 Ultra（HyperOS 2 底层、HyperOS 3 上层移植包）的 Device 页面 | beta.3/32 已通过完整 Device 页面主要流程；仅代表该设备和移植包 |
| 原 Xiaomi `nezha` / Android 17 / HyperOS `OS4.0.0.17.XPACNXM` | 三个 Device 页面入口仍无响应，保留为已知失败记录 |
| 本地自动化验证 | 现有构建：124 个单元测试通过；Lint 0 个错误/60 个警告；Debug 和 minified Release 已构建 |
| Compose / artwork instrumentation | 当前用例仅编译；保留的测试 APK 早于最新 Device 行点击用例；没有 adb 执行结果 |

以上是特定设备上的用户结果，不是跨 ROM 的完整验收。两台 beta.3/32 设备覆盖上述三条 Device 页面路径，原 `nezha` 记录仍是已知失败。编译成功的 Compose 点击测试不能作为设备上点击可用的证据。此次发布流程没有可用 adb 实机；instrumentation 执行和完整实机验收必须记录为未执行，而不是通过。

## 已记录的兼容性基线

- 已验证：小米 17 Ultra / HyperOS 4，beta.3/32 完整 Device 页面主要流程
- 已验证：小米 11 Ultra，HyperOS 2 底层、HyperOS 3 上层移植包，beta.3/32 完整 Device 页面主要流程
- 原始记录：Xiaomi `nezha`
- 原始记录：Android 17
- 原始记录：HyperOS `OS4.0.0.17.XPACNXM`
- Technics Audio Connect 4.4.0
- EAH-AZ100 固件 `JNZ1ZE 1.040`

其他主机和固件版本未知。若找不到精确签名，保护性钩子应直接放行或报告能力不可用。

## beta.3 其他设备结果

| 主机 | beta.3/32 结果 | 边界 |
| --- | --- | --- |
| 小米 17 Ultra / HyperOS 4 | 三个 Device 页面入口主要流程已报告可用 | 未记录精确构建号，完整生命周期仍未覆盖 |
| 小米 11 Ultra / HyperOS 2 底层、HyperOS 3 上层移植包 | 三个 Device 页面入口主要流程已报告可用 | 未记录精确构建号，完整生命周期仍未覆盖 |
| Xiaomi `nezha` / Android 17 / HyperOS `OS4.0.0.17.XPACNXM` | 三个 Device 页面入口在记录中仍无响应 | 作为该主机的已知限制保留 |

## 安装前

1. 从 GitHub Pre-release 下载维护者签名的 `TechnicsPods-0.10.0-beta.3-release.apk` 和 `SHA256SUMS.txt`。安装到测试设备前核对校验和及维护者证书指纹。
2. 为 `META-INF/xposed/scope.list` 中的包启用模块：`com.android.bluetooth`、SystemUI、Settings、`com.miui.home`、`com.milink.service`、`com.xiaomi.bluetooth` 以及 Technics Audio Connect。
3. 修改 LSPosed 作用域后重启。如果设备安装了 beta.1/beta.2 Debug 或测试签名候选包，Android 无法使用不同维护者证书覆盖升级；先导出需要的诊断数据，卸载旧包，再安装 beta.3。Debug/测试构建仅用于本地检查。

未签名 Release APK 不是可安装的签名包。若构建时没有配置密钥，维护者必须另行签名、验证证书/哈希并单独上传。Debug/测试密钥不能证明维护者发布签名有效。

## 验收检查

1. 配对并连接 EAH-AZ100。确认状态栏钩子报告精确的 A2DP 回调，或明确报告能力不可用。
2. 启用图标时，确认 AZ100 连接显示 `wireless_headset`；无关耳机不得冒用或隐藏模块拥有的图标。
3. 重启蓝牙和 SystemUI。确认启动时 profile 快照不完整时，启动配置协调不会隐藏图标。
4. 打开 Fusion Device Center 和蓝牙详情。确认 AZ100 身份、电量和模式渲染；非 AZ100 设备应安全放行。
5. 操作 Settings 模式控制。确认请求由中央 Provider 接受，已确认的结果异步刷新。
6. 在宿主编码独立验证前，将 Settings ANC 强度级别视为不支持。
7. 确认能力记录报告功能、已安装/不可用状态和精确钩子数量，不宣称硬件渲染成功。
8. 确认 artwork 替换仅限已验证的 MiLink 资源 ID，或单个保守的 Settings 大标题候选项，并且仅针对选定 AZ100。两个入口的基本替换已由用户确认；beta 包含 Audio Connect 5.0.1 中审阅过的黑、蓝、金、银、紫映射。来源/颜色/插槽、自动协议选择、通知、宿主异步覆盖和原图恢复仍需明确验收。两台 beta.3/32 设备的报告包含图片来源入口访问，原 `nezha` 记录仍无法访问。若 Settings 布局含义不明确或未经测量，应保持宿主图像不变。
9. 启用自动管理，然后断开/重连并开关蓝牙。确认活动事务可取消、缓存值保留、采样可恢复，且不会与并发 RFCOMM 会话冲突。
10. 模块读取或控制期间打开 Technics Audio Connect。确认官方应用取得所有权时模块取消/让出，拒绝新写入，并在官方 socket/session 结束后才释放。
11. 官方会话恢复不明确时，确认模块继续让出控制权。重启官方应用重新宣布会话，或完全关闭/开启蓝牙；确认适配器关闭后可释放恢复锁，重连必须先读取新状态再写入。
12. 分别从应用和通知停止自动管理，然后关闭官方应用。确认模块不再自主读取；新的显式请求仍会先读取最新状态再控制。
13. 测试真实的 Miuix Home / Device / About 导航、系统浅色/深色主题、窄屏和字号 2。三条 Device 页面入口已在兼容性表中的两台 beta.3/32 主机上报告可用，但原 `nezha` 记录仍无响应。权限、诊断和图片选择期间旋转屏幕；选中标签、结果和图片应保留。About 设置应可通过滚动访问，页面不得创建额外连接或观察者。缓慢的文档提供程序不得冻结图片导入或日志导出界面。
14. 在旧系统详情页打开时切换选定 AZ100 设备。旧页面请求必须被拒绝，一个设备的电量/模式/artwork 不得显示为另一个设备的当前数据。
15. 测试锁屏连接、重启、包更新、进程回收和蓝牙权限撤销。确认能恢复，或显示可操作的权限/后台限制状态，且宿主不崩溃。
16. 在测试设备上安装维护者签名的 beta.3 Release，验证未来使用同一密钥的升级。Debug 和测试签名变体仅用于本地检查。对未知 ROM/宿主签名执行安全放行测试；签名发布包不等于完成硬件验收。
17. 在 Settings 中点击关闭/ANC/环境音：每个被接受的真实请求应在硬件完成前产生一次系统触感。基础反馈已由用户确认。拒绝/忙碌/无效请求、内部重放、旧视图、断连和设备切换不得产生反馈；这些边界情况尚未报告。关闭系统触感后确认控制仍可用且不震动。MiLink 保留原有反馈行为。

## 应保留的证据

记录设备/构建字符串、模块 APK SHA-256、签名身份、LSPosed 日志和 Provider 能力记录。不得发布蓝牙地址、原始厂商 payload 或未脱敏的抓包归档。
