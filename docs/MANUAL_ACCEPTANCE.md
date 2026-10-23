# 玩家实机验收 — R0–R4

请在新的测试世界中执行。安装 Minecraft **26.3**、Java **25**、Fabric Loader **0.20.1**、Fabric API **0.165.0+26.3** 及本次构建的 Mod；客户端和服务器均安装。测试 Jar、自动化命令和真实结果见 [实施报告](R0_R4_IMPLEMENTATION_REPORT.md)。完成本清单后再决定是否授权 G1；本版本没有自然生成。

自动化已通过 18 个项目 GameTest 加 1 个原版 sanity test，覆盖注册、层高、半砖/楼梯状态、配方、掉落、地面刷怪判定、材质属性、状态编码及原版地图真实像素。客户端测试已通过模型烘焙、群系草色、资源包替换、260 个干状态保存重开和专服放置/重连。固定版本的可选光影与小地图组合也完成加载和基本场景检查，边界见第 4 节；日志和截图见[实施报告](R0_R4_IMPLEMENTATION_REPORT.md)。下面的清单用于剩余人眼细验与玩家复核，不把自动测试替代为完整人工验收。

## 1. 展示区与 20 个变体

新建启用命令的超平坦创造世界，执行以下指令（仅用于该测试世界）：

```mcfunction
/fill -2 64 -2 18 64 24 minecraft:stone
/tp @s 8 66 23 180 30
/setblock 0 65 0 buildup_groundscape:grass_sod_block
/setblock 3 65 0 buildup_groundscape:grass_sod_patch
/setblock 6 65 0 buildup_groundscape:grass_sod_slab
/setblock 9 65 0 buildup_groundscape:grass_sod_stairs
/setblock 0 65 4 buildup_groundscape:mycelium_sod_block
/setblock 3 65 4 buildup_groundscape:mycelium_sod_patch
/setblock 6 65 4 buildup_groundscape:mycelium_sod_slab
/setblock 9 65 4 buildup_groundscape:mycelium_sod_stairs
/setblock 0 65 8 buildup_groundscape:podzol_sod_block
/setblock 3 65 8 buildup_groundscape:podzol_sod_patch
/setblock 6 65 8 buildup_groundscape:podzol_sod_slab
/setblock 9 65 8 buildup_groundscape:podzol_sod_stairs
/setblock 0 65 12 buildup_groundscape:crimson_nylium_sod_block
/setblock 3 65 12 buildup_groundscape:crimson_nylium_sod_patch
/setblock 6 65 12 buildup_groundscape:crimson_nylium_sod_slab
/setblock 9 65 12 buildup_groundscape:crimson_nylium_sod_stairs
/setblock 0 65 16 buildup_groundscape:warped_nylium_sod_block
/setblock 3 65 16 buildup_groundscape:warped_nylium_sod_patch
/setblock 6 65 16 buildup_groundscape:warped_nylium_sod_slab
/setblock 9 65 16 buildup_groundscape:warped_nylium_sod_stairs
```

- [ ] 自然方块创造栏及搜索栏可以找到全部 20 个物品；切换简体中文/英文后名称完整。
- [ ] 从上、下、四侧查看模型，没有紫黑缺失纹理、缺面、闪烁或穿模；GUI、主手、副手、掉落物也正常。
- [ ] 五种材质的各个面使用对应**原版顶面纹理**。这是原创的全表面草皮设计，侧面和底面不会出现普通草方块的泥土边。

## 2. 放置、碰撞、流体与保存

领取薄层：`/give @s buildup_groundscape:grass_sod_patch 64`。以下操作对其他四种材质各重复一次。

- [ ] 在完整支撑面上右键放置，连续点顶面得到 1–8 层；每层高度 1/8 方块，脚下高度和选框一致。
- [ ] 按住 Shift 再点顶面仍可叠加；点侧面在有支撑的相邻位置放置。不同材质不能混叠，也不能覆盖已有薄层。
- [ ] 第 8 层仍是薄层方块；第 9 次顶面放置在其上方新建一层，原有 8 层不转换成完整草皮方块。
- [ ] 悬空、下半砖上方不能放置薄层；完整方块、上半砖、双半砖、8 层薄层能提供完整顶部支撑。拆除支撑后薄层消失并按层数掉落一次。
- [ ] 薄层不能放入水或岩浆所在格，不消耗物品；薄层不支持含水。另用水流冲刷与活塞移动检查有无遗留悬空或重复掉落。
- [ ] 半砖可下置、上置、同材质合并；不同材质不合并；双半砖破坏掉落 2 个半砖。
- [ ] 楼梯四向、倒置、内角、外角正常；放置和拆掉相邻楼梯会重新计算转角。逐级行走，没有空洞或看不见的碰撞墙。
- [ ] 半砖和楼梯可含水，水桶取放及相邻流体更新正常；含水半砖合并为双半砖后不再含水。
- [ ] 将薄层不同高度、半砖及楼梯紧邻摆放，白天/夜晚分别检查侧面剔除、阴影和接缝；不要只看独立方块。
- [ ] 保存退出并重新进入，所有层数、朝向、转角和含水状态保留；离开足够远使区块卸载后再回来检查一次。

可直接构造八种层高进行对照：

```mcfunction
/setblock 0 65 20 buildup_groundscape:grass_sod_patch[layers=1]
/setblock 1 65 20 buildup_groundscape:grass_sod_patch[layers=2]
/setblock 2 65 20 buildup_groundscape:grass_sod_patch[layers=3]
/setblock 3 65 20 buildup_groundscape:grass_sod_patch[layers=4]
/setblock 4 65 20 buildup_groundscape:grass_sod_patch[layers=5]
/setblock 5 65 20 buildup_groundscape:grass_sod_patch[layers=6]
/setblock 6 65 20 buildup_groundscape:grass_sod_patch[layers=7]
/setblock 7 65 20 buildup_groundscape:grass_sod_patch[layers=8]
```

