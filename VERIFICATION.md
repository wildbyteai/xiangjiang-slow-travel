# 公开示例版验证记录

验证日期：2026-10-07。范围仅为本仓库的公开示例副本，不将私人版历史测试当成公开版通过证据。

| 验收项 | 实际检查 | 结果 |
| --- | --- | --- |
| P01 发布范围与隐私 | 明确源码白名单；敏感标识、密钥特征、本机路径、禁止的 APK/签名/备份文件扫描；第三方素材来源核对 | 通过；启发式扫描不代表能证明不存在任何未知密钥 |
| P02 独立生成 | `node build.cjs`，从合成行程、OSM 数据、模板和本地 D3 重建四个入口资源 | 通过；不联网、不读取私人目录 |
| P03 Android | JDK 17、Android SDK 35、本机依赖缓存，生成 Gradle 8.9 Wrapper 后再用 `./gradlew --offline --no-daemon :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` | 43 JVM 测试通过，0 failures/errors；lint 0 errors / 17 warnings；debug 构建成功 |
| P04 手机导游逻辑 | `node scripts/verify.cjs`（数据/语法/引用/署名/隐私/状态，10项），`node scripts/guide-interaction-test.cjs`（detached DOM 交互模型，6项） | 全部通过，包含13站渲染/切换、时间详情、清单、完成与地图消息联动；不是实际浏览器渲染 |
| P05 远端发布 | public 可见性、默认 main、PR 合并、Git tree 内容回读 | 实际远端结果由发布操作完成后核实；不把本表当作预先通过证明 |

测试分布：DiaryStore 18，DiaryPolish 13，DiaryMap 3，ModelSettingsForm 9。Gradle Wrapper JAR SHA-256：`498495120a03b9a6ab5d155f5de3c8f0d986a449153702fb80fc80e134484f17`。

未完成安卓真机或模拟器、定位/相机/Photo Picker/Keystore实际操作、手机代表性视口的渲染布局、真实 Service Worker 离线、真实模型端点请求及首次联网依赖下载验收。本次不声称这些能力已通过真机验收。历史 H5 仅核对源码/资源/脚本语法，不纳入主产品功能通过结论。

没有使用用户真实照片、真实位置、真实密钥或模型额度做测试；没有上传 APK、测试日志或私人备份。构建报告留在被 Git 忽略的本机 build 目录。

## 0.3.0-demo 增量（2026-10-07）

- 行程日期从代码中解耦：新增 `TripDates`，App 与网页都从 `days[].date` 读取，移除 Java/JS 中写死的示例日期。
- 日记页与 HTML 导出改为按“第一天 · 岳麓书院 · 3月15日 周六”分组，导出页改为纸质手账风格（封面、日期印章、照片网格、统计）。
- 本机检查：`node build.cjs`、`verify.cjs`（10项）、`guide-interaction-test.cjs`（6项）通过；主代码对 android-35 `javac` 编译通过；JVM 单元测试 47 项通过（新增 TripDates 4 项）。本次环境为 aarch64，未运行 Gradle lint/assembleDebug，交由新增的 GitHub Actions 在 PR 上验证。
- 真机验收仍待完成，见 LAUNCH.md 第 3 节。
