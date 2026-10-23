# R0–R4 实施与验收报告

交付日期：2026-10-24（Asia/Shanghai）。本次完成第一目标的 R0–R4 实现、构建、自动回归和基础运行验证，状态为 **PASS WITH RISKS，等待玩家人工验收**。没有开始 G1–G3，没有自然生成、区块回填或地形修改代码。

## 1. 范围与构建产物

仅修改 `26.3/`。工作区已有 Fabric 模板，在此基础上完成实现；根目录规范与其他版本目录未改。工作区不是 Git 工作树，因此采用文件、生成资源与 Jar 内容检查，未声称完成 Git diff、分支或提交审查，也未提交/推送。

| 产物 | 文件 | 字节数 | SHA-256 |
|---|---|---:|---|
| 可安装 Mod | [buildup-groundscape-1.0.0.jar](../build/libs/buildup-groundscape-1.0.0.jar) | 140761 | `7b68f26a140b00a201dcea080f1e22e7fcadd50eb70f542c33e55b46a29a731e` |
| 源码包 | [buildup-groundscape-1.0.0-sources.jar](../build/libs/buildup-groundscape-1.0.0-sources.jar) | 117217 | `8757484e938e1f5e802a9e3e0fd130f7d2c6dc9171bfd8484c1e4a04d9581ab7` |

完整工程源码、Wrapper、生成器与测试均位于本目录。玩家只安装第一项；`sources.jar` 不放入 `mods`。两个 Jar 都已检查，不带测试 Mod、Datagen 缓存、第三方兼容测试 Jar 或 G1 资源。生产 Jar 的 233 个生成 JSON 与源码输出逐字节一致。

## 2. 版本与 API 依据

| 项目 | 实际使用 |
|---|---|
| Minecraft | 26.3，元数据限定该版本 |
| Java | JDK 25.0.3，编译 release/toolchain 25 |
| Gradle Wrapper | 9.8.2 |
| Fabric Loom | 1.19.3，固定正式版本 |
| Fabric Loader | 0.20.1 |
| Fabric API | 0.165.0+26.3 |
| 名称体系 | 26.3 官方去混淆名称，无 Yarn `mappings` 配置 |
| 客户端环境 | Windows 11、NVIDIA RTX 5070 Ti、驱动 621.01 |

