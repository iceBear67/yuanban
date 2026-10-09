// 在游戏中发送一条聊天消息。聊天是高风险工具，按策略可能被拒绝；带 idempotency key，重启后不会重复发送。
export async function main(api, input) {
    const key = input.idempotencyKey ?? ('announce:' + input.message);
    const result = await api.attempt(api.actions.chat(input.message, {idempotencyKey: key}));
    if (result.kind === 'DUPLICATE_SUPPRESSED') {
        return {kind: 'already_sent'};
    }
    if (result.ok) {
        api.events.progress({step: 'sent'});
        return {kind: 'sent'};
    }
    if (result.kind === 'PERMISSION_DENIED') {
        api.events.attention({kind: 'denied', data: {message: input.message, result: result.kind}});
        return {kind: 'denied', reason: result.kind};
    }
    api.events.fault({kind: 'unexpected_state', reason: result.kind, data: {message: input.message}});
    return {kind: 'failed', reason: result.kind};
}
