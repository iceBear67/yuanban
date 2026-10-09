// background：空闲时看向附近的玩家。改变朝向需要调度器授予的短期 lease：先发 action_request，
// 得到授权后才调用 lookAt；没有授权时只观察。
export async function main(api, input) {
    const options = input ?? {};
    const intervalMs = options.intervalMs ?? 5000;
    let rounds = 0;
    let actions = 0;
    while (!api.cancelled()) {
        const found = await api.attempt(api.observe.entities({type: 'minecraft:player', radius: 16, limit: 1}));
        if (found.ok && found.value.entities.length > 0) {
            const player = found.value.entities[0];
            const grant = await api.events.actionRequest({
                tool: 'locomotion.lookAt', purpose: 'look at ' + (player.name ?? 'a player'), durationMs: 1500, timeoutMs: 1000
            });
            if (grant.granted) {
                const looked = await api.attempt(api.actions.lookAt(player.id));
                actions++;
                api.events.observation({lookedAt: player.name ?? null, ok: looked.ok});
            } else {
                api.events.observation({lookAtDenied: grant.reason ?? null});
            }
        }
        rounds++;
        if (options.rounds && rounds >= options.rounds) {
            break;
        }
        const message = await api.inbox.next({types: ['stop'], timeoutMs: intervalMs});
        if (message !== null) {
            break;
        }
    }
    return {kind: api.cancelled() ? 'stopped' : 'completed', actions};
}