[Fabric 26.3 官方说明](https://www.fabricmc.net/2026/09/15/263.html)与[开发入口](https://fabricmc.net/develop/)用于核对版本方向。访问时开发文档首页仍为 26.2；具体类型和签名以项目解析的 **26.3 Minecraft/Fabric 依赖、官方类签名及实际编译/运行**为准，没有照抄旧版 Yarn 示例。

已采用当前 `Identifier`、`CreativeModeTabEvents`、`BlockColorRegistry` / `BlockTintSources` 与 26.3 数据格式。没有自定义 Mixin、Access Widener、反射兼容层或额外运行依赖。服务端不会执行客户端着色、模型或 Datagen 入口。

## 3. 已实现的 20 个变体

所有 ID 均以 `buildup_groundscape:` 开头。

| 家族 | 完整方块 | 薄层 | 半砖 | 楼梯 |
|---|---|---|---|---|
| Grass | `grass_sod_block` | `grass_sod_patch` | `grass_sod_slab` | `grass_sod_stairs` |
| Mycelium | `mycelium_sod_block` | `mycelium_sod_patch` | `mycelium_sod_slab` | `mycelium_sod_stairs` |
| Podzol | `podzol_sod_block` | `podzol_sod_patch` | `podzol_sod_slab` | `podzol_sod_stairs` |
| Crimson Nylium | `crimson_nylium_sod_block` | `crimson_nylium_sod_patch` | `crimson_nylium_sod_slab` | `crimson_nylium_sod_stairs` |
| Warped Nylium | `warped_nylium_sod_block` | `warped_nylium_sod_patch` | `warped_nylium_sod_slab` | `warped_nylium_sod_stairs` |

- **R0**：完善 Java 25 工程、元数据、入口、固定版本、参考功能矩阵及独立实现边界。
- **R1**：20 方块/物品、自然方块创造栏；薄层 1–8 层、每层 1/8 格、真实厚度碰撞与选框、支撑和堆叠；原版半砖合并/水浸、楼梯四向/倒置/内外角/水浸。全部共 475 个 BlockState。
- **R2**：70 个原创模型、完整状态/物品定义、五类原版顶面纹理引用、Grass 全面群系 tint、原版地图色、简中/英文；233 个 JSON 由 Fabric Datagen 生成，可稳定重建。
- **R3**：40 个配方、20 个掉落表、工具与音效/硬度/抗爆属性、薄层防自然地面刷怪、服务端与真实联网交互。
- **R4**：18 个本项目服务端 GameTest、客户端端到端测试、独立资源审计、真实地图采样、普通专服启动、可选兼容冒烟、复核修复、文档与构建产物。

### 关键原创决定

参考公开资料未定义的细节均采用原创行为，不冒称完全还原：

1. 薄层满八层后保持 Patch 身份及防刷怪能力；再点击顶面会在上方放置新薄层。Shift 可叠加，侧点相邻放置，不同材质不能混叠；支撑丢失按原生邻居更新路径掉落一次。
2. 薄层不水浸，不能直接放进非空流体；半砖和楼梯使用原版机制。所有形态稳定装饰，不传播、不退化、不运行草地随机刻或骨粉繁殖。
3. 六面均为对应原版顶面材质；Grass 六面按真实群系草色着色，其余家族不套草色。纹理在运行时引用，没有打包游戏纹理或参考 Mod 资产。
4. 体积单位为完整块 8、半砖 4、楼梯 6、薄层 1；六个完整块制作八个楼梯，可拆回薄层，全部转换守恒，不允许反向生成原版地表材料。
5. 防刷怪同时使用表面 `isValidSpawn=false` 和原版 `prevent_mob_spawning_inside` 标签，覆盖薄层上方及其内部候选格；没有阻断命令、生成蛋或任意 Mod 强制生成。

公开参考依据与逐项范围见 [Reference Parity Matrix](REFERENCE_PARITY_MATRIX.md)。未下载、反编译或复用 Grass Overhaul 的 Jar、源码、模型或贴图；没有其旧版实机逐项对照，因此不声称 100% 一致。

## 4. 修改文件与原因

| 路径 | 内容与目的 |
|---|---|
| [build.gradle](../build.gradle)、[gradle.properties](../gradle.properties) | 固定 Loom/Java、分离 common/client/test、Datagen、构建时 GameTest、源码缓存排除、可选本地兼容测试参数 |
| [fabric.mod.json](../src/main/resources/fabric.mod.json) | 名称空间、真实入口、版本与环境元数据；原图标移动至一致路径 |
| [BuildupGroundscape.java](../src/main/java/com/davidblackcn/BuildupGroundscape.java) | 主入口及统一标识符 |
| [SurfaceFamily.java](../src/main/java/com/davidblackcn/registry/SurfaceFamily.java)、[GroundscapeBlocks.java](../src/main/java/com/davidblackcn/registry/GroundscapeBlocks.java) | 五族材质属性、通用注册及创造栏 |
| [SodPatchBlock.java](../src/main/java/com/davidblackcn/block/SodPatchBlock.java)、[SodStairsBlock.java](../src/main/java/com/davidblackcn/block/SodStairsBlock.java) | 薄层原创逻辑；原版楼梯构造入口 |
| [BuildupGroundscapeClient.java](../src/client/java/com/davidblackcn/client/BuildupGroundscapeClient.java) | 仅客户端草色注册 |
| [BuildupGroundscapeDataGenerator.java](../src/client/java/com/davidblackcn/client/BuildupGroundscapeDataGenerator.java)、[GroundscapeResourceProvider.java](../src/client/java/com/davidblackcn/client/data/GroundscapeResourceProvider.java) | 生成 70 模型、20 状态、20 物品、40 配方、40 解锁进度、20 掉落、21 标签和两种语言 |
| `src/main/generated/` | 233 个实际 Datagen 输出，不手工维护重复 JSON |
| [GameTests](../src/gametest/java/com/davidblackcn/test/GroundscapeGameTests.java)、[地图测试](../src/gametest/java/com/davidblackcn/test/GroundscapeMapTests.java)、[客户端测试](../src/gametest/java/com/davidblackcn/test/GroundscapeClientTest.java) | 注册/形态/交互/配方/回收/地图/渲染/保存/联网真实检查；独立测试 Mod 不打入产品 |
| [verify_resources.py](../scripts/verify_resources.py) | 独立检查生成网格外露面、旋转、资源引用、配方守恒、掉落和翻译 |
| [.github/workflows/build.yml](../.github/workflows/build.yml)、[.gitignore](../.gitignore) | CI Datagen 变更检查、资源审计、构建与 GameTest；忽略生成缓存 |
| [README](../README.md)、[矩阵](REFERENCE_PARITY_MATRIX.md)、[人工清单](MANUAL_ACCEPTANCE.md)、本报告、[UPDATE_NOTES](../UPDATE_NOTES.md)、`docs/evidence/` | 玩家说明、证据及交付边界 |

删除模板中无业务作用的 Example Mixin 和对应 JSON；保留原 LICENSE、Wrapper 及不相关文件。未新增世界生成接口、空 Feature 或未来 G1 实现。

## 5. 实际执行的验证

以下命令均在 `26.3/` 执行。机器默认 Java 为 21，实际构建前显式选择已安装的 25；Gradle 使用已有用户缓存。完成联网解析后回归采用 `--offline`，未关闭任何必要校验。

```powershell
$env:JAVA_HOME = 'C:/Program Files/Java/jdk-25.0.3'
$env:GRADLE_USER_HOME = 'C:/Users/David_Black_/.gradle'
$env:DEBUG = ''
.\gradlew.bat runDatagen --offline --console=plain
python scripts/verify_resources.py --minecraft-client-jar C:/Users/David_Black_/.gradle/caches/fabric-loom/26.3/minecraft-client.jar
.\gradlew.bat build --offline --console=plain
.\gradlew.bat runClientGameTest --offline --console=plain
.\gradlew.bat runServer --offline --console=plain
```

路径是本机实际值，其他机器按其 JDK/缓存位置替换；初次构建需要联网。普通专服在测试目录接受 EULA，绑定回环地址，启动后输入 `stop` 正常保存。没有对外公开服务器。

| 检查 | 实际结果 | 证据与边界 |
|---|---|---|
| 初始模板构建/依赖解析 | PASS | 正式 Loom 与目标依赖可解析；不是只写配置 |
| 最终 `runDatagen` | PASS，21 秒 | 233 文件，`written: 0`；[日志](evidence/datagen.log) |
| 生成幂等校验 | PASS | 重跑前后排序路径+内容 SHA-256 摘要完全一致：`7ac4a80e51098a3186ae4dd77d1e7bc358697abd14491a91771ef84021d8a25a` |
| Python 资源审计 | PASS | 20 变体、70 外露面网格、260 干燥几何状态、200 楼梯旋转、40 守恒配方、20 掉落、两种语言、标签；原版 Jar 的全部纹理引用存在，200 楼梯 rotation/uvlock 相同 |
| **最终 Wrapper `build`** | **PASS，22 秒，exit 0** | [完整构建日志](evidence/build.log)；封装修复后重新执行 |
| 服务端 GameTest | **19/19 PASS** | 18 个本项目用例 + Minecraft `always_pass`；[XML](evidence/gametest-results.xml)、[日志](evidence/server-gametest.log) |
| 普通 `test` 任务 | NO-SOURCE | 未另设 JUnit 测试；真正玩法测试由 `check -> runGameTest` 执行 |
| 基础 `runClientGameTest` | PASS，54 秒，exit 0 | [客户端日志](evidence/client-gametest.log)；真实图形客户端、单人及专服连接，不是只编译 |
| 普通 `runServer` | PASS，37 秒，exit 0 | [日志](evidence/dedicated-server.log)有 `Done`、停止、保存；独立进程启动成功 |
| 可选组合 `runClientGameTest -PcompatMods=...` | PASS，1 分 1 秒，exit 0 | [命令输出](evidence/compat-client-command.log)、[客户端日志](evidence/compat-client-gametest.log)，具体边界见下一节 |
| Jar 内容核验 | PASS | 元数据版本 1.0.0，233 JSON 一致；无测试、缓存、兼容 Jar 和 G1 内容 |
| GitHub Actions 云端执行 | NOT RUN | 已完善工作流，当前非 Git 工作树，未提交触发远端 CI |

### 自动化覆盖细节

- 服务端实际验证 20 注册、完整方块放置与扣物品、五族 × 八层精确碰撞/选框、薄层堆叠/Shift/侧点/不同物品/满层/支撑丢失/流体拒绝、创造模式不扣物品。
- 对照原版半砖与五族 × 40 楼梯几何状态，测试真实半砖合并、水浸、楼梯朝向/倒置及相邻内外角更新。
- 475 状态 codec 往返；40 个真实加载配方逐一 `matches/assemble/getRecipeFor`；实际掉落表求值，覆盖空工具/时运 III/精准采集；材质工具、硬度、声音、地图基础色。
- 原生普通地面 `SpawnPlacements` 以草方块为阳性对照，五族 × 八层的上方及内部均拒绝生成。
- 地图测试通过原生 `MapItem.update` 扫描地表，20 个变体默认状态 + 五种原版对照的 25 个像素材质编号全部匹配；没有直接写颜色冒充采样。
- 客户端核对全部 475 状态模型非 missing，展示 260 个干状态；平原、沼泽、恶地 Grass tint 与原版逐值匹配，其他四族不着草色；真实关闭并重开保存的 260 状态。
- 原创诊断棋盘资源包真实替换五个原版纹理，重载后检查 sourcePackId 和模型 sprite、截图并恢复；专服客户端实际发送放置包，生存叠至八层、消耗八件，断线重连仍为八层。

展示截图：[原版材质](evidence/0000_groundscape-showcase.png)、[诊断资源包](evidence/0001_groundscape-resourcepack.png)、[专服堆叠](evidence/0002_groundscape-dedicated-stacking.png)。截图均已查看。测试世界在 `build/run/clientGameTest/saves/`，该目录可被下次基线测试清理；[人工清单](MANUAL_ACCEPTANCE.md)另提供可复制建造指令，不依赖临时世界永久保留。

## 6. 小地图 / PBR 可选组合

下载仅用于隔离开发运行目录，按 Modrinth 官方项目版本元数据核对 Minecraft/Fabric 兼容性和 SHA-512；未作为产品依赖或重新分发。确切来源、版本 ID、文件名及哈希见 [compat-manifest.json](evidence/compat-manifest.json)。

| 组件 | 实测版本 |
|---|---|
| Xaero’s Minimap | fabric-26.3-26.6.1 |
| Iris | 1.11.7+26.3-fabric |
| Sodium | mc26.3-0.9.2-fabric |
| Complementary Reimagined | r5.9.3，HIGH，`RP_MODE=3`（labPBR） |
| SPBR | 22.5 |

复现时将清单对应文件放入 `build/compat/`，在 `build/run/clientGameTest/` 中准备 `config/iris.properties`（`enableShaders=true`、`shaderPack=ComplementaryReimagined_r5.9.3.zip`），shaderpacks 下放光影及同名 `.zip.txt`（`RP_MODE=3`），resourcepacks 下放 SPBR，`options.txt` 选择 `resourcePacks:["file/SPBR-22_5.zip"]`。然后运行：

```powershell
$compatJars = (Get-ChildItem -LiteralPath 'build/compat' -Filter '*.jar' |
    ForEach-Object { $_.FullName }) -join ';'
.\gradlew.bat runClientGameTest "-PcompatMods=$compatJars" --offline --console=plain
```

该参数仅给客户端测试添加外部 Mod 并保留预配置测试目录，不改变产品依赖。参数使用 Windows 路径分隔符；其他平台需相应调整。基线测试未传该参数。

**已确认**：组合成功加载，完整客户端回归通过；Xaero 地表模式能显示五族展示区色条，光影世界正常渲染，SPBR 被加载，Iris 建立 `blocks_n` / `blocks_s` 图集；诊断包重载、保存重开和专服连接仍通过。截图：[组合展示与小地图](evidence/compat-0000_groundscape-showcase.png)、[组合资源包重载](evidence/compat-0001_groundscape-resourcepack.png)、[组合专服](evidence/compat-0002_groundscape-dedicated-stacking.png)。

**未确认**：所有小地图模式/缩放/配色的精确一致；各家族各个面的 PBR 法线、视差、反射与原版逐项等同；其他光影/资源包/小地图组合。加载成功及图集存在不等于上述视觉检查通过，保留 `UNVERIFIED`。

## 7. 开发中问题、修复与日志说明

| 发现的问题 | 根因与处理 | 最终状态 |
|---|---|---|
| 默认 Java 21、沙箱缓存目录和首次离线 Loom 未解析 | 选择实际 JDK 25 / 用户 Gradle 缓存，先正常解析精确版本，再离线回归 | 修复，完整构建 PASS |
| 旧式 API/数据字段不能直接用于 26.3 | 逐项核对实际依赖；使用当前 tint/创造栏 API 和 loot 数据字段 | 编译、真实加载及掉落求值 PASS |
| 创造模式 mock 最初扣物品导致一个 GameTest 失败 | 测试假玩家未同步 abilities；按真实 GameType 初始化测试夹具，没有绕过断言或改生产消耗逻辑 | 修复后最终 19/19 PASS |
| 客户端展示行距可能覆盖前一族末行 | 展示夹具行距由 4 调整为 6，并断言恰好 260 独立坐标 | 保存重开与截图 PASS |
| 初次资源包截图停在重载覆盖画面 | 测试等待 overlay 消失并等待区块渲染后再截图 | 重跑，世界棋盘纹理可见 |
| 自家 item tag 缺译名提示 | Datagen 补齐七个标签的中英翻译 | 资源审计 PASS |
| 首次普通服务端未接受 EULA；一次非交互启动无法发送 stop | 配置仅测试运行目录 EULA；关闭该任务自有进程后改用交互终端重跑 | 最终普通专服正常 stop、exit 0 |
| 源码 Jar 带入 Datagen `.cache` | 最终打包审查发现，构建显式排除缓存 | 重建后两个 Jar 均无缓存 |

没有隐瞒运行日志警告：基础开发客户端存在原版测试默认各向异性值警告、离线开发令牌的 Realms/认证 401 及 `end_of_frame` 提示。可选组合另有开发 refmap 提示、Xaero 更新查询超时、SPBR 的原版 mace 纹理引用提示、光影原版 slab 变体提示及资源重载短时落后。这些不被记录为“零错误日志”；未发现本 Mod 缺失模型/资源或类加载崩溃，测试最终均退出 0。未修改上游 Mod 来隐藏警告。

## 8. 自检与剩余范围

```text
Code Review: PASS WITH RISKS
Scope: 26.3 全部生产实现、生成资源、测试、构建/CI、Jar 及交付文档
BLOCKER: None
MAJOR: None
MINOR: None outstanding
Fixes Applied During Review: 排除源码包 Datagen 缓存；完善标签译名和测试截图等待
Verification: Wrapper build / 19 GameTests / 两套客户端回归 / 独立专服 / Datagen / 资源及封装审计 PASS
Update Notes: PASS
Residual Risks: 以下人工视觉与未穷举运行组合
```

1. 没有 Grass Overhaul 原版实机逐项对照，模型、配方、满层行为等为已说明的原创设计；不承诺内部实现或像素级还原。
2. 人工仍需查看所有手持/掉落视角、紧邻形态的细微遮挡/光照、真实群系边界混色，以及 PBR 各面表现；可选测试仅证明具体组合冒烟与列出的显示范围。
3. 地图实际像素测试覆盖各变体默认干燥状态，不覆盖全部含水状态、地图缩放或阴影。保存重开覆盖 260 干状态，475 状态序列化包含含水状态；未把两者混写为全部状态实机保存。
4. 实际专服网络测试覆盖 Grass Patch 堆叠/扣物品/重连，未穷举全部形态的多人操作与完整服务器重启组合。
5. 外来流体冲刷、活塞、爆炸、实体寻路及长时自然刷怪平台没有专项完整实机测试；基础形状/支撑/掉落与原生刷怪判定有自动覆盖。相关步骤已列入人工清单。
6. 未运行配方查看器等其他 Mod 组合、未执行远端 CI、未做正式发布签名。没有已知阻断代码缺陷。

## 9. 玩家最短验收顺序

完整指令和记录表见 [MANUAL_ACCEPTANCE.md](MANUAL_ACCEPTANCE.md)。建议先在新测试世界完成：

1. 安装正确版本，找到五族 × 四形态，确认没有紫黑纹理与明显色差。
2. 每族薄层叠 1–8 层、拆支撑；半砖合并；楼梯转内外角并倒置行走。
3. 生存合成、挖掘与回收，确认薄层按层数、双半砖按两件掉落。
4. 对比三个群系草色、切换资源包；查看原版地图与常用小地图，按需检查 PBR。
5. 专服放置/拆除/重连并重启保存，观察黑暗平台防刷怪对照；确认没有自然生成。

发现问题记录 ID、BlockState、操作、版本和日志。**本次停在 R4，等待人工验收；没有执行也不会自动进入 G1。**
