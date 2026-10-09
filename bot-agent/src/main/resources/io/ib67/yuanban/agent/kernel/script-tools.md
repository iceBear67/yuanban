工具请求：`api.observe.*`、`api.actions.*`、`api.unsafe.send` 返回一个请求对象，请求在使用时才执行，一个请求只能执行一次：
- `await 请求`：成功时得到值，失败时抛出 ToolError（`e.kind`、`e.reason`、`e.result`）；
- `await api.attempt(请求, {observer?, recover?})`：得到完整结果 `{ok, kind, reason, actionId, elapsedMs, value, observation}`，不抛出；
- `await api.actions.withObserver(请求, observer 描述)`：带 observer 执行，成功时得到 observer 的结论。

注意：async 函数返回请求对象时，请求会被 Promise 当作 thenable 执行。需要传递未执行的请求时放进对象，例如 `{retry: 请求}`。

结果的 kind：OK、ACTION_CANCELLED（reason 为 BY_USER、NO_PATH、BLOCKED、NO_ENTITY、CHUNK_NOT_LOADED、WORLD_REBUILT、PLAYER_DIED）、
DISCONNECTED、BOT_FAILURE、TIMEOUT、CHUNK_NOT_LOADED、NOT_IN_WORLD、WAIT_TIMEOUT、ASSERTION_FAILED、WOULD_EXECUTE（dry-run，ok 为 true）、
INVALID_ARGUMENT、PERMISSION_DENIED、LEASE_REQUIRED、BUDGET_EXHAUSTED、RATE_LIMITED、DUPLICATE_SUPPRESSED（ok 为 true）、
UNCERTAIN_SIDE_EFFECT、LIMIT_EXCEEDED、RESULT_TOO_LARGE、CANCELLED（网关取消：停止、暂停 PAUSED、lease 变化）、REPLAY_MISMATCH。
ok 只表示调用与 observer 得到结论，不表示服务端执行了游戏效果；动作正常完成只代表请求走完。

观察：`playerSnapshot()`、`block(pos)`、`blocks(min, max, {onlyNonAir})`（最多 4096 个，未加载区块列在 unknownChunks）、
`entities({radius, type, name, limit})`、`entity(id)`、`chatLog({limit})`、`inventory()`、`world()`、`status()`、
`waitFor(condition, {timeoutMs})`、`assertState(condition)`。

移动（需要 lease）：`moveTo(pos, {mode, closeDistance, maxDistance})`、`moveForward(blocks, {direction: {x, z}, mode})`、
`jumpTo(pos, {mode})`、`rotate(yaw, pitch)`、`lookAt(pos 或实体编号)`。mode 为 WALK、SPRINT、SNEAK。

交互（需要 lease，按坐标与频率限流）：`breakBlock(pos, {face})`（默认带 blockBroken observer，值为 {broken, original, changedTo}）、
`placeBlock(against, face, {hand})`、`useItemOn(pos, face, {hand, cursor})`、`useItem({hand, holdTicks})`、`attack(id)`、`interact(id, {hand})`、
`selectHotbar(slot)`、`swapHands()`、`click(slot, mode, {hotbarSlot})`（mode 为 left、right、shift、swapHotbar）、`clickButton(id)`、
`closeWindow()`、`respawn()`。

高风险（按策略允许或拒绝）：`chat(message)`、`command(command)`、`drop(wholeStack)`、`dropSlot(slot, wholeStack)`、`configure(key, value)`。

每个请求的选项都可以带 `timeoutMs` 与 `idempotencyKey`。带 idempotencyKey 的副作用记入账本：完成过的直接得到 DUPLICATE_SUPPRESSED 与上次的结果，
开始过但没有记录结束的得到 UNCERTAIN_SIDE_EFFECT。

observer 描述（交给 withObserver 或 attempt 的 observer 选项）：`api.observe.movementReached(target, {distance})` → {reached, finalPosition, distance, minDistance}；
`blockBroken(pos)`；`blockAt(pos, 名称或名称数组)` → {met, current, changes}；`blockChanges(min, max, {limit})`；`damage({limit})`；
`chat({contains, limit})`；`entityMovement({type, id, atLeast, towards: {x, y, z, angle}, into: {min, max}, count})`；`pickups()`；`slots()`；
`windowOpened()`；`sounds(min, max)`；`particles(min, max)`。

条件（waitFor 与 assertState，只能是数据，不能是函数）：每个条件是只有一个键的对象：
`{all: [...]}`、`{any: [...]}`、`{not: c}`、`{near: {x, y, z, distance}}`、`{far: {...}}`、`{onGround: true}`、`{loaded: true}`、`{inWorld: true}`、
`{dead: false}`、`{health: {lt|lte|gt|gte|eq: n}}`、`{food: {...}}`、`{chunkLoaded: {x, z}}`（方块坐标）、
`{block: {x, y, z, is?: 名称或数组, air?, blocksMotion?, liquid?}}`、`{entityNear: {type?, name?, radius, count?}}`、`{entityExists: {id}}`、
`{windowOpen: true 或 {type}}`、`{holding: {item}}`、`{hasItem: {item, count?}}`、`{chatContains: {text, last?}}`、`{dimension: 名称}`、
`{gameMode: SURVIVAL|CREATIVE|ADVENTURE|SPECTATOR}`。

恢复：`api.recover(kind, async (result, {attempt, request}) => ..., {maxRetries, budgetMs})` 只能注册 CHUNK_NOT_LOADED、NO_PATH、BLOCKED、
NO_ENTITY、WAIT_TIMEOUT、TIMEOUT；`api.attempt` 遇到这些错误时运行处理函数后重试，处理函数返回 `{retry: 请求}` 时改用这个请求，返回 false 时放弃。
开始与用完时自动发出 recovery_started、recovery_exhausted 通知。
