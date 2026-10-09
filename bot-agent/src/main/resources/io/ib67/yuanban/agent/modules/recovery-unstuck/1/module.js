// recovery：从委托来的 lease 开始，依次朝四个方向直线移动一小段，再尝试跳到旁边的方块；
// 位置变化超过 1 格即为脱困。都不行时发出 fault(stuck)。
export async function main(api, input) {
    const start = (await api.observe.playerSnapshot()).position;
    const distance = (a, b) => Math.hypot(a.x - b.x, a.z - b.z);
    const directions = [{x: 1, z: 0}, {x: -1, z: 0}, {x: 0, z: 1}, {x: 0, z: -1}];
    for (const direction of directions) {
        if (api.cancelled()) {
            return {kind: 'stopped'};
        }
        await api.attempt(api.actions.moveForward(1.5, {direction, timeoutMs: 5000}));
        const now = (await api.observe.playerSnapshot()).position;
        if (distance(start, now) >= 1) {
            api.events.progress({step: 'unstuck', direction});
            return {kind: 'moved', from: start, to: now, direction};
        }
    }
    await api.attempt(api.actions.jumpTo({x: start.x + 1, y: start.y, z: start.z}, {timeoutMs: 5000}));
    const after = (await api.observe.playerSnapshot()).position;
    if (distance(start, after) >= 1) {
        api.events.progress({step: 'unstuck', direction: 'jump'});
        return {kind: 'moved', from: start, to: after, direction: 'jump'};
    }
    api.events.fault({kind: 'stuck', reason: 'no_direction_free', data: {position: start}});
    return {kind: 'stuck', position: after};
}