## 3. 生存获取与回收

在工作台中对每种材质验证以下 8 个配方。原版地表方块按原版方式获取；转换为草皮后**没有还原成原版地表方块的配方**。

| 输入 | 输出 | 排列 |
|---|---|---|
| 1 个对应原版地表方块 | 1 个完整草皮方块 | 无序 |
| 1 个完整草皮方块 | 8 个薄层 | 无序 |
| 8 个薄层 | 1 个完整草皮方块 | 无序，分占 8 格 |
| 3 个完整草皮方块 | 6 个半砖 | 横排 |
| 2 个半砖 | 1 个完整草皮方块 | 无序，分占 2 格 |
| 6 个完整草皮方块 | 8 个楼梯 | 原版阶梯排列 |
| 1 个楼梯 | 6 个薄层 | 无序 |
| 1 个半砖 | 4 个薄层 | 无序 |

- [ ] 生存模式放置每次消耗 1 个物品；创造模式不消耗。循环合成与拆解不会增加草皮总量。
- [ ] 草地、菌丝、灰化土使用铲；两种菌岩使用镐。声音和材质手感符合原版对应方块。
- [ ] 使用合适工具破坏：薄层按层数掉落，完整方块/楼梯各掉 1 个；精准采集、时运不会额外增殖草皮。创造直接破坏不掉落物品。
- [ ] 首次获得配方材料后配方书解锁并可制作；若安装配方查看 Mod，记录其名称/版本并检查显示。

## 4. 草色、纹理包、PBR 与地图

已经实际运行的可选组合：Xaero's Minimap **26.6.1**、Iris **1.11.7**、Sodium **0.9.2**、Complementary Reimagined **r5.9.3**、SPBR **22.5**（`RP_MODE=3`）。[实测截图](evidence/compat-0000_groundscape-showcase.png)显示正常光影场景与 Xaero 默认地表展示；日志确认法线/高光图集创建。其完整客户端测试通过，但**逐面 PBR 精确表现与小地图全部颜色/模式仍未验证**。版本及哈希见[兼容测试清单](evidence/compat-manifest.json)。

- [ ] 在展示区旁放置原版草方块，执行 `/fillbiome -2 64 -2 18 72 24 minecraft:plains`，再分别替换为 `minecraft:swamp` 和 `minecraft:badlands`；等待区块重绘，草皮和原版草顶面应同色，其他四类不被染绿。另在真实群系边界观察混色过渡。
- [ ] 启用一个兼容 26.3、明确替换原版草/菌丝/灰化土/菌岩纹理的资源包。重新加载资源后，草皮相应纹理应同步变化；记录资源包名称和版本，截图保存前后对照。
- [ ] **PBR 精确对照仍待人工验证**：先使用上面的已测组合，在相同光照下并排放置原版地表与草皮，近距离观察顶面、侧面和楼梯转角；改变视角与太阳方向，检查法线凹凸、高光/反射及贴图衔接。记录材质、形态和未对应的面；加载正常、图集创建或引用相同纹理都不等于逐面表现通过。
- [ ] 为每个材质制作至少 16×16 平台，将完整方块、满层薄层、双半砖、楼梯分区；使用未缩放原版地图走近更新，比较对应原版材质颜色。地图阴影可因表面高度不同而变化。
- [ ] **小地图完整行为待人工验证**：使用已测 Xaero 26.6.1 检查同一平台，放大比较五种材质与对应原版地表，切换高度、缩放与洞穴模式；记录色差与缺失区域。其他版本、FTB 等小地图需要独立检查，不能沿用 Xaero 基本显示的 PASS。

## 5. 防刷怪与专用服务器

- [ ] 使用两个条件相同的封闭黑暗平台，一个裸露原版草方块、一个铺满草皮薄层；使用允许敌对生物生成的难度，站在离平台大于 24 格、仍在模拟距离内的位置。确认对照平台确实刷怪后，观察薄层平台；分别测试 1 层和 8 层，其他层数可参照自动判定测试。
- [ ] 该检查针对普通自然地面刷怪；生成蛋、`/summon`、刷怪笼和特殊事件不是等价测试。无怪生成的空对照不能说明防刷怪通过。
- [ ] 启动专用服务器并从客户端连接，生存模式放置、叠加、合并、楼梯转角、拆除；让另一名玩家或第二客户端观察同步（如可用）。
- [ ] 断线重连，再正常关闭并重启服务器；方块与背包数量保持一致，服务端/客户端日志没有相关异常。
- [ ] 检查新区域没有自动生成的草皮，已有区域没有自动回填；本轮仅提供玩家建造功能，不传播、不自动生长。

## 记录与验收决定

每项记录 `PASS` / `FAIL` / `UNVERIFIED`，失败附方块 ID、状态、坐标、复现步骤、日志与截图。未运行的资源包组合、逐面 PBR 以及小地图模式/精确颜色继续保持 `UNVERIFIED`；已有加载或基本场景 PASS 不扩大到这些细项。没有参考 Mod 实机对照，不声称完全一致。

- 客户端/服务端与 Mod 版本：
- 资源包、光影、小地图版本：
- 通过项、失败项、未验证项：
- 日志和截图路径：
- R4 人工验收：通过 / 需修复；G1 是否另行明确授权：
