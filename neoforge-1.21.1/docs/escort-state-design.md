# FindMe Escort State Design

本文档用于约束后续“护航/跟随”功能，目标是让玩家骑着一个坐骑时，可以让另一个已绑定生物跟随飞行或移动，同时不破坏 FindMe 现有召唤、收纳、小窝、救援、技能表演等状态。

## 命名

功能名暂定为 `Escort`，中文可以叫“护航”。

不要直接叫 `follow`，因为它容易和原版宠物 owner-follow AI、mod 生物自身跟随 AI、救援追敌 AI 混在一起。

## 核心原则

- 护航生物必须是真实实体，不是通过 `FindMeTemporaryActionController` 注册的附属临时 performer。
- 护航状态只表示“FindMe 正在把这个已部署生物当作玩家护航单位管理”。
- 护航不应该改变生物的永久 NBT，不保存临时 `NoAI`、`NoGravity`、速度、隐身、坐下、目标等状态。
- 护航不应该绕过现有绑定/所有权/死亡/存储保护。
- 第一版最多允许每个玩家 1 个护航生物，后续再扩展为 2 个或编队护航。

## 状态定义

| 状态 | 含义 | 是否真实实体 | 是否可持久化 |
| --- | --- | --- | --- |
| Stored | 生物只存在于玩家数据的 stored entity NBT 中 | 否 | 是 |
| Deployed | 生物被真实放在世界里 | 是 | 是，记录 UUID/last known |
| Riding | 玩家当前骑乘该生物 | 是 | 是，属于 deployed 的特殊情况 |
| HomeResident | 小窝附近的临时居家实体 | 是 | 只保存 home 数据，不保存 resident 临时状态 |
| ArrivalPending | 召唤/救援入场动画尚未完成 | 是 | 否 |
| StoragePending | 收纳/回家动画正在处理 | 是 | 否 |
| MountCinematic | 坐骑救援/切换电影流程 | 是 | 否 |
| CombatRescue | 伴侣救援/紧急救援控制 | 是 | 否 |
| SkillPerformer | 技能用临时表演实体 | 是 | 否，绝不能进入玩家数据 |
| Escort | 护航状态，跟随玩家或玩家当前坐骑 | 是 | 可以保存 escort UUID，但不保存运动临时状态 |

## 冲突矩阵

| 当前状态 | 能否进入 Escort | 处理方式 |
| --- | --- | --- |
| Stored | 可以 | 先普通召唤/部署，入场完成后进入 Escort |
| Deployed | 可以 | 直接进入 Escort |
| Riding | 不可以 | 当前骑乘坐骑不能同时护航 |
| HomeResident | 可以但需要转换 | 先解除 resident 语义，按普通部署实体进入 Escort |
| ArrivalPending | 暂缓 | 入场完成后再进入 Escort |
| StoragePending | 不可以 | 等收纳完成；若玩家强制选择则取消 Escort 请求 |
| MountCinematic | 不可以 | 坐骑救援/切换优先，Escort 不介入 |
| CombatRescue | 不可以 | 战斗救援优先，Escort 等救援结束 |
| SkillPerformer | 永远不可以 | 技能 performer 是临时实体，必须忽略 |
| DeadRecord | 不可以 | 需要先走死亡/复活逻辑 |

## 被其他系统打断时的规则

| 事件 | Escort 行为 |
| --- | --- |
| 玩家主动收纳护航生物 | 取消 Escort，然后收纳 |
| 玩家删除绑定 | 取消 Escort，然后删除 |
| 玩家设为小窝 home | 不自动取消；只有回家/收纳时取消 |
| 小窝召回或自动回家 | 取消 Escort |
| 玩家切换维度 | 第一版取消 Escort 并收纳或重新召唤，避免跨维度丢实体 |
| 玩家死亡/退出 | 取消 Escort，按现有 logout/death 收纳规则处理 |
| 生物死亡 | 取消 Escort，进入现有死亡记录 |
| 触发坐骑救援/切换 | 如果护航生物不是目标坐骑，可以继续；如果它参与 cinematic，则取消 Escort |
| 触发伴侣/紧急救援 | 若护航生物被选为救援者，暂时暂停 Escort，救援结束后可恢复 |
| 触发主动技能 | 如果用护航生物作为技能来源，不移动真实护航实体；仍创建 skill performer |

