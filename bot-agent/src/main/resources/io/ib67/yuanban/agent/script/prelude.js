// 自治脚本的前导脚本：取走主机对象，构造交给入口函数的 api。求值结果为 {settle, boot}，只由 Java 端持有。
(function () {
    'use strict';
    const host = globalThis.__host;
    delete globalThis.__host;

    const pending = new Map();

    function settle(id, json, ok) {
        const entry = pending.get(id);
        if (!entry) {
            return;
        }
        pending.delete(id);
        const value = JSON.parse(json);
        if (ok) {
            entry.resolve(value);
        } else {
            entry.reject(new Error(value.error));
        }
    }

    function call(start) {
        return new Promise((resolve, reject) => {
            const id = start();
            pending.set(id, {resolve, reject});
        });
    }

    // 同步主机方法返回 {ok, value} 或 {ok: false, error}
    function sync(json) {
        const result = JSON.parse(json);
        if (!result.ok) {
            throw new Error(result.error);
        }
        return result.value;
    }

    class ToolError extends Error {
        constructor(result) {
            super(result.kind + (result.reason ? ': ' + result.reason : ''));
            this.name = 'ToolError';
            this.result = result;
            this.kind = result.kind;
            this.reason = result.reason;
        }
    }

    // 一个工具请求：直接 await 时执行，成功得到 value，失败抛出 ToolError；交给 attempt 时得到完整结果；交给 withObserver 时带上 observer。
    // 一个请求只能执行一次。
    class ToolRequest {
        constructor(tool, args) {
            this.tool = tool;
            this.args = Object.freeze(Object.assign({}, args || {}));
            this.executed = false;
        }

        run(observer) {
            if (this.executed) {
                throw new Error('tool request ' + this.tool + ' was already executed; create a new request');
            }
            this.executed = true;
            const observerJson = observer === undefined || observer === null ? '' : JSON.stringify(observer);
            return call(() => host.invoke(this.tool, JSON.stringify(this.args), observerJson));
        }

        fresh() {
            return new ToolRequest(this.tool, this.args);
        }

        then(onFulfilled, onRejected) {
            return this.run().then(result => {
                if (result.ok) {
                    return result.value;
                }
                throw new ToolError(result);
            }).then(onFulfilled, onRejected);
        }
    }

    function request(tool, args) {
        return new ToolRequest(tool, args);
    }

    function point(p) {
        if (p === null || typeof p !== 'object') {
            throw new TypeError('expected a point {x, y, z}');
        }
        return {x: p.x, y: p.y, z: p.z};
    }

    function merge(base, options) {
        return Object.assign(base, options || {});
    }

    let api;

    function notify(kind, payload) {
        return sync(host.notify(kind, JSON.stringify(payload === undefined ? {} : payload)));
    }

    // attempt / recover：只对白名单错误运行恢复，限制次数与时间
    const recoverable = new Set(['CHUNK_NOT_LOADED', 'NO_PATH', 'BLOCKED', 'NO_ENTITY', 'OUT_OF_RANGE', 'WAIT_TIMEOUT', 'TIMEOUT']);
    const handlers = new Map();

    function recoveryKey(result) {
        return result.kind === 'ACTION_CANCELLED' ? result.reason : result.kind;
    }

    function recover(kind, fn, options) {
        if (!recoverable.has(kind)) {
            throw new Error(kind + ' is not recoverable; recoverable errors: ' + Array.from(recoverable).join(', '));
        }
        if (typeof fn !== 'function') {
            throw new TypeError('recover expects a handler function');
        }
        const opts = options || {};
        handlers.set(kind, {fn, maxRetries: opts.maxRetries === undefined ? 2 : opts.maxRetries, budgetMs: opts.budgetMs || 0});
    }

    async function attempt(req, options) {
        if (!(req instanceof ToolRequest)) {
            throw new TypeError('attempt expects a tool request');
        }
        const opts = options || {};
        let current = req;
        let result = await current.run(opts.observer);
        const started = Date.now();
        let attempts = 0;
        while (!result.ok && opts.recover !== false) {
            const handler = handlers.get(recoveryKey(result));
            if (!handler) {
                break;
            }
            if (attempts >= handler.maxRetries || (handler.budgetMs > 0 && Date.now() - started > handler.budgetMs)) {
                notify('recovery_exhausted', {tool: current.tool, kind: result.kind, reason: result.reason, attempts});
                result = Object.assign({}, result, {recovery: {attempts, exhausted: true}});
                break;
            }
            attempts++;
            notify('recovery_started', {tool: current.tool, kind: result.kind, reason: result.reason, attempt: attempts});
            // 处理函数返回 {retry: 请求} 时改用这个请求重试（直接返回请求对象会被 Promise 当作 thenable 执行），
            // 返回 false 时放弃，其余情况重试原来的请求
            const next = await handler.fn(result, {attempt: attempts, request: current});
            if (next === false) {
                result = Object.assign({}, result, {recovery: {attempts, exhausted: false, abandoned: true}});
                break;
            }
            current = next && next.retry instanceof ToolRequest ? next.retry : current.fresh();
            result = await current.run(opts.observer);
            if (result.ok) {
                result = Object.assign({}, result, {recovery: {attempts, exhausted: false}});
            }
        }
        return result;
    }

    async function withObserver(req, observer) {
        if (!(req instanceof ToolRequest)) {
            throw new TypeError('withObserver expects a tool request');
        }
        const result = await req.run(observer);
        if (result.ok) {
            return result.value;
        }
        throw new ToolError(result);
    }

    function filter(options) {
        const opts = options || {};
        return {types: opts.types || [], replyTo: opts.replyTo || null, timeoutMs: opts.timeoutMs || 0};
    }

    const inbox = Object.freeze({
        next: options => call(() => host.inboxNext(JSON.stringify(filter(options)))),
        poll: options => JSON.parse(host.inboxPoll(JSON.stringify(filter(options))))
    });

    function emit(type, payload, extra) {
        const spec = Object.assign({payload: payload === undefined ? {} : payload}, extra || {});
        return sync(host.emit(type, JSON.stringify(spec)));
    }

    function attention(spec, type) {
        const s = spec || {};
        return emit(type, {kind: s.kind || null, data: s.data === undefined ? {} : s.data, evidence: s.evidence || []},
            {topic: s.kind || s.topic || null, dedupeKey: s.dedupeKey || null, priority: s.priority || null, expectReply: !!s.expectReply});
    }

    const events = Object.freeze({
        progress: payload => emit('progress', payload),
        attention: spec => attention(spec, 'attention'),
        question: spec => emit('question', {text: (spec || {}).text, choices: (spec || {}).choices || [], data: (spec || {}).data || {}},
            {topic: (spec || {}).topic || null, expectReply: true}),
        fault: spec => emit('fault', {kind: (spec || {}).kind, reason: (spec || {}).reason || null, data: (spec || {}).data || {}},
            {topic: (spec || {}).kind || null, dedupeKey: (spec || {}).dedupeKey || null, priority: 'high'}),
        result: payload => emit('result', payload),
        observation: payload => emit('observation', payload),
        interest: spec => attention(spec, 'interest'),
        suggestion: spec => emit('suggestion', {goal: (spec || {}).goal, reason: (spec || {}).reason || null, data: (spec || {}).data || {}}),
        // 请求短暂的 capability lease；得到 {granted, leaseId, expiresAt} 或 {granted: false, reason}
        actionRequest: async spec => {
            const id = emit('action_request', {tool: spec.tool, purpose: spec.purpose, durationMs: spec.durationMs}, {expectReply: true});
            const reply = await inbox.next({types: ['lease'], replyTo: id, timeoutMs: spec.timeoutMs || 10000});
            return reply === null ? {granted: false, reason: 'no reply'} : reply.payload;
        }
    });

    async function askSupervisor(topic, data, options) {
        const opts = options || {};
        const id = emit('attention', {kind: topic, data: data === undefined ? {} : data, evidence: opts.evidence || []},
            {topic, dedupeKey: opts.dedupeKey || null, priority: opts.priority || null, expectReply: true});
        return inbox.next({types: ['focus', 'policy', 'user_reply', 'stop'], replyTo: id, timeoutMs: opts.timeoutMs || 0});
    }

    const observe = Object.freeze({
        playerSnapshot: options => request('observe.playerSnapshot', options),
        block: (pos, options) => request('observe.block', merge(point(pos), options)),
        blocks: (min, max, options) => request('observe.blocks', merge({min: point(min), max: point(max)}, options)),
        entities: options => request('observe.entities', options),
        entity: (id, options) => request('observe.entity', merge({id}, options)),
        chatLog: options => request('observe.chatLog', options),
        inventory: options => request('observe.inventory', options),
        world: options => request('observe.world', options),
        status: options => request('observe.status', options),
        waitFor: (condition, options) => request('observe.waitFor', merge({condition}, options)),
        assertState: (condition, options) => request('observe.assertState', merge({condition}, options)),
        // observer 描述，交给 withObserver
        movementReached: (target, options) => merge({kind: 'movementReached', target: point(target)}, options),
        blockBroken: pos => ({kind: 'blockBroken', pos: point(pos)}),
        blockAt: (pos, is) => ({kind: 'blockAt', pos: point(pos), is}),
        blockChanges: (min, max, options) => merge({kind: 'blockChanges', min: point(min), max: point(max)}, options),
        damage: options => merge({kind: 'damage'}, options),
        chat: options => merge({kind: 'chat'}, options),
        entityMovement: options => merge({kind: 'entityMovement'}, options),
        pickups: options => merge({kind: 'pickups'}, options),
        slots: options => merge({kind: 'slots'}, options),
        windowOpened: () => ({kind: 'windowOpened'}),
        sounds: (min, max, options) => merge({kind: 'sounds', min: point(min), max: point(max)}, options),
        particles: (min, max, options) => merge({kind: 'particles', min: point(min), max: point(max)}, options)
    });

    const actions = Object.freeze({
        moveTo: (target, options) => request('locomotion.moveTo', merge(point(target), options)),
        moveForward: (blocks, options) => request('locomotion.moveForward', merge({blocks}, options)),
        jumpTo: (target, options) => request('locomotion.jumpTo', merge(point(target), options)),
        rotate: (yaw, pitch, options) => request('locomotion.rotate', merge({yaw, pitch}, options)),
        lookAt: (target, options) => request('locomotion.lookAt',
            merge(typeof target === 'number' ? {entityId: target} : point(target), options)),
        breakBlock: (pos, options) => request('interaction.breakBlock', merge(point(pos), options)),
        placeBlock: (against, face, options) => request('interaction.placeBlock', merge(Object.assign(point(against), {face}), options)),
        useItemOn: (pos, face, options) => request('interaction.useItemOn', merge(Object.assign(point(pos), {face}), options)),
        useItem: options => request('interaction.useItem', options),
        attack: (entityId, options) => request('interaction.attack', merge({entityId}, options)),
        interact: (entityId, options) => request('interaction.interact', merge({entityId}, options)),
        selectHotbar: (slot, options) => request('interaction.selectHotbar', merge({slot}, options)),
        swapHands: options => request('interaction.swapHands', options),
        click: (slot, mode, options) => request('interaction.click', merge({slot, mode}, options)),
        clickButton: (buttonId, options) => request('interaction.clickButton', merge({buttonId}, options)),
        closeWindow: options => request('interaction.closeWindow', options),
        respawn: options => request('interaction.respawn', options),
        chat: (message, options) => request('external.chat', merge({message}, options)),
        command: (command, options) => request('external.command', merge({command}, options)),
        drop: (wholeStack, options) => request('external.drop', merge({wholeStack: !!wholeStack}, options)),
        dropSlot: (slot, wholeStack, options) => request('external.dropSlot', merge({slot, wholeStack: !!wholeStack}, options)),
        configure: (key, value, options) => request('external.configure', merge({key, value}, options)),
        withObserver
    });

    const unsafe = Object.freeze({
        send: (packet, args, options) => request('unsafe.send', merge({packet, args: args || []}, options))
    });

    function asyncHost(method, op) {
        return args => call(() => host[method](op, JSON.stringify(args === undefined ? {} : args)));
    }

    const workspace = Object.freeze({
        module: Object.freeze({
            list: asyncHost('workspace', 'module.list'),
            get: asyncHost('workspace', 'module.get'),
            search: asyncHost('workspace', 'module.search'),
            test: asyncHost('workspace', 'module.test'),
            saveDraft: asyncHost('workspace', 'module.saveDraft'),
            start: asyncHost('bundle', 'run')
        }),
        memory: Object.freeze({
            read: asyncHost('workspace', 'memory.read'),
            write: asyncHost('workspace', 'memory.write')
        }),
        checkpoint: Object.freeze({
            latest: asyncHost('workspace', 'checkpoint.latest')
        })
    });

    const bundle = Object.freeze({
        run: asyncHost('bundle', 'run'),
        status: asyncHost('bundle', 'status'),
        send: asyncHost('bundle', 'send'),
        pausePrimary: asyncHost('bundle', 'pausePrimary'),
        resumePrimary: asyncHost('bundle', 'resumePrimary'),
        preflight: asyncHost('bundle', 'preflight'),
        recover: asyncHost('bundle', 'recover'),
        replan: asyncHost('bundle', 'replan'),
        cancel: asyncHost('bundle', 'cancel')
    });

    const ledger = Object.freeze({
        get: key => sync(host.ledger('get', key, 'null')),
        has: key => sync(host.ledger('get', key, 'null')) !== null,
        mark: (key, data) => sync(host.ledger('mark', key, JSON.stringify(data === undefined ? {} : data)))
    });

    function describe(error) {
        if (error instanceof ToolError) {
            return 'ToolError ' + JSON.stringify(error.result);
        }
        if (error && error.stack) {
            return String(error.stack);
        }
        return String(error);
    }

    function boot(module, entry, inputJson, selfJson, stateJson) {
        const input = JSON.parse(inputJson);
        const self = Object.freeze(JSON.parse(selfJson));
        const state = JSON.parse(stateJson);
        api = Object.freeze({
            input,
            self,
            state: () => state,
            cancelled: () => host.cancelled(),
            inbox,
            events,
            notify,
            checkpoint: (name, data) => sync(host.checkpoint(String(name), JSON.stringify(data === undefined ? null : data))),
            budget: limits => sync(host.budget(JSON.stringify(limits || {}))),
            sleep: ms => call(() => host.sleep(ms)),
            log: (...args) => host.log('info', args.map(arg => typeof arg === 'string' ? arg : JSON.stringify(arg)).join(' ')),
            observe,
            actions,
            unsafe,
            attempt,
            recover,
            withObserver,
            waitFor: (condition, options) => observe.waitFor(condition, options),
            assertState: condition => observe.assertState(condition),
            askSupervisor,
            ledger,
            workspace,
            bundle,
            ToolError
        });
        const main = module[entry];
        if (typeof main !== 'function') {
            host.fail('module does not export a function named ' + entry);
            return;
        }
        Promise.resolve()
            .then(() => main(api, input))
            .then(value => host.finish(JSON.stringify(value === undefined ? null : value)),
                error => host.fail(describe(error)));
    }

    return {settle, boot};
})();
