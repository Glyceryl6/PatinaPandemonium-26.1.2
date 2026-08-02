# Patina Pandemonium / 锈色狂欢

一个面向 Minecraft Java 26.1.2、NeoForge 26.1.2 的“方块数量失控”模组：把可发现的完整碰撞方块扩展为铜方块式的四阶段氧化家族，并补齐缺少的常用建筑制品。

## 功能概览

- 对每个符合条件的完整方块建立：原始、斑驳、锈蚀、氧化四阶段。
- 每个阶段都有未涂蜡和涂蜡状态。
- 自动补齐台阶、楼梯、墙、栅栏、栅栏门、按钮、压力板、告示牌与墙上告示牌。
- 门和活板门按需求明确排除。
- 优先通过常见命名规则复用原版或其他模组已经存在的制品，不重复注册。
- 自动扫描启动时已经注册的其他模组完整方块，并为它们生成同样的家族。
- 所有新物品名称由“氧化阶段 + 原方块名 + 制品类型”三个本地化组件拼接，不需要逐个写语言键。
- 新建“锈色狂欢”创造模式标签页，放入生成方块和被复用的现有方块。
- 运行时生成客户端资源包与服务端数据包；`runData` 使用同一写入器输出可检查的 JSON/PNG 文件。

## 方块筛选

默认把“默认状态碰撞形状为完整立方体”的方块视为源方块。台阶、楼梯、墙、栅栏、栅栏门、按钮、压力板、门、活板门和告示牌本身不会再次成为源方块；`exposed_`、`weathered_`、`oxidized_`、`waxed_` 前缀的已有氧化方块也会跳过，避免递归扩张。

这里刻意不保留源方块的特殊功能。生成方块会复制硬度、声音等基础属性，但箱子、熔炉、红石机器等方块的变种只是普通建筑方块，符合“不考虑合理性以及原功能”的目标。

## 资源与数据生成

`GeneratedPackWriter` 会生成：

- 方块状态；
- 方块模型与 26.1 客户端物品声明；
- 由源贴图算法着色得到的阶段贴图；
- 告示牌实体纹理与静态物品模型；
- 合成配方与蜂蜜脾涂蜡配方；
- 方块掉落表；
- 楼梯、台阶、墙、栅栏、告示牌等原版标签；
- NeoForge `oxidizables` 与 `waxables` 数据映射；
- 生成清单 `patina_manifest.json`。

运行时会在下列目录创建两个始终启用的 ZIP 包：

```text
config/patina_pandemonium/generated-client-resources.zip
config/patina_pandemonium/generated-server-data.zip
```

客户端包仅包含 `assets/`，服务端包仅包含 `data/`，分别使用 26.1.2 的资源包与数据包格式声明。

## 告示牌

生成告示牌不是装饰性假方块：

- 使用原版 `StandingSignBlock`、`WallSignBlock` 和 `SignItem`；
- 通过 `BlockEntityTypeAddBlocksEvent` 把动态告示牌加入原版 `SIGN` 方块实体类型；
- 为每个“源方块 + 氧化阶段”建立动态 `WoodType`；
- 客户端初始化时调用 `Sheets.addWoodType` 注册对应渲染材质；
- 告示牌实体纹理由源方块贴图进行同样的算法着色并平铺为 64×32，不需要手绘。

## 开发环境

需要 Java 25。常用命令：

```text
./gradlew runData
./gradlew runClient
./gradlew runServer
./gradlew build
```

`runData` 输出到 `src/generated/resources`。运行时兼容包仍会保留，以覆盖开发时没有安装、但玩家启动时安装的兼容模组。

## 配置

第一次启动会写出：

```text
config/patina_pandemonium-rules.json
```

主要字段：

- `excludedNamespaces`：排除整个命名空间；
- `excludedBlocks`：排除指定源方块；
- `maximumGeneratedBlocks`：安全上限；
- `slabs`、`stairs`、`walls`、`fences`、`fenceGates`、`buttons`、`pressurePlates`、`signs`：制品开关；
- `textureOverrides`：复杂模型无法自动解析时，指定用于着色的源贴图；
- `existingFormOverrides`：非标准命名模组的现有制品映射。

贴图覆盖示例：

```json
{
  "textureOverrides": {
    "examplemod:machine_casing": "examplemod:block/machine/casing"
  }
}
```

现有制品覆盖键格式为：

```text
<源方块ID>|<fresh/exposed/weathered/oxidized>|<true/false>|<制品ID>
```

例如：

```json
{
  "existingFormOverrides": {
    "examplemod:polished_panel|fresh|false|stairs": "examplemod:panel_steps"
  }
}
```

## 兼容性边界

NeoForge 的静态方块注册表最终会冻结，因此无法在世界加载后再补注册方块。本项目在自身收到方块 `RegisterEvent` 时，以最低优先级扫描当前注册表；绝大多数按常规方式较早注册的内容都能被发现，但刻意晚于本监听器注册、使用非常规注册流程或完全非标准命名的模组，无法保证零配置覆盖。对这种情况可使用排除项、贴图覆盖和现有制品覆盖。

生成规模可能非常大。每个源方块理论上最多产生 8 个状态乘以 10 种形态；大型整合包应先降低制品开关或 `maximumGeneratedBlocks`，再逐步放开。
