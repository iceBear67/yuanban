// 一次性的只读检查：目标附近的地形、危险方块、落差，以及玩家到目标的直线上区块是否已加载。
// 已知安全、未知与已知危险分开返回；unknown 表示尚未证明安全，不能当作安全。
const DANGEROUS = new Set([
    'minecraft:lava', 'minecraft:fire', 'minecraft:soul_fire', 'minecraft:magma_block', 'minecraft:cactus',
    'minecraft:sweet_berry_bush', 'minecraft:powder_snow', 'minecraft:campfire', 'minecraft:soul_campfire',
    'minecraft:wither_rose', 'minecraft:pointed_dripstone'
]);

export async function main(api, input) {
    const target = input.target ?? (input.primaryInput && input.primaryInput.target);
    if (!target) {
        return {known: [], unknown: [{fact: 'target', reason: 'no target given'}], hazards: [], safe: false};
    }
    const known = [];
    const unknown = [];
    const hazards = [];
    const player = await api.observe.playerSnapshot();
    known.push({fact: 'player_position', value: player.position});

    const tx = Math.floor(target.x);
    const ty = Math.floor(target.y);
    const tz = Math.floor(target.z);
    const area = await api.attempt(api.observe.blocks({x: tx - 2, y: ty - 4, z: tz - 2}, {x: tx + 2, y: ty + 2, z: tz + 2}));
    if (!area.ok) {
        unknown.push({fact: 'target_area', reason: area.kind});
    } else {
        if (!area.value.complete) {
            unknown.push({fact: 'target_area', reason: 'CHUNK_NOT_LOADED', chunks: area.value.unknownChunks});
        }
        const blocks = new Map(area.value.blocks.map(block => [block.pos.x + ',' + block.pos.y + ',' + block.pos.z, block]));
        const at = (x, y, z) => blocks.get(x + ',' + y + ',' + z);
        for (const block of area.value.blocks) {
            if (DANGEROUS.has(block.name)) {
                hazards.push({kind: 'hazard_block', block: block.name, pos: block.pos});
            }
        }
        const feet = at(tx, ty, tz);
        const head = at(tx, ty + 1, tz);
        if ((feet && feet.blocksMotion) || (head && head.blocksMotion)) {
            hazards.push({kind: 'obstructed_target', feet: feet && feet.name, head: head && head.name});
        }
        let drop = 0;
        for (let y = ty - 1; y >= ty - 4; y--) {
            const block = at(tx, y, tz);
            if (block === undefined) {
                break;
            }
            if (block.blocksMotion) {
                break;
            }
            drop++;
        }
        if (drop >= 3) {
            hazards.push({kind: 'drop', depth: drop, pos: {x: tx, y: ty, z: tz}});
        } else if (area.value.complete) {
            known.push({fact: 'ground_below_target', drop});
        }
    }

    // 直线上每隔 8 格检查区块是否已加载
    const dx = target.x - player.position.x;
    const dz = target.z - player.position.z;
    const length = Math.hypot(dx, dz);
    const steps = Math.max(1, Math.ceil(length / 8));
    for (let i = 1; i <= steps; i++) {
        const x = player.position.x + dx * i / steps;
        const z = player.position.z + dz * i / steps;
        const check = await api.attempt(api.observe.assertState({chunkLoaded: {x, z}}));
        if (!check.ok) {
            unknown.push({fact: 'route_chunk', x: Math.floor(x), z: Math.floor(z), reason: 'CHUNK_NOT_LOADED'});
        }
    }
    return {target, known, unknown, hazards, safe: hazards.length === 0 && unknown.length === 0};
}
