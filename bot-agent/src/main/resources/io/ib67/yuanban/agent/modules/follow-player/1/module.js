// 持续跟随一名玩家，直到停止。focus 或 goal 消息中的 player 字段更换跟随对象；
// 看不到玩家时询问 supervisor，每次最多等 1 秒回答，之后重新查找。
export async function main(api, input) {
    let player = input.player;
    const distance = input.distance ?? 3;
    let following = 0;
    while (!api.cancelled()) {
        const control = api.inbox.poll({types: ['focus', 'goal', 'policy']});
        if (control !== null && control.payload.player) {
            player = control.payload.player;
            api.events.progress({step: 'target_changed', player});
        }
        const found = await api.attempt(api.observe.entities({type: 'minecraft:player', name: player, radius: 64, limit: 1}));
        if (!found.ok || found.value.entities.length === 0) {
            const reply = await api.askSupervisor('player_not_visible', {player}, {
                timeoutMs: 1000, dedupeKey: 'follow:not-visible:' + player
            });
            if (reply !== null && reply.type === 'stop') {
                break;
            }
            if (reply !== null && reply.payload.player) {
                player = reply.payload.player;
            }
            continue;
        }
        const target = found.value.entities[0];
        if (target.distance > distance + 1) {
            const moved = await api.attempt(api.actions.moveTo(target.position, {closeDistance: distance, timeoutMs: 15000}));
            if (moved.kind === 'CANCELLED' && api.cancelled()) {
                break;
            }
            if (moved.ok) {
                following++;
                if (following % 5 === 1) {
                    api.events.progress({step: 'following', player, position: target.position});
                }
            }
        } else {
            await api.attempt(api.actions.lookAt(target.id));
            await api.sleep(500);
        }
    }
    return {kind: 'stopped', player};
}
