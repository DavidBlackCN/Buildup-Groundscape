# Reference Parity Matrix — Buildup Groundscape

审计日期：2026-10-23 至 2026-10-24（Asia/Shanghai）。目标：Minecraft 26.3 / Fabric / Java 25。范围仅为 R0–R4；G1–G3 不属于本次实现。

## 证据与状态规则

本项目独立实现 Grass Overhaul 公开介绍中的核心建筑功能。未下载、反编译或复制参考 Mod 的 Jar、源码、模型或贴图；没有参考 Mod 的独立实机对照记录。下文的 `PASS` 只表示相应证据支持的检查通过，不表示与参考 Mod 的内部实现或未公开行为完全一致。

- `REFERENCE-VERIFIED`：参考项目公开说明明确提及；不是本项目测试结果。
- `PARITY-TO-VERIFY`：参考资料未定义细节，或尚未实机比较。
- `GROUNDSCAPE-DESIGN` / `DESIGNED-EQUIVALENT`：本项目原创、自洽的行为选择，不声称逐项等同参考版。
- `PASS`：所列检查实际通过；`FAIL`：检查实际失败；`UNVERIFIED`：没有执行或证据不足；`NOT_APPLICABLE`：该检查不适用。
- 静态资源检查、状态逻辑测试、实际游戏视觉和外部兼容分别记录，不能相互替代。

## 公开参考基线

| 编号 | 可确认的功能范围 | 证据分类 | 参考确认 | 尚不能由公开资料推出的结论 |
|---|---|---|---|---|
| REF-01 | Grass、Mycelium、Podzol、Crimson Nylium、Warped Nylium 五个家族；patch、slab、stairs、full block 四种形态 | REFERENCE-VERIFIED | PASS | 精确注册 ID、内部类结构、每种形态全部状态 |
| REF-02 | 类似地毯的 patch 可以堆叠 | REFERENCE-VERIFIED | PASS | 1–8 层、碰撞厚度、满层转换、Shift/侧面点击规则 |
| REF-03 | 原版草色与所在生物群系一致 | REFERENCE-VERIFIED | PASS | 其他四个家族也应统一使用草色；精确 tint 实现 |
| REF-04 | 引用原版纹理，以适配资源包，并宣称 PBR 支持 | REFERENCE-VERIFIED | PASS | 每种光影、PBR 规范、资源包均可完全兼容 |
| REF-05 | 原版地图与部分小地图具有正确颜色 | REFERENCE-VERIFIED | PASS | 任意小地图版本、全部地图扫描策略均已支持 |
| REF-06 | Patch 用于阻止生物生成 | REFERENCE-VERIFIED | PASS | 具体刷怪判定实现；生成蛋、刷怪笼、命令也应被拦截 |
| REF-07 | 配方、精准采集、掉落数量、工具、流体、传播与保存细节 | PARITY-TO-VERIFY | UNVERIFIED | 公开简介没有完整定义这些行为 |

