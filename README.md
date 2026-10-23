# Buildup Groundscape · 地表织景

Minecraft **26.3 / Fabric / Java 25** 的地表建筑装饰 Mod。提供五种材质、四种形态，共 **20 个独立方块**。本版本只包含 R0–R4 手动建筑系统，**不生成或修改自然地形**；G1–G3 尚未开始。

| 材质 | 完整方块 | 1–8 层薄层 | 半砖 | 楼梯 |
|---|---|---|---|---|
| 草地 Grass | ✓ | ✓ | ✓ | ✓ |
| 菌丝 Mycelium | ✓ | ✓ | ✓ | ✓ |
| 灰化土 Podzol | ✓ | ✓ | ✓ | ✓ |
| 绯红菌岩 Crimson Nylium | ✓ | ✓ | ✓ | ✓ |
| 诡异菌岩 Warped Nylium | ✓ | ✓ | ✓ | ✓ |

## 安装

1. 使用 Minecraft **26.3**、Java **25** 和 Fabric Loader **0.20.1 或兼容的后续版本**。
2. 安装适用于 26.3 的 **Fabric API 0.165.0+26.3 或兼容的后续版本**。
3. 将 `build/libs/buildup-groundscape-1.0.0.jar` 放入客户端及专服的 `mods` 目录。不要安装 `-sources.jar`。

客户端和服务器都需要安装。本项目不包含 Forge/NeoForge 版本，也不声明支持其他 Minecraft 版本。发行前建议先在测试世界验收。

## 使用

- 在创造模式的“自然方块”分类查找，或搜索“草皮 / Sod”。提供简体中文、英文物品与标签名称。
- 薄层每层高 **1/8 格**，手持同材质薄层点击顶面叠加，Shift 同样可叠加；侧面点击在相邻有支撑位置放置。
- 八层保持薄层方块身份和防刷怪能力；再放一个会在其上方开始新的一层。拆除按层数掉落。
- 薄层需要下方完整顶面支撑；支撑消失时掉落。不允许直接放进水或岩浆，不支持含水。
- 半砖支持上下半、合并双层和含水；楼梯支持四向、倒置、内外转角和含水，行为沿用原版。
- 三种主世界材质用锹挖掘；两种菌岩用镐。硬度、音效、抗爆、工具要求及地图基础颜色取自对应原版方块。
- 所有形态是稳定装饰方块，不扩散、不退化为泥土/下界岩，不执行草地生长或骨粉繁殖。
- 薄层阻止普通地面自然生成的生物在其表面或所在格生成；不拦截命令、刷怪蛋或其他 Mod 强制生成。

例如：`/give @s buildup_groundscape:grass_sod_patch 64`。所有 ID 为 `buildup_groundscape:<材质>_sod_<形态>`；材质见上表英文对应的 snake_case，形态为 `block / patch / slab / stairs`。

## 生存配方与回收

这些是本项目的原创配方，并非对参考 Mod 配方的逐项复制。需要先取得相应原版地表方块，通常依赖精准采集。

| 输入 | 输出 | 摆放 |
|---|---|---|
| 1 对应原版草方块/菌丝/灰化土/菌岩 | 1 完整草皮方块 | 无序 |
| 1 完整草皮方块 | 8 草皮薄层 | 无序 |
| 8 同材质草皮薄层 | 1 完整草皮方块 | 无序，工作台 |
| 3 完整草皮方块 | 6 草皮半砖 | 横向一排 |
| 2 同材质草皮半砖 | 1 完整草皮方块 | 无序 |
| 6 完整草皮方块 | 8 草皮楼梯 | 原版楼梯形配方 |
| 1 草皮楼梯 | 6 草皮薄层 | 无序 |
| 1 草皮半砖 | 4 草皮薄层 | 无序 |

