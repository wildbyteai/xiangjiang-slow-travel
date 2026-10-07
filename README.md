# 湘江慢游 / Xiangjiang Slow Travel

长沙两日慢游导游与本机旅行日记的源码：岳麓书院、橘子洲、湘江，结合正餐与休息，一天一处主景点。

本仓库是独立的 **公开示例版**。日期采用历史合成样例，车次和时刻为演示字段，住宿只标地铁站附近区域；不包含任何真实订单、家庭地址、用户照片、密钥或私人旅行资料。请勿按示例购买车票或预约。

## 目录与入口

| 目录 | 内容 |
| --- | --- |
| `android-app/` | Android 原生 App（Java），定位、拍照、选照片、旅行记录、日记与模型设置 |
| `mobile-guide/site/` | 手机网页导游手写源码、样式、离线缓存与主屏幕入口 |
| `maps/` | 地图模板、公开 OSM 地理数据、合成行程数据及 D3 |
| `legacy-h5/` | 地图优先的历史 H5 界面源码，使用相同示例数据；仅参考，不作为当前执行入口 |
| `scripts/` | 公开版检查与手机网页交互模型测试 |

生成文件不入库。`node build.cjs` 从以上源码生成地图、网页行程数据及 Android assets，不访问网络或依赖作者本机目录。

## 构建与测试

准备 Node.js 20+、JDK 17、Android SDK（platform 35 / build-tools 35.0.0），通过 `ANDROID_HOME` 或本机 `android-app/local.properties` 指定 SDK。需要网络下载公开依赖；不要提交 `local.properties`。

```sh
node build.cjs
node scripts/verify.cjs
node scripts/guide-interaction-test.cjs
cd android-app
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Gradle Wrapper 固定 8.9，AGP 8.7.3，minSdk 26、targetSdk 34、compileSdk 35。调试 APK 在 `android-app/app/build/outputs/apk/debug/`，不提交到本仓库。

网页入口为 `mobile-guide/site/index.html`，历史页面为 `legacy-h5/index.html`。可以本地预览；Service Worker 需要安全上下文，手机离线使用需用户自行选择 HTTPS 托管并验证。本次源码发布不启用 Pages、自动部署或安装包发布。

公开示例 App 的 applicationId 为 `com.bytewatcher.xiangjiang.demo`，与私人版隔离，**不能作为私人版的升级包**，不读取或迁移私人版记录。

## App 的数据与权限边界

- 记录默认在本机私有目录，包含地点、文字、照片和用户确认的位置。计划不自动变成实际经历；地图记录点不代表连续 GPS 轨迹。
- 定位只在用户操作时获取；不实现后台定位、账户、云同步或自动上传。
- 使用系统相机、Android 13+ Photo Picker（旧版文档选择器）；照片 EXIF 时间/GPS 只提供候选，由用户确认采用。
- 日记可编辑、选封面、HTML 导出、ZIP 备份与合并恢复；卸载会失去本机记录，先备份。导出可能包含私人照片和文字，分享前自行检查。
- 可选识图润色：在原生配置页填完整 HTTPS `/chat/completions` 地址、支持图片输入的模型名与个人 API key。仅支持兼容 Chat Completions 协议。
- 保存配置不联网。密钥由 Android Keystore / AES-GCM 保存在 noBackup，旧密钥不回显、不进日记导出；配置页限制截屏。
- 每次润色前确认选中素材，最多三张照片，发送前 JPEG 重编码去 EXIF；精确坐标默认不发送。模型可能产生费用或不实内容，保留原文，候选稿由用户确认采用。
- 本地地图 WebView 禁外部网络、file/content 访问和通用 JavaScript bridge；模型请求从原生代码显式发送。

## 修改为自己的旅程

首先修改 `maps/itinerary-data.json` 的日期、行程和交通字段，再运行 `node build.cjs`。地图范围目前只覆盖长沙核心区，`android-app/map.html` 的视图范围和 Java 界面仍有城市专用文案，不是任意城市的通用引擎。公开示例页还保留演示标题；如扩展行程需一起调整，不要将私人配置提交到公开仓库。

## 验证状态与授权

见 [验证说明](VERIFICATION.md) 与 [发布范围](PUBLICATION.md)。JVM、静态检查和 detached DOM 模型测试不等于安卓真机、真实定位/相机/Keystore、手机布局或真实模型服务验收；这些仍待完成。示例不是可直接用于正式出行的时刻表。

第三方素材和依赖见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。本仓库暂未为作者原创代码选择开源许可证；公开可见不等于无条件授予复用授权，第三方材料继续适用其原许可。
