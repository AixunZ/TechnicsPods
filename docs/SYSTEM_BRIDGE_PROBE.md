# System and HyperOS surface probes

## Current status (2026-10-11, beta.2)

The user has confirmed both system entries' control/state updates, Settings haptics and the status-bar icon. MiLink artwork replacement was confirmed earlier; Settings artwork replacement was confirmed on 2026-10-11. Settings uses the AZ100 fragment's identity-bound conservative single-header selector, rather than a verified per-ROM resource-ID list. Basic rendering success does not establish every device-switch, async write-back or restore case.

The Device-page rows **选择 AZ100**, **耳机颜色** and **设备图片** still do not respond. Further repair is paused at the user's request. The supplied `technics-az100-capture-20261011-003021.jsonl` contains 20 META records and no Settings or `TechnicsPods-Art` image records; it cannot identify the Settings image resource. Future art diagnostics use LSPosed logs after enabling diagnostic probes, but no further capture is requested during this pause. See [current handoff](CURRENT_SESSION_HANDOFF.md).

The project first added a read-only `com.android.bluetooth` inventory and then added read-only HyperOS surface discovery. These probes do not change return values or send Bluetooth commands.

## Collect the inventory

1. Install the APK and update the LSPosed module.
2. Enable the existing Technics Audio Connect and `com.android.bluetooth` scopes.
3. For HyperOS surface discovery, also enable `com.android.systemui`, `com.android.settings`, `com.miui.home`, `com.milink.service`, and `com.xiaomi.bluetooth`.
4. Reboot once so the target processes load the new scopes.
5. Open the system Bluetooth details page or device center once, then export the complete LSPosed log ZIP.

The Bluetooth probe records common AOSP/OEM classes and method names. On the tested ROM it found `RemoteDevices$DeviceProperties.getBatteryLevel()` and `setBatteryLevelFromHfp(int)`, and runtime logs showed the system already stores the AZ100 value. Therefore no battery injection is required.

The HyperOS probe records package versions and class names loaded by SystemUI, Settings, and the launcher when their names contain Bluetooth, battery, headset, earbud, device-center, media-output, accessory, nearby, or audio tokens. The current build also emits declared constructors and methods for the candidate MiLink, headset-runtime, Xiaomi Bluetooth, and Device Center classes, prefixed with ClassName :: ctor or ClassName :: method. These signatures are the input for the next protective hook implementation. It does not include Bluetooth addresses or vendor payloads.

If no HyperOS entries appear, the target process was not restarted or the relevant surface uses another package. Do not enable any guessed control hook until the inventory identifies a real method or service surface.

## What to send after collection

Send the complete LSPosed log ZIP, or the lines containing TechnicsPods-HyperOsProbe from the new run. Keep these groups intact:

- Surface process ready package=... lines, including the package version.
- ... :: ctor ... and ... :: method ... lines for HeadsetLocalService, HeadsetCirculateSessionService, DeviceControlManager, DeviceInfo, BatteryInfo, BluetoothHeadsetService, MiuiFastConnectService, and the Device Center classes.
- Any Failed to inspect ... or hook-install warnings.

Do not manually redact method names or parameter types; those are required to select an exact overload. The probe does not intentionally log Bluetooth names, MAC addresses, or raw vendor payloads.
## 当前桥接实现（0.10.0-beta.1）

探针已经转为保护性桥接。`com.milink.service`、`com.xiaomi.bluetooth`、SystemUI、Settings 和桌面进程会安装 AZ100 Hook，只有方法参数中明确包含名称匹配 `EAH-AZ100` 的 `BluetoothDevice` 时才改写结果；其它耳机全部继续执行原方法。

- `ProfileContext`、`ThirdPartyHeadsetStrategy`、`AncBatteryController` 的设备 ID 返回稳定的 `technics_eah_az100_<address>` 标识。
- 电量返回 HyperOS 六槽 `List<Integer>`：充电盒、左耳、右耳、盒充电、左充电、右充电。未知电量返回 `-1`，当前未读取充电状态时三个充电标志返回 `0`。
- `getAncState` 返回 AZ100 的关闭、降噪或环境声状态；没有新鲜后台快照时保持原系统结果。
- `setAncState`/`setAncStateBlock` 只接受 0、1、2，并调用已有安全控制客户端完成 ACK 和强制回读；失败返回错误值，不吞掉异常。

