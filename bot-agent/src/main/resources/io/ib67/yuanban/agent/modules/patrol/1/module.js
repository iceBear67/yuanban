// 按顺序巡逻 points 中的路点，laps 圈之后结束（laps 为 0 时一直巡逻到停止）。
// 每个路点之后写检查点（下一个路点与圈数），重启时从检查点继续；路点到不了时发出 attention(unexpected_state)。
export async function main(api, input) {
    const restored = api.state();
    let index = restored?.index ?? 0;
    let lap = restored?.lap ?? 0;
    const laps = input.laps ?? 1;
    while (!api.cancelled() && (laps === 0 || lap < laps)) {
        const point = input.points[index];
        const result = await api.attempt(api.actions.moveTo(point, {closeDistance: 1}),
            {observer: api.observe.movementReached(point, {distance: 2})});
        if (result.kind === 'CANCELLED' && api.cancelled()) {
            break;
        }
        if (result.ok && result.value.reached) {
            api.events.progress({step: 'waypoint', index, lap});
        } else {
            api.events.attention({
                kind: 'unexpected_state',
                data: {point, index, result: {kind: result.kind, reason: result.reason ?? null}},
                dedupeKey: 'patrol:waypoint:' + index
            });
        }
        index = (index + 1) % input.points.length;
        if (index === 0) {
            lap++;
        }
        api.checkpoint('waypoint', {index, lap});
        if (input.pauseMs) {
            await api.sleep(input.pauseMs);
        }
    }
    return {kind: api.cancelled() ? 'stopped' : 'completed', laps: lap, next: index};
}
