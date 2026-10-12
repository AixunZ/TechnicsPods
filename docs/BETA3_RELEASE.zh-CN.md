# 0.10.0-beta.3 —— 首个公开测试版

版本号：**32**。包名：`dev.technics.pods`。开发者：**AixunZ**。这是通过 GitHub [Pre-release](https://github.com/AixunZ/TechnicsPods/releases/tag/v0.10.0-beta.3) 公开发布的首个测试版本；本地签名、打包和 GitHub 上传均已完成。beta.1 和 beta.2 仍作为内部候选版本历史保留。

## 变更

- 应用版本为 `0.10.0-beta.3`，关于页开发者署名为 `AixunZ`。
- 公开分发包使用维护者长期发布证书 `97598098835abf3b86d18b028333a3c06d575fd7a0f5e14e2a4d438b5b13292d`。发布 APK 的 SHA-256 为 `c6257b7105a594d5d1814715bc41594089b0938814b8a37d2c93a3a065051986`。Debug、测试签名、未签名和 instrumentation APK 仍仅作为本地测试产物。
- 本地公开打包完成：124 个单元测试通过，Lint 为 0 个错误/60 个警告，并验证 `releaseSignatureVerified=true`。Instrumentation 与完整实机验收仍未执行。README 已包含安装、签名/校验和验证、已知 beta 限制以及维护者 GPT 开发致谢；token 说明是维护者估算，并非 OpenAI 背书或独立审计的用量报告。
- beta.2 的传输、系统控制/状态钩子、artwork 和 Device 页面操作均保留。beta.3/32 在小米 17 Ultra / HyperOS 4，以及小米 11 Ultra（HyperOS 2 底层、HyperOS 3 上层移植包）上报告三个 Device 页面入口可用；原 Xiaomi `nezha` 记录仍存在无响应问题。

## 下载和安装

从 GitHub Pre-release 下载 `TechnicsPods-0.10.0-beta.3-release.apk`，同时下载 `SHA256SUMS.txt` 和 `delivery-manifest.json`。安装前，应将 APK SHA-256（`c6257b7105a594d5d1814715bc41594089b0938814b8a37d2c93a3a065051986`）和维护者证书指纹（`97598098835abf3b86d18b028333a3c06d575fd7a0f5e14e2a4d438b5b13292d`）与发布清单核对。公开附件还包括对应源码和崩溃回溯所需的 `TechnicsPods-0.10.0-beta.3-source.zip` 与 `TechnicsPods-0.10.0-beta.3-mapping.txt`。

在 LSPosed 中为 `META-INF/xposed/scope.list` 列出的作用域启用 TechnicsPods；修改作用域后重启。通过 Android 蓝牙设置配对 EAH-AZ100。若要进行独占的读取/控制基线测试，请先强行停止 Technics Audio Connect；官方应用共存属于单独的验收场景。

内部 beta.1/beta.2 候选包使用本地 Debug/测试证书。Android 无法使用不同维护者证书直接升级这些安装。如果需要保留本地诊断数据，请先导出；然后卸载旧候选包，再安装本维护者签名的包。后续公开版本必须继续使用同一维护者密钥，才能正常覆盖升级。

## 已知限制和证据

- **Device 页面结果与主机有关。** beta.3/32 在小米 17 Ultra / HyperOS 4，以及小米 11 Ultra（HyperOS 2 底层、HyperOS 3 上层移植包）上报告“选择 AZ100”“耳机颜色”和“设备图片”主要流程可用；原 Xiaomi `nezha` 记录中的三个入口仍无响应。自动颜色选择已实现，但尚未单独在硬件上验收。
- 当前兼容性证据还包括：`0.10.0-beta.3`/versionCode 32 在小米 17 Ultra / HyperOS 4 上通过完整 Device 页面流程；在小米 11 Ultra（HyperOS 2 底层、HyperOS 3 上层移植包）上也通过完整 Device 页面流程。上述结果只覆盖已报告的设备和主要流程，不代表所有生命周期、主题、旋转或其他 ROM 场景。
- 原记录手机为 Xiaomi `nezha`、Android 17、HyperOS `OS4.0.0.17.XPACNXM`，耳机固件为 `JNZ1ZE 1.040`；该记录中的三个 Device 页面入口仍无响应。其他 ROM、固件和边界场景尚未验证。
- 本发布流程没有可用 adb 实机。因此 instrumentation 执行和完整实机验收均未执行，不能标记为通过。早期用户报告仅是 beta.2 的局部硬件证据，不构成 beta.3 的完整验收。
- 早期报告确认两个系统入口的模式控制/状态更新、Settings 触感反馈、状态栏耳机图标以及 MiLink/Settings artwork 替换。Settings 原生 ANC 强度编码尚未验证，暂不支持。MiLink artwork 可能偏软，因为官方耳机盒源图仅为 176×130 像素。Fusion 卡片外层蓝色手机宿主标记、多设备行为、生命周期/恢复、官方应用所有权和其他 ROM 仍是验收目标。

完整验收条件见[公开测试验收清单](PUBLIC_BETA_ACCEPTANCE.zh-CN.md)（英文版：[Public beta acceptance](PUBLIC_BETA_ACCEPTANCE.md)）。本地生成的检查结果、哈希、证书身份和源码边界见[beta 交付说明](BETA_DELIVERY.md)及发布包中的 `delivery-manifest.json`。