REF-01–REF-06 来源：[Grass Overhaul 官方 Modrinth 页面](https://modrinth.com/mod/grass-overhaul)。公开页面同时标注 Custom 许可；本项目只将公开功能描述作为参考，遵守独立实现边界。[页面所链接的许可](https://github.com/nvb-uy/AGNYA-License) 单独存在复用限制；未读取参考项目实现。

## 20 变体逐项追踪

下表所有 ID 均位于 `buildup_groundscape` 命名空间。维度缩写：注册 = 方块/物品注册；行为 = 形态、碰撞、放置；资源 = 状态、模型、物品定义、翻译；生存 = 配方/掉落/工具；视觉 = 烘焙模型、tint 与诊断资源包；环境 = 客户端、专服注册与存档。各形态的共用检查和材质检查见后续表格。

`PASS¹` 的证据边界：20 变体通过服务端逻辑测试，260 个干状态在真实客户端展示并保存重开；专服网络交互实际测试 Grass Patch 的 1–8 层堆叠、物品消耗和重连。没有把该网络用例冒称为每个变体的全部网络操作实测。

| 注册 ID 路径 | 材质检查 | 形态检查 | 注册 | 行为 | 资源 | 生存 | 视觉 | 环境 |
|---|---|---|---|---|---|---|---|---|
| `grass_sod_block` | MAT-G | FORM-B | PASS | PASS | PASS | PASS | PASS | PASS¹ |
| `grass_sod_patch` | MAT-G | FORM-P | PASS | PASS | PASS | PASS | PASS | PASS¹ |
| `grass_sod_slab` | MAT-G | FORM-S | PASS | PASS | PASS | PASS | PASS | PASS¹ |
| `grass_sod_stairs` | MAT-G | FORM-T | PASS | PASS | PASS | PASS | PASS | PASS¹ |
| `mycelium_sod_block` | MAT-M | FORM-B | PASS | PASS | PASS | PASS | PASS | PASS¹ |
| `mycelium_sod_patch` | MAT-M | FORM-P | PASS | PASS | PASS | PASS | PASS | PASS¹ |
| `mycelium_sod_slab` | MAT-M | FORM-S | PASS | PASS | PASS | PASS | PASS | PASS¹ |
| `mycelium_sod_stairs` | MAT-M | FORM-T | PASS | PASS | PASS | PASS | PASS | PASS¹ |
| `podzol_sod_block` | MAT-P | FORM-B | PASS | PASS | PASS | PASS | PASS | PASS¹ |
| `podzol_sod_patch` | MAT-P | FORM-P | PASS | PASS | PASS | PASS | PASS | PASS¹ |
| `podzol_sod_slab` | MAT-P | FORM-S | PASS | PASS | PASS | PASS | PASS | PASS¹ |
| `podzol_sod_stairs` | MAT-P | FORM-T | PASS | PASS | PASS | PASS | PASS | PASS¹ |
| `crimson_nylium_sod_block` | MAT-C | FORM-B | PASS | PASS | PASS | PASS | PASS | PASS¹ |
| `crimson_nylium_sod_patch` | MAT-C | FORM-P | PASS | PASS | PASS | PASS | PASS | PASS¹ |
| `crimson_nylium_sod_slab` | MAT-C | FORM-S | PASS | PASS | PASS | PASS | PASS | PASS¹ |
| `crimson_nylium_sod_stairs` | MAT-C | FORM-T | PASS | PASS | PASS | PASS | PASS | PASS¹ |
| `warped_nylium_sod_block` | MAT-W | FORM-B | PASS | PASS | PASS | PASS | PASS | PASS¹ |
| `warped_nylium_sod_patch` | MAT-W | FORM-P | PASS | PASS | PASS | PASS | PASS | PASS¹ |
| `warped_nylium_sod_slab` | MAT-W | FORM-S | PASS | PASS | PASS | PASS | PASS | PASS¹ |
| `warped_nylium_sod_stairs` | MAT-W | FORM-T | PASS | PASS | PASS | PASS | PASS | PASS¹ |

## 原创行为契约与形态测试

| 检查 | 本项目行为/验收条件 | 参考关系 | 实现检查 | 证据或人工检查 |
|---|---|---|---|---|
| FORM-B | 独立完整 sod 方块；完整碰撞，可获得、放置、回收 | REFERENCE-VERIFIED / 细节 DESIGNED-EQUIVALENT | PASS | 五族真实 BlockItem 放置、物品消耗、完整形状、实际战利品求值 |
| FORM-P-01 | 原创 `layers=1..8`，每层高度 1/8，outline/collision/support 相同 | DESIGNED-EQUIVALENT | PASS | 五族 × 八层精确形状比较 |
| FORM-P-02 | 同类顶部点击叠加，Shift 仍可叠加；侧点放邻格；不同材质不合并；8 层保持 Patch，下一次顶部点击在上方放新 Patch | DESIGNED-EQUIVALENT | PASS | 真实 BlockItem.place、逐次消耗、不同手持物/侧点/满层边界测试 |
| FORM-P-03 | 需要下方完整上支撑面；支撑丢失后移除并按层数掉落 | GROUNDSCAPE-DESIGN | PASS | 无支撑放置拒绝且不消耗；实际邻居更新与五份物品实体检查 |
| FORM-P-04 | Patch 不水浸，不可直接放入非空流体 | GROUNDSCAPE-DESIGN | PASS | 源水/岩浆中的放置失败且不消耗；外来水流冲刷另列人工待验 |
| FORM-P-05 | Patch 拒绝普通地面自然刷怪的表面及内部空间判定 | REFERENCE-VERIFIED / 原创实现 | PASS | 原生 SpawnPlacements 检查：草方块阳性对照；五族 × 八层上方/内部均拒绝 |
| FORM-S | 原版 top/bottom/double、同类合并、水浸、正确碰撞与双份回收 | DESIGNED-EQUIVALENT | PASS | 实际放置/合并/消耗、水浸合并清水状态、三形状与原版对照、掉落求值 |
| FORM-T | 四向、上下、straight/inner/outer；邻居变化更新 | DESIGNED-EQUIVALENT | PASS | 五族 × 40 几何状态与原版碰撞/outline 比较；40 次朝向/half 放置、实际内外角更新与水浸 |
| SAVE | 全部状态编码；世界关闭/重开保持展示状态 | GROUNDSCAPE-DESIGN | PASS | 475 个 BlockState codec round-trip；另有 260 个干状态真实世界关闭、重开、客户端核对 |
| DROP | Patch 每层一个；双半砖两个；完整块/楼梯一个；Fortune/Silk 不增加数量 | DESIGNED-EQUIVALENT | PASS | 真实加载 loot table 求值，空工具/Fortune III/Silk Touch；工具采集门槛保持对应原版材质 |
| CREATIVE | 创造放置/叠加不扣物品 | GROUNDSCAPE-DESIGN | PASS | 正确初始化创造模式 abilities 的真实 BlockItem.place；创造破坏视觉仍可人工复核 |
| RECIPE | 每族八个配方；转换保持体积且不能转回原版材料刷资源 | DESIGNED-EQUIVALENT | PASS | 40 个实际加载配方逐一 matches/assemble/getRecipeFor；完整=8、半砖=4、楼梯=6、Patch=1 单位 |
| WORLDGEN | 不注册自然生成、不回填世界、不创建 G1 占位入口 | GROUNDSCAPE-DESIGN | PASS | 源码/资源范围审查；G1–G3 仅保留设计文档 |

## 材质与客户端检查

| 检查 | 设计/检查范围 | 静态或自动验证 | 实机视觉 | 证据或验收要求 |
|---|---|---|---|---|
| MAT-G | Grass：真实位置的原版群系草色；物品默认草色；六面草面纹理 | PASS | PASS | 客户端平原/沼泽/恶地与原版 grass tint 逐值比较；展示截图 |
| MAT-M | Mycelium：六面原版菌丝顶纹理；不套草色；对应地图色/工具/声音 | PASS | PASS | 客户端 tint 源为空，70 模型审计及五族展示 |
| MAT-P | Podzol：六面原版灰化土顶纹理；不套草色；对应地图色/工具/声音 | PASS | PASS | 客户端 tint 源为空，70 模型审计及五族展示 |
| MAT-C | Crimson Nylium：六面原版绯红菌岩顶纹理；不套草色；对应地图色/工具/声音 | PASS | PASS | 客户端 tint 源为空，70 模型审计及五族展示 |
| MAT-W | Warped Nylium：六面原版诡异菌岩顶纹理；不套草色；对应地图色/工具/声音 | PASS | PASS | 客户端 tint 源为空，70 模型审计及五族展示 |
| MODEL | 顶/侧/底面、层数、半砖和楼梯转角、面剔除与模型覆盖 | PASS | PASS | 70 原创网格的全部外露面、260 几何状态、200 楼梯旋转；475 状态客户端无 missing model；细微光照见人工待验 |
| ITEM | 20 个物品定义、en_us/zh_cn、七个 item tag 双语名称、创造栏 | PASS | PASS（有限） | 资源审计与展示热栏；每个物品的全部手持/掉落视角仍须人工细验 |
| PACK | 原版纹理运行时引用；诊断资源包真实覆盖五种纹理并重载 | PASS | PASS | 原创棋盘纹理测试包、sourcePackId/sprite 检查和重载后世界截图；测试后恢复 |
| PBR-LOAD | Iris 1.11.7 / Sodium 0.9.2 / Complementary Reimagined r5.9.3 / SPBR 22.5，RP_MODE=3 | PASS | PASS（加载/场景） | 可选组合完整客户端测试 exit 0；实际创建 blocks_n/blocks_s 图集，正常展示/资源包重载/存档/专服交互 |
| PBR | 各材质和形态每种面的法线/反射与原版精确对照 | NOT_APPLICABLE | UNVERIFIED | 图集创建与正常光影场景不能替代逐面 PBR 视觉核对；不声明所有光影或材质包兼容 |
| MAP | 每种材质的原版地图颜色及真实地图像素 | PASS | UNVERIFIED（人工显示/阴影） | 原版 MapItem.update 扫描 20 变体默认状态 + 5 个原版实体地表对照；25 个像素材质编号正确，不直接写入颜色；高度阴影与全部层级地图外观须人工细验 |
| MINIMAP-LOAD | Xaero's Minimap 26.6.1 加载及默认地表展示 | PASS | PASS（有限） | 实际运行截图左上显示展示区与材质色条；与光影组合一起通过完整客户端测试 |
| MINIMAP | 小地图每种材质精确颜色、不同模式/高度/缩放 | NOT_APPLICABLE | UNVERIFIED | 基本地表显示不代表全部颜色和洞穴模式通过；FTB 等其他小地图未测试 |

## 环境与交付证据

| 检查 | 状态 | 证据/说明 |
|---|---|---|
| Minecraft 26.3 版本基线 | PASS | [Fabric 26.3 迁移文章](https://www.fabricmc.net/2026/09/15/263.html) 确认去混淆名称和 26.3 的 API 变更；具体签名仍以目标依赖为准 |
| 依赖实际可解析与 Wrapper 构建 | PASS | Java 25.0.3 / Gradle 9.8.2 / Loom 1.19.3 / Loader 0.20.1 / Fabric API 0.165.0+26.3；精确命令及最终构建见实施报告 |
| Data Generation 与资源审计 | PASS | runDatagen 实际执行；verify_resources.py 检查生成几何、纹理引用、配方、掉落、语言和标签 |
| 服务端自动化测试 | PASS | 18 个本项目 GameTest + Minecraft always_pass，共 19/19 通过；初次创造 mock 夹具失败已据当前 API 修复后重跑 |
| 客户端启动/进入世界/20 变体 | PASS | 实际 runClientGameTest，完整展示、tint/模型/资源包检查、截图；最终运行 exit 0 |
| 专用服务器启动 | PASS | 独立 runServer 达到 Done，并用 stop 正常保存关闭、exit 0；客户端测试内专服实际启动、连接、交互并关闭，无客户端类加载崩溃 |
| 客户端连接专服及重连保存 | PASS | 草地 Patch 生存连续叠至 8 层、消费 8 个物品、断开/重连后状态一致；其他变体网络用例边界见注¹ |
| 可选光影/PBR 包/小地图组合 | PASS（加载与基本场景）；精确表现 UNVERIFIED | 上述固定版本组合实际完整运行；限制见 PBR / MINIMAP，不泛化到其他版本 |
| 未复制参考实现 | PASS | 本次只访问参考公开介绍与许可；70 个网格由原创 datagen 生成，没有参考资产/代码或打包原版纹理 |

Fabric 开发入口为 [Develop](https://fabricmc.net/develop/)。检查时 [开发文档](https://docs.fabricmc.net/develop/) 首页仍显示 26.2；不能据此将旧版范例当作 26.3 已验证接口。

## 可复核的证据

- [服务端 GameTests 源码](../src/gametest/java/com/davidblackcn/test/GroundscapeGameTests.java)：注册、形状、交互、掉落、配方、刷怪、编码、材质属性。
- [原版地图栅格化测试](../src/gametest/java/com/davidblackcn/test/GroundscapeMapTests.java)：通过原生扫描获得真实地图像素，不以方块属性自测替代地图行为；覆盖各变体默认状态。
- [客户端端到端测试](../src/gametest/java/com/davidblackcn/test/GroundscapeClientTest.java)：模型/tint、实际资源包重载、260 状态保存重开、专服连接/消耗/重连。
- [资源独立审计](../scripts/verify_resources.py)：检查真实输出 JSON，几何与体积守恒算法独立于 datagen。
- [客户端运行日志](evidence/client-gametest.log)、[五族展示](evidence/0000_groundscape-showcase.png)、[诊断资源包覆盖](evidence/0001_groundscape-resourcepack.png)、[专服满层堆叠](evidence/0002_groundscape-dedicated-stacking.png)。三张截图已实际查看；资源包截图显示游戏世界中的棋盘纹理，不是重载画面。
- [服务端测试日志](evidence/server-gametest.log)、[19/19 测试 XML](evidence/gametest-results.xml)、[普通专用服务器启动/正常停止日志](evidence/dedicated-server.log)。
- [可选组合版本/来源/哈希清单](evidence/compat-manifest.json)、[可选组合运行日志](evidence/compat-client-gametest.log)、[光影与 Xaero 展示](evidence/compat-0000_groundscape-showcase.png)、[组合内资源包替换](evidence/compat-0001_groundscape-resourcepack.png)、[组合内专服堆叠](evidence/compat-0002_groundscape-dedicated-stacking.png)。三张图已独立查看。第三方文件仅放在本地测试目录，不打包到 Mod。
- 全部命令、修复过程、最终构建结果和其他证据路径见 [R0–R4 实施报告](R0_R4_IMPLEMENTATION_REPORT.md)。

## 明确的原创差异与待验收

未实测参考 Mod，故不声称「100% 还原」。以下是有意的 `DESIGNED-EQUIVALENT`，不是已知参考版差异：

- 每层 1/8，高度到 8 仍保持防刷怪 Patch 身份；再点击放上层新 Patch，不悄然转换成可刷怪的完整 sod 方块。
- 六个外表面均使用该家族的原版顶面纹理；Grass 全面使用群系 tint。没有泥土/下界岩背衬，不主张与参考的 UV 或侧面模型逐像素相同。
- 完整块=8、半砖=4、楼梯=6、薄层=1 单位；六完整块制成八楼梯，并可拆回薄层，保持体积。不会把 sod 反向转换为原版草地/菌岩。
- 装饰方块不传播、不退化、不因遮光改变材质，不引入随机刻、方块实体、Mixin 或世界扫描。
- Patch 不水浸，Slab/Stairs 沿用原版水浸机制。原版材质工具门槛、硬度与声音保留。

仍为 `UNVERIFIED` 的边界：PBR 各面的法线/反射精确对照、第三方小地图完整颜色/模式行为、参考 Mod 实机逐项对照、全部手持/掉落角度的视觉细节、各类外来流体冲刷/活塞/爆炸/实体寻路组合。现有刷怪测试验证原版地面生成判定，未把命令召唤、生成蛋或刷怪笼纳入“禁止生成”承诺。上述项目的可复现步骤见 [人工验收](MANUAL_ACCEPTANCE.md)。

可选组合日志包含开发账号认证/Realms 请求失败、Iris 开发环境 refmap 提示、资源包原版 mace 纹理引用和光影原版 stone_slab 属性提示，以及资源重载期间的短时延迟；未作为“零警告”运行记录。它们不使本 Mod 的模型/状态测试失败，但也不能据此声称第三方组合没有任何上游限制。

本次不实施 G1–G3。完成第一目标交付后等待用户人工验收，不把本矩阵作为启动自然生成开发的授权。
