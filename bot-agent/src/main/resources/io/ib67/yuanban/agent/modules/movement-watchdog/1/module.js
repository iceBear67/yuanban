// 只读监听 primary 的移动，只订阅低成本信号：移动中长时间没有进展时发出 fault(stalled)，
// 受伤、生命过低与死亡时发出 fault(hazard)，世界重建时发出 attention(world_changed)。
// 收到 drain 时处理完剩余的信号，返回发现的问题。
export async function main(api, input) {
    const options = (input.primaryInput && input.primaryInput.watchdog) || {};
    const stallMs = options.stallMs ?? 8000;
    const lowHealth = options.lowHealth ?? 6;
    const findings = [];
    let moving = false;
    let lastPosition = null;
    let lastProgressAt = Date.now();
    let stalledReported = false;

    function report(kind, reason, data) {
        findings.push({kind, reason, data});
        api.events.fault({kind, reason, data, dedupeKey: 'watchdog:' + kind + ':' + reason});
    }

    function progressed() {
        lastProgressAt = Date.now();
        stalledReported = false;
    }

    function distance(a, b) {
        return Math.hypot(a.x - b.x, a.y - b.y, a.z - b.z);
    }

    function handle(message) {
        const payload = message.payload;
        switch (message.topic) {
            case 'primary_tool_start':
                if (payload.tool.startsWith('locomotion.')) {
                    moving = true;
                    progressed();
                }
                break;
            case 'primary_tool_end':
                if (payload.tool.startsWith('locomotion.')) {
                    moving = false;
                }
                break;
            case 'moved':
                if (lastPosition === null || distance(lastPosition, payload.position) >= 0.5) {
                    lastPosition = payload.position;
                    progressed();
                }
                break;
            case 'teleported':
                lastPosition = payload.to;
                progressed();
                break;
            case 'primary_progress':
                progressed();
                break;
            case 'damage':
                report('hazard', 'damage', payload);
                break;
            case 'health':
                if (payload.health < lowHealth && payload.health < payload.oldHealth) {
                    report('hazard', 'low_health', payload);
                }
                break;
            case 'player_died':
                report('hazard', 'player_died', payload);
                break;
            case 'world_entered':
                api.events.attention({kind: 'world_changed', data: payload, dedupeKey: 'watchdog:world:' + payload.epoch});
                break;
            default:
                break;
        }
    }

    while (true) {
        const message = await api.inbox.next({types: ['signal', 'drain', 'stop'], timeoutMs: 500});
        if (message === null) {
            if (moving && !stalledReported && Date.now() - lastProgressAt > stallMs) {
                stalledReported = true;
                report('stalled', 'no_progress', {sinceMs: Date.now() - lastProgressAt, position: lastPosition});
            }
            continue;
        }
        if (message.type === 'stop') {
            return {kind: 'stopped', findings};
        }
        if (message.type === 'drain') {
            let rest = api.inbox.poll({types: ['signal']});
            while (rest !== null) {
                handle(rest);
                rest = api.inbox.poll({types: ['signal']});
            }
            return {kind: 'drained', findings};
        }
        handle(message);
    }
}
