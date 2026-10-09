// 移动到目标并确认到达。乐观执行：直接寻路，由 movementReached observer 确认到达；
// 可恢复的错误在本地处理，暂停时等待恢复，尝试次数用完时发出 attention。
export async function main(api, input) {
    const target = input.target;
    const closeDistance = input.closeDistance ?? 1;
    const reachDistance = closeDistance + 0.75;
    const restored = api.state();
    let attemptsLeft = restored?.attemptsLeft ?? (input.maxAttempts ?? 3);

    api.recover('CHUNK_NOT_LOADED', async () => {
        await api.attempt(api.waitFor({chunkLoaded: {x: target.x, z: target.z}}, {timeoutMs: 10000}), {recover: false});
    }, {maxRetries: 2});
    api.recover('BLOCKED', async () => {
        await api.sleep(500);
    }, {maxRetries: 2});
    api.recover('NO_PATH', async (result, ctx) => {
        // 先走到朝向目标的较近一点，之后的循环再走向目标
        const here = await api.observe.playerSnapshot();
        const fraction = Math.pow(0.5, ctx.attempt);
        return {
            retry: api.actions.moveTo({
                x: here.position.x + (target.x - here.position.x) * fraction,
                y: target.y,
                z: here.position.z + (target.z - here.position.z) * fraction
            }, {closeDistance: 1})
        };
    }, {maxRetries: 2});

    while (!api.cancelled() && attemptsLeft > 0) {
        const control = api.inbox.poll({types: ['focus', 'policy', 'bot_event']});
        if (control !== null && control.type === 'bot_event' && control.topic === 'player_died') {
            api.events.fault({kind: 'hazard', reason: 'player_died', data: control.payload});
            return {kind: 'failed', reason: 'PLAYER_DIED'};
        }
        const result = await api.attempt(
            api.actions.moveTo(target, {closeDistance, maxDistance: input.maxDistance ?? 50}),
            {observer: api.observe.movementReached(target, {distance: reachDistance})});
        if (result.ok && result.value.reached) {
            api.events.progress({step: 'arrived', position: result.value.finalPosition});
            return {kind: 'arrived', position: result.value.finalPosition};
        }
        if (result.kind === 'CANCELLED') {
            if (api.cancelled()) {
                break;
            }
            // 被 supervisor 暂停：下一次调用等到恢复，不计入尝试次数
            continue;
        }
        if (result.kind === 'ACTION_CANCELLED' && (result.reason === 'WORLD_REBUILT' || result.reason === 'PLAYER_DIED')) {
            api.events.fault({kind: 'hazard', reason: result.reason, data: {target}});
            return {kind: 'failed', reason: result.reason};
        }
        if (result.kind === 'DISCONNECTED' || result.kind === 'BOT_FAILURE') {
            api.events.fault({kind: 'bot_ended', reason: result.kind, data: {target}});
            return {kind: 'failed', reason: result.kind};
        }
        attemptsLeft--;
        api.checkpoint('after-attempt', {attemptsLeft});
        api.events.progress({step: 'retry', kind: result.kind, reason: result.reason ?? null, attemptsLeft});
    }
    if (api.cancelled()) {
        return {kind: 'stopped'};
    }
    api.events.attention({kind: 'unreachable', data: {target}, dedupeKey: 'travel:unreachable'});
    return {kind: 'failed', reason: 'UNREACHABLE'};
}
