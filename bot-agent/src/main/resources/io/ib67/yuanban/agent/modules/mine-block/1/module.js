// 挖掉 pos 处的方块：先读取方块，走到附近，带 idempotency key 挖掘，再用 assertState 确认变成空气。
// 挖掘只走完请求不代表方块被破坏：结论来自 blockBroken observer 与断言。
export async function main(api, input) {
    const pos = {x: Math.floor(input.pos.x), y: Math.floor(input.pos.y), z: Math.floor(input.pos.z)};
    const key = 'mine:' + pos.x + ',' + pos.y + ',' + pos.z;
    const before = await api.attempt(api.observe.block(pos));
    if (!before.ok) {
        api.events.fault({kind: 'unexpected_state', reason: before.kind, data: {pos}});
        return {kind: 'failed', reason: before.kind};
    }
    if (before.value.air) {
        return {kind: 'already_air', block: before.value.name};
    }
    const approach = await api.attempt(api.actions.moveTo({x: pos.x + 0.5, y: pos.y, z: pos.z + 0.5}, {closeDistance: 3}));
    if (!approach.ok && approach.kind !== 'ACTION_CANCELLED') {
        return {kind: 'failed', reason: approach.kind};
    }
    const mined = await api.attempt(api.actions.breakBlock(pos, Object.assign({idempotencyKey: key}, input.face ? {face: input.face} : {})));
    if (mined.kind === 'DUPLICATE_SUPPRESSED') {
        return {kind: 'broken', duplicate: true};
    }
    if (!mined.ok) {
        api.events.fault({kind: 'unexpected_state', reason: mined.kind, data: {pos, reason: mined.reason ?? null}});
        return {kind: 'failed', reason: mined.kind};
    }
    const check = await api.attempt(api.assertState({block: {x: pos.x, y: pos.y, z: pos.z, air: true}}));
    if (mined.value.broken && check.ok) {
        api.events.progress({step: 'broken', pos, original: mined.value.original});
        return {kind: 'broken', original: mined.value.original, changedTo: mined.value.changedTo};
    }
    api.events.attention({kind: 'not_broken', data: {pos, observer: mined.value, assertion: check.value ?? null}});
    return {kind: 'not_broken'};
}