## 第一版功能范围

- 每个玩家最多 1 个 Escort UUID。
- 支持 mount 和 companion，不支持 vehicle。
- UI 可以先从管理界面右键菜单加“护航/取消护航”，之后再接编队 UI。
- 如果目标生物是 stored，先走普通召唤，召唤成功后设置 Escort。
- 如果目标生物已经 deployed 且不是当前骑乘实体，直接设置 Escort。
- 如果目标生物在小窝附近，先按普通部署实体处理，不保留居家 AI。
- Escort 不参与当前坐骑/载具的“只能出现一个”规则；它是额外护航单位，但第一版限制为 1 个，避免性能和状态爆炸。

## 移动策略

### 通用目标点

护航目标不是玩家脚下，而是玩家或当前骑乘实体周围的偏移点。

- 玩家没有骑乘：目标点在玩家后侧 3-5 格。
- 玩家骑乘陆地坐骑：目标点在玩家坐骑侧后方 4-7 格。
- 玩家骑乘飞行坐骑/载具：目标点在玩家当前坐骑侧后方并略高 2-5 格。

偏移方向要稳定，不要每 tick 随机换边。

### 距离分层

| 距离 | 行为 |
| --- | --- |
| 近距离 | 不强控，只停攻击目标或轻量修正 |
| 中距离 | 使用导航、MoveControl 或速度朝目标点移动 |
| 远距离 | 快速靠近，但不要每 tick teleport |
| 极远/卡住 | 安全重定位到玩家附近，然后继续 Escort |

### AI 控制策略

- 默认不硬禁 AI。
- 每 tick 可以停导航目标、清攻击目标、清 aggressive，避免打架。
- 对需要飞行动画的生物，保持 `NoAI=false`。
- 对会被 owner-follow 拉走的特殊生物，可以在配置/兼容表里启用强控制。
- 不把 Escort 的临时 `NoAI`/`NoGravity` 写入 stored NBT。

## 服务边界

建议新增：

- `CompanionEscortService`
- `PendingEscort` 或内部记录结构

建议 facade：

- `CompanionManager.isEscorting(UUID uuid)`
- `CompanionManager.startEscort(ServerPlayer player, CompanionKind kind, UUID uuid)`
- `CompanionManager.cancelEscort(ServerPlayer player, UUID uuid, EscortCancelReason reason)`

建议数据：

- `PlayerCompanionData.escortUuid`
- 如果后续支持多个护航，再升级为列表或编队字段。

## Tick 顺序建议

`CompanionEvents` 里 Escort tick 应该排在：

1. storage/home/arrival 状态检查之后
2. mount cinematic 和 combat rescue 之后
3. 普通 sync 之前

理由：救援/切换/收纳属于更高优先级，Escort 只是普通跟随状态。

## 第一版验收清单

- 玩家骑着龙时，可以让另一只龙跟随。
- 玩家骑乘陆地坐骑时，护航生物不会撞到玩家或挡视野。
- 护航生物不会被误收纳、误判成当前坐骑、误进入技能 performer 数据。
- 收纳、删除、死亡、退出、换维度后不会残留 Escort 状态。
- 小窝 resident 转 Escort 后，不会同时保持 home resident 标记。
- 主动技能使用护航生物时，真实护航实体不消失、不瞬移，技能仍使用临时 performer。
- 大型/多部位飞行生物不会每 tick teleport 抽搐。
- 多人情况下，A 玩家护航状态不会影响 B 玩家同类生物。

## 暂不做

- 多个护航生物。
- 护航编队阵型。
- 护航自动战斗。
- 护航跨维度无缝追随。
- 护航载具。
- 护航状态保存复杂运动参数。