跨进程状态通过 `dev.technics.pods.state` Provider 传递。读取、控制、官方所有权和图片分别验证调用包及真实 UID。系统 Hook 异步读取快照，控制转发到唯一 Runtime；无新鲜快照或官方 App 占用时保持未知/缓存状态。

### 首次验证顺序

1. 安装新 APK，LSPosed 中保留 Technics Audio Connect、`com.android.bluetooth`、`com.milink.service`、`com.xiaomi.bluetooth`、SystemUI、Settings、桌面作用域。
2. 重启设备，打开模块应用并启动后台状态监测，等待第一次读取成功。
3. 打开系统蓝牙详情或设备中心，确认 AZ100 条目显示三组电量和当前 ANC。
4. 过滤 `TechnicsPods-HyperOsBridge` 日志，确认出现 `bridge ready`，且没有 `interception failed`。
5. 先在系统页切换“关闭/降噪/环境声”中的一个选项，确认耳机实际变化，再回到模块应用执行一次读取，检查状态回读一致。
6. 若系统页崩溃或控制失败，立即停止模块中的后台监测并导出完整 LSPosed 日志；不要重复快速点击控制，便于区分超时、权限或 ROM 方法差异。

## Settings 专用桥接（0.10.0-beta.1）

最新真机日志确认设备中心链路已经能够显示左耳、右耳、充电盒电量和 ANC
状态，但 com.android.settings 使用另一套旧式 MiuiHeadsetActivity，
通用 MiLink 桥接在该进程中命中了 0 个方法。

本版新增只在 com.android.settings 安装的保护性桥接：

- 在 MiuiHeadsetActivity.onCreate 前为明确匹配 EAH-AZ100 的设备补齐
  MIUI_HEADSET_SUPPORT、DEVICE_ID 和缺失的 COME_FROM。
- 动态遍历 HeadsetIDConstants 与 Binder Proxy 的实际方法签名，不再假定
  getDeviceInfo 或 isSupportAudioSwitch 是零参数方法。
- isBleMmaConnect 同时验证真实 BluetoothDevice 和 120 秒内的新鲜 Provider
  状态，解决页面把已连接 AZ100 判为 MMA 断开的情况。
- 向 MiuiHeadsetFragment 注入 Settings 自己使用的三槽电量、ANC level code、
  16 槽状态 CSV 和固件显示值；收到只针对小米 MMA 的连接失败回调时恢复 AZ100
  的新鲜状态。
- 所有未知返回类型、非 AZ100 设备、离线或过期状态都继续执行系统原方法。

Settings 的 `updateAncMode(int, boolean)` 只转发真实用户点击的模式请求；
内部 UI 回放继续执行原方法。`updateAncLevel` 的宿主等级编码尚未确认，
当前 beta 不转发等级写入。这一限制优先于历史 0.9.15/v29 的等级转发记录。
模式写入复用现有 ACK、强制回读和失败处理客户端，确认成功后
通过 Provider 更新共享状态并刷新当前 Fragment。日志曾确认故障版本记录了
`module write bridge disabled`；该路径已在 0.9.15 修复，并已在记录的实机上验证成功。

## Current verified Fusion route (v0.9.13)

The read-only probe has now been followed by a protective Fusion route in
`Az100FusionDeviceRouteHook`. On the test ROM, Fusion Device Center builds
`MainCardView` in `com.milink.service:ui` before the headset controller is ready.
The hook therefore performs these operations only for a device matching
`EAH-AZ100`:

1. Supplies the cached AZ100 headset service for the empty protocol `393216` lookup.
2. Replaces the ordinary display config passed to `MainCardView.k()` with the
   `wc.a.q()` config labeled `SOUND_BLUETOOTH_CONTROL`.
3. After the card is created, retries the native `fc.t.b0()` click route for a
   bounded period until `HeadsetContentController` has been captured.

The final hardware log confirms `fc.t.b0()` and
`com.miui.circulateplus.world.headset.HeadsetDetailFragment.onCreate()`. This
means the inner Fusion entry reaches the stock headset control page. The outer
blue phone-host marker remains a separate, unresolved Device Center status model.

This historical route log alone does not prove stock-button writes. Later user
reports confirm mode forwarding and live state updates in both current system
entries through the guarded central control client. Native Settings ANC-strength
encoding and broader lifecycle/error cases remain unverified.