材料单位为完整方块 8、薄层 1、半砖 4、楼梯 6，各路线守恒。草皮不能反合成为原版方块。正常工具挖掘时，完整方块/楼梯掉落自身，双半砖掉落 2 个半砖，薄层掉落对应层数；精准采集和时运不增加数量。爆炸可能减少回收量。

## 外观和兼容性

草皮使用原创的“所有表面覆盖地表材质”模型：顶面、侧面、底面都引用对应原版顶面纹理。草地所有表面使用当前位置的原版群系草色，其余材质不套用草绿色。纹理未复制进 Mod，资源包对这些原版纹理的替换会作用于草皮。

已实测原版客户端及专服、五种纹理的诊断资源包替换、群系 tint、260 个干状态保存重开、原版地图 20 变体默认状态的真实像素采样。可选组合的结果如下；这些组件不是本 Mod 的必需依赖，也没有打包到发行 Jar。

| 实测可选组合 | 已通过 | 仍待验收 |
|---|---|---|
| Xaero's Minimap 26.6.1 | 加载、默认地表展示；截图可见展示区材质色条 | 全部材质精确色值、洞穴/其他模式、不同高度与缩放 |
| Iris 1.11.7 + Sodium 0.9.2 + Complementary Reimagined r5.9.3 + SPBR 22.5（RP_MODE=3） | 完整客户端测试、光影场景、法线/高光图集创建、资源重载、存档与专服交互 | 每种面、形态的法线/反射与原版精确对照；其他光影/PBR 包 |

版本、截图和测试边界见[实现报告](docs/R0_R4_IMPLEMENTATION_REPORT.md)与[功能矩阵](docs/REFERENCE_PARITY_MATRIX.md)。不声明所有光影、小地图或配方查看器兼容。全表面设计、8 层上限、配方和掉落属于独立原创决定，未实测参考 Mod 的精确行为。

## 构建与验证

工具链固定为 Minecraft 26.3、Java 25、Gradle 9.8.2、Loom 1.19.3、Loader 0.20.1、Fabric API 0.165.0+26.3。使用官方去混淆名称，无 Yarn/Remap 插件或自定义 Mixin。

```powershell
# JAVA_HOME 应指向本机 JDK 25；示例路径请按安装位置调整。
$env:JAVA_HOME = 'C:/Program Files/Java/jdk-25.0.3'
.\gradlew.bat runDatagen
python scripts/verify_resources.py
.\gradlew.bat build
.\gradlew.bat runClientGameTest
```

`build` 包含真实专用 GameTest 服务器测试；客户端测试另外运行，需要可用图形环境。`test` 没有另设 JUnit 用例，不能将其 `NO-SOURCE` 当作测试通过。`runClient` / `runServer` 可启动普通开发环境。首次普通专服启动需要阅读并接受其生成的 `run/eula.txt`；测试环境通过 Loom 的 `configureTests` 单独配置。

`src/main/generated` 的 JSON 由 Fabric Datagen 生成并纳入产物，请修改生成器而非手改输出。Python 3 仅用于资源审计，不是玩家依赖。测试 Mod 位于 `src/gametest`，不会打包进发行 Jar。客户端测试自动创建展示世界及原创棋盘资源包，结果留在 `build/run/clientGameTest/`。

## 验收与许可

- [玩家实机验收清单](docs/MANUAL_ACCEPTANCE.md)
- [R0–R4 实现、命令与证据](docs/R0_R4_IMPLEMENTATION_REPORT.md)
- [功能还原矩阵](docs/REFERENCE_PARITY_MATRIX.md)
- [更新说明](UPDATE_NOTES.md)

保留工作区原有的 [CC0-1.0 LICENSE](LICENSE)。实现没有使用 Grass Overhaul 的源码、Jar、模型或贴图，仅依据其[公开功能说明](https://modrinth.com/mod/grass-overhaul)独立开发。Minecraft 纹理由游戏在运行时提供。本项目不是 Mojang/Microsoft 官方产品。

第一目标交付后停止于 R4，等待人工验收；未经明确授权不开始 G1。
