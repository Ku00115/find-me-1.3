# FindMe 1.3.0-test.3（测试版）

## 中文

- 新增独立的全队指令轮盘，可让当前伙伴编队执行跟随、保护、守卫、攻击、停止/恢复与全部收纳；坐骑轮盘不会误开全队指令。
- 完善伙伴战术控制：提高威胁选择与攻击意愿，保护和守卫使用分散站位，限制无意义跟随、模型堆叠和远距离目标干扰。
- 统一伙伴召唤与战术部署表现：普通召唤、单体战术和全队部署使用中心魔法阵、白色连接线、成员魔法阵及落位动画，并带有魔法阵开启与显现音效；紧急救援仍使用独立安全流程。
- 增加召唤实体描边开关及黑色/白色选择，并分别提供坐骑动画和伙伴动画开关。
- 增加“优先使用坐骑原生右键交互”设置，默认关闭，便于兼顾喂食、模组原生交互与 FindMe 骑乘行为。
- 修复设置页修改选项后自动滚动、仓库加入高编号编队时无法滚动、死亡仓库导航与睡眠复活、沉浸式对话开关等界面和交互问题。
- 修复缺失存档快照的战术部署反复重试、全队指令调度压力过高、重复诊断输出等问题；诊断日志仍由总开关控制。
- Forge 1.20.1 与 NeoForge 1.21.1 的网络协议、设置、指令、动画和测试已同步。

## English

- Added a dedicated team command wheel for Follow, Protect, Guard, Attack, contextual Pause/Resume, and Recall All. The mount wheel can no longer open team commands by mistake.
- Improved companion tactical control with stronger threat selection, distributed Protect/Guard positions, and safeguards against unwanted following, model stacking, and distant-target interference.
- Unified companion summon and tactical deployment presentation. Ordinary summons, single tactical deployments, and team deployments now use a center circle, white links, member circles, overhead arrival, and matching circle/reveal sounds. Emergency rescue remains an independent safety flow.
- Added summoned-entity outlines with black or white color selection, plus independent mount and companion summon-animation toggles.
- Added a disabled-by-default option to prefer a mount's native right-click interaction, allowing feeding and mod-native interactions when desired.
- Fixed settings scroll jumps, scrolling when assigning warehouse entries to later teams, death warehouse navigation and sleep revival, immersive dialogue settings, and related UI behavior.
- Prevented missing-snapshot tactical requests from retrying indefinitely, reduced team-command scheduling pressure and duplicate diagnostics, and kept all diagnostic output behind the master logging switch.
- Synchronized protocol, settings, commands, animation behavior, and regression tests across Forge 1.20.1 and NeoForge 1.21.1.

---

# FindMe 1.3.0-test.2（测试候选）

## 中文

- 修复首次绑定 Sable 载具并自动建立载具编队后，原轮盘载具可能从轮盘、编队或管理页隐藏的问题。
- 服务端现在将真正的完整载具集合发送给客户端；客户端也会防御性合并轮盘与完整列表，避免包顺序或不完整列表再次隐藏载具。
- 增加 `vehicle-roster` 诊断数量与首次自动编队回归测试。Forge 与 NeoForge 已同步并通过完整构建；NeoForge 候选版已部署等待游戏内验收。

## English

- Fixed existing wheel vehicles becoming hidden from the wheel, teams, or management after the first Sable vehicle binding creates an automatic vehicle team.
- The server now sends the actual complete vehicle roster, while the client defensively merges wheel entries into that roster so packet order or a partial list cannot hide vehicles again.
- Added `vehicle-roster` count diagnostics and a first-auto-team regression test. Forge and NeoForge are synchronized and pass full builds; the NeoForge candidate is deployed for in-game acceptance.
- Fixed stored entity vehicles refusing to start a cross-system handoff while the player was still riding the source. Immersive Aircraft now uses its native passenger relationship, and the source retires only after boarding is confirmed.
- NeoForge Sable handoff now warms up the restored sub-level, verifies moving-seat tracking for consecutive ticks, and only then retires the source. Sable UUID replacement is accepted by the roster transaction instead of waiting forever on the stored UUID.
- Added bounded `vehicle-handoff` diagnostics for boarding attempts, Sable warmup/tracking, commit, timeout, and rollback. The global diagnostic switch still controls these messages.

---

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
