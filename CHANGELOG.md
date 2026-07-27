# FindMe 1.3.0-test.1

## 中文

### 发布说明

FindMe 1.3 经历了大量功能扩展与多次重构。本次作为测试版本发布，方便玩家和
整合包作者验证实际兼容情况。1.3 的稳定性和模组兼容性可能不如旧版，安装或
升级前必须备份存档。

### 主要内容

- 重做坐骑、伙伴和载具的收纳、召唤、切换与状态管理。
- 增加经典圆形、六翼、横向战术和折叠碎片轮盘样式；默认使用经典圆形轮盘。
- 增加待选、已召唤、切换中和死亡状态，以及编队、仓库和分页管理。
- 增加坠落救援、伙伴战术指令、回家流程和骑行视角设置。
- 增加小窝居民、巡逻范围、无人区块收纳和重新加载恢复。
- 重做 ApricityUI 管理、设置、详细、诊断、备份恢复和实体编辑界面。
- 增加实体分类、移动方式、绑定条件、动画、特效、预览 NBT 和尺寸覆盖配置。
- 增加自动备份、手动备份、死亡记录和诊断日志开关。
- 精简聊天指令为管理、编辑器、重载和管理员强制驯服。
- 补充名纸、载具绑定器和小窝配方；NeoForge 版另有定位坐垫。
- 分离并标明 Forge 1.20.1 与 NeoForge 1.21.1 测试构建。

### 平台差异

- NeoForge 1.21.1 包含 Cobblemon、Sable/航空学、MachineMax 和 Waystones 的可选联动入口。
- Forge 1.20.1 不包含 Cobblemon 与 Sable 专用功能，保留适用于该版本的 Waystones、
  MachineMax 及生物兼容处理。

### 已知风险

- 自定义 AI、骑乘控制器、分段模型或特殊序列化的模组生物可能无法完整兼容。
- 救援、载具切换、跨维度回家和实体预览是兼容风险较高的功能。
- 不要在 FindMe 仍保存相关记录时移除对应生物或载具模组。
- 更换版本或配置前，请先收纳已放出的对象并创建手动备份。

## English

### Release note

FindMe 1.3 went through substantial feature expansion and repeated refactoring. This
build is published as a test release so players and pack authors can validate it in
real modpacks. It may be less stable or compatible than earlier releases. Back up
your world before installing or upgrading.

### Highlights

- Reworked mount, companion, and vehicle storage, summon, switch, and lifecycle flows.
- Added classic radial, six-wing, tactical strip, and folded-shard wheel styles; the
  classic radial wheel is the default.
- Added pending, deployed, switching, and dead states with teams, warehouse, and paging.
- Added fall rescue, tactical companion commands, ride-home flow, and riding camera settings.
- Added Small House residents, patrol boundaries, unloaded-area storage, and restoration.
- Reworked ApricityUI management, settings, details, diagnostics, backups, and entity editor.
- Added entity category, movement, binding, animation, effect, preview NBT, and scale overrides.
- Added automatic/manual backups, death records, and an opt-in diagnostic logging switch.
- Reduced chat commands to management, editor, reload, and administrator force-tame tools.
- Added recipes for Name Paper, Vehicle Binder, and Small House; NeoForge also has a Seat Cushion.
- Published separately identified Forge 1.20.1 and NeoForge 1.21.1 test builds.

### Platform differences

- NeoForge 1.21.1 exposes optional Cobblemon, Sable/Aeronautics, MachineMax, and Waystones integrations.
- Forge 1.20.1 does not include Cobblemon or Sable-specific features; it retains integration
  paths appropriate to that version, including Waystones and MachineMax.

### Known risks

- Modded entities with custom AI, riding controllers, multipart renderers, or unusual
  serialization may not be fully compatible.
- Rescue, vehicle handoff, cross-dimension ride-home, and entity previews carry higher risk.
- Do not remove a creature or vehicle mod while FindMe records still depend on it.
- Store deployed entries and create a manual backup before changing versions or configuration.
