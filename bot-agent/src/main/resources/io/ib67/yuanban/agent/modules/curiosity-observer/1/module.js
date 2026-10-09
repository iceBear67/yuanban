// background：定期观察附近的实体，只读。第一次看到的玩家与靠近的敌对生物作为 interest（带证据引用）交给 supervisor，
// 其余观察只写入 trace。见过的玩家写入检查点，重启后不重复报告。
const HOSTILE = new Set([
    'minecraft:zombie', 'minecraft:husk', 'minecraft:drowned', 'minecraft:skeleton', 'minecraft:stray',
    'minecraft:creeper', 'minecraft:spider', 'minecraft:cave_spider', 'minecraft:witch', 'minecraft:enderman',
    'minecraft:pillager', 'minecraft:vindicator', 'minecraft:phantom', 'minecraft:slime', 'minecraft:blaze'
]);

export async function main(api, input) {
    const options = input ?? {};
    const intervalMs = options.intervalMs ?? 3000;
    const radius = options.radius ?? 24;
    const restored = api.state();
    const seen = new Set(restored?.seen ?? []);
    let rounds = 0;
    while (!api.cancelled()) {
        const result = await api.attempt(api.observe.entities({radius, limit: 64}));
        if (result.ok) {
            let players = 0;
            let hostiles = 0;
            for (const entity of result.value.entities) {
                if (entity.type === 'minecraft:player') {
                    players++;
                    if (entity.name && !seen.has(entity.name)) {
                        seen.add(entity.name);
                        api.events.interest({
                            kind: 'new_player',
                            data: {name: entity.name, position: entity.position, distance: entity.distance},
                            evidence: ['entity:' + entity.id],
                            dedupeKey: 'curiosity:player:' + entity.name
                        });
                    }
                } else if (HOSTILE.has(entity.type)) {
                    hostiles++;
                    if (entity.distance <= 8) {
                        api.events.interest({
                            kind: 'hostile_nearby',
                            data: {type: entity.type, id: entity.id, distance: entity.distance},
                            evidence: ['entity:' + entity.id],
                            dedupeKey: 'curiosity:hostile:' + entity.id
                        });
                    }
                }
            }
            api.events.observation({entities: result.value.total, players, hostiles});
        }
        rounds++;
        if (rounds % 10 === 0) {
            api.checkpoint('seen', {seen: Array.from(seen)});
        }
        if (options.rounds && rounds >= options.rounds) {
            break;
        }
        const message = await api.inbox.next({types: ['stop', 'focus', 'policy'], timeoutMs: intervalMs});
        if (message !== null && message.type === 'stop') {
            break;
        }
    }
    return {kind: api.cancelled() ? 'stopped' : 'completed', seen: Array.from(seen)};
}
