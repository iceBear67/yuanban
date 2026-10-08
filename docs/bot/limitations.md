# 限制与待办

[目录](README.md) · 上一篇：[测试](testing.md)

本篇目录：

- [不实现的功能](#不实现的功能)
- [不覆盖的移动](#不覆盖的移动)
- [其它不覆盖的客户端行为](#其它不覆盖的客户端行为)
- [对 Minestom 的绕过方法](#对-minestom-的绕过方法)
- [待办](#待办)

## 不实现的功能

| 功能 | 现在的处理 |
|---|---|
| 正版验证与加密 | 收到 Encryption Request 时 bot 以 `UnsupportedOperationException` 结束；服务端必须使用离线模式 |
| 聊天签名 | Chat Message 与 Chat Command 不签名，不发送 Player Session，不处理服务端的安全聊天要求 |
| 下载资源包 | 按 `ResourcePackPolicy` 回应接受或拒绝，不下载 |
| SRV 解析 | `TcpTransport` 直接连接给出的地址与端口 |
| 进程内连接 | 由外部模块实现 `Transport` |
| 按键输入、客户端时钟 | 公开接口不提供；Player Input 由移动状态派生，tick 时钟只在调度器内部 |
| 客户端的距离检查 | 攻击、交互、挖掘的距离由服务端检查 |
| 预测交互结果 | 物品栏点击的结果、物品能否使用、放置是否成功都不预测，模型只由服务端数据更新 |

## 不覆盖的移动

移动与碰撞使用 Minestom 的 `PhysicsUtils`，它是为服务端实体编写的，以下原版玩家移动行为不覆盖：

- 台阶抬升（原版玩家走上 0.6 格以内的台阶不需要跳跃）：半砖、楼梯、地毯等任何高于脚部的表面都要跳上去。`moveTo` 按节点的站立高度起跳；`moveForward` 不跳跃，遇到台阶被阻挡。
- 潜行时在方块边缘停住。
- 头顶空间不足时被迫蹲下或者爬行。
- 水与熔岩中的阻力、浮力与游泳，以及浅水中不能开始疾跑的限制。
- 区块未加载时原版改为以 0.1 的速度下落；本库不执行模拟并取消移动动作。
- 落到地面与撞墙时的反弹（`minecraft:bounciness` 属性、粘液块与床）。
- 连续两次按下跳跃键切换飞行状态。
- 攀爬梯子和藤蔓。
- 蜘蛛网与细雪的减速。
- 鞘翅滑翔。
- 与实体的碰撞。
- 控制载具时载具自身的移动模拟（Move Vehicle 发送的是模型中服务端给出的载具位置）。

连接原版服务端时，这些差异可能触发它的「移动错误」检查；通过这项检查的优先级低。另外，原版服务端会在玩家悬空 80 tick 之后踢出玩家（允许飞行的情况除外），暂停移动时要注意这一点，Minestom 没有这项检查。

## 其它不覆盖的客户端行为

- 其它实体的位置不插值，模型保存服务端最后给出的值；实体的药水效果不随 tick 递减。
- 实体包围盒不覆盖幼年与姿势以外随状态变化的尺寸：史莱姆一类与幻翼的大小、河豚的膨胀、鲑鱼的大小、成年骆驼坐下、山羊跳跃、北极熊站立、嗅探兽与监守者挖掘、潜影贝、盔甲架的标记、交互实体，以及末影龙、快乐恶魂、潜影贝对 scale 属性的限制。
- 骑乘炽足兽时乘客的上下起伏。
- 实体每 tick 的步骤只模拟马类的人立动画。
- 移动方向按期望方向向量直接施加加速度，斜向行走的速度与向前行走相同。

## 对 Minestom 的绕过方法

以下位置绕过了 Minestom `2026.10.05-26.2` 的数据（`net.minestom:data` 的 JSON）缺少的内容，代码标注 `FIXME(Minestom M12)`，问题记录在根目录的 [`MINESTOM-BUG-REPORT.md`](../../MINESTOM-BUG-REPORT.md)。Minestom 提供对应数据之后应当删除这些绕过。

| 位置 | 原因 | 绕过方法 | 限制 |
|---|---|---|---|
| [`movement/RidingSeat.java`](../../bot/src/main/java/io/ib67/yuanban/bot/movement/RidingSeat.java)、[`entity/EntityDimensions.java`](../../bot/src/main/java/io/ib67/yuanban/bot/entity/EntityDimensions.java)（M12） | 实体类型数据只有成年实体默认姿态的尺寸与挂点，没有幼年、坐下姿态与玩家各姿势的尺寸和挂点 | 骑乘位置内置原版 26.2 各载具（马、骷髅马、僵尸马、驴、骡、羊驼、猪、炽足兽、鹦鹉螺、鸡、骆驼、快乐恶魂）的幼年与坐下姿态的取值；实体包围盒内置原版 26.2 全部可以是幼年的实体的幼年尺寸，以及玩家与人体模型各姿势的尺寸 | 只对 26.2 正确；骑乘位置只识别这些载具类型的幼年状态；包围盒不覆盖其它随状态变化的尺寸 |

## 待办

以下问题暂不处理。代码中的位置标注 `TODO(T<编号>)`，[`bot/README.md`](../../bot/README.md) 的「待办」表格登记同样的内容。

| 编号 | 位置 | 问题 | 现在的处理 |
|---|---|---|---|
| T1 | [`world/SectionRef.java`](../../bot/src/main/java/io/ib67/yuanban/bot/world/SectionRef.java) 的 `biome` | MCProtocolLib 的 `DataPalette.index` 对 4×4×4 的生物群系调色板算错下标：用 `y << 3 \| z << 1 \| x`，应为 `y << 4 \| z << 2 \| x` | 不处理，生物群系照常用 `DataPalette.get` 读取。一个区段中只有一种生物群系时结果正确 |
| T2 | [`protocol/ProtocolMachine.java`](../../bot/src/main/java/io/ib67/yuanban/bot/protocol/ProtocolMachine.java) 的 `playerChat` | MCProtocolLib 的 `ClientboundPlayerChatPacket` 在过滤类型为 PARTIALLY_FILTERED 时不读取过滤标记的 BitSet，之后的聊天类型、发送者名称与目标名称都从错误的位置读出 | 不支持。收到部分过滤的玩家聊天时抛出 `UnsupportedOperationException`，bot 以这个异常结束 |
| T3 | [`data/Items.java`](../../bot/src/main/java/io/ib67/yuanban/bot/data/Items.java) 的 `sameItemSameComponents` | 原版解码物品时去掉补丁中与默认组件相同的条目，MCProtocolLib 保留原始补丁 | 物品按原始补丁比较，null 补丁与空补丁视为相同；服务端发来与默认值相同的补丁条目时，比较结果与原版不同。影响挖掘中判断主手物品是否改变，以及物品栏判断槽位是否改变 |
| T4 | [`interaction/BlockPredicates.java`](../../bot/src/main/java/io/ib67/yuanban/bot/interaction/BlockPredicates.java) | `can_break` 中针对方块实体组件的条件（exact components 与 component predicates） | 不支持。该位置有方块实体时，遇到带有组件条件的谓词抛出 `UnsupportedOperationException`；没有方块实体时与原版相同，直接不匹配 |

[目录](README.md) · 上一篇：[测试](testing.md)
