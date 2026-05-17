<script lang="ts">
    import type { TransactionEvent, TransactionTriple } from '$lib/mazeState.svelte';

    let {
        events,
        filterText = '',
        hasMore = false,
        isLoadingMore = false,
        onLoadMore,
        totalCount = 0,
        hotCount = 0,
        coldCount = 0,
        archiveRunId = ''
    } = $props<{
        events: TransactionEvent[];
        filterText?: string;
        hasMore?: boolean;
        isLoadingMore?: boolean;
        onLoadMore?: () => void | Promise<void>;
        totalCount?: number;
        hotCount?: number;
        coldCount?: number;
        archiveRunId?: string;
    }>();

    let expandedRowKey = $state<string | null>(null);
    let autoLoadMore = $state(false);

    const normalizedFilterText = $derived(filterText.trim().toLowerCase());

    const filteredEvents = $derived.by(() => {
        if (!normalizedFilterText) {
            return events;
        }

        return events.filter((event: TransactionEvent) => {
            const agent = (event.agent ?? '').toLowerCase();
            const graph = (event.graph ?? '').toLowerCase();
            return agent.includes(normalizedFilterText) || graph.includes(normalizedFilterText);
        });
    });

    const coldEventKeys = $derived.by(() => {
        const keys = new Set<string>();
        if (coldCount <= 0) {
            return keys;
        }

        for (const event of events.slice(-coldCount)) {
            keys.add(transactionEventKey(event));
        }

        return keys;
    });

    const visibleHotCount = $derived(Math.max(0, hotCount));
    const visibleColdCount = $derived(Math.max(0, coldCount));

    function summarizeRules(event: TransactionEvent): string {
        if (event.traceMode === 'summary') {
            return `${event.ruleCount ?? event.rules.length} rules`;
        }

        let added = 0;
        let removed = 0;

        for (const rule of event.rules) {
            added += rule.added.length;
            removed += rule.removed.length;
        }

        return `${event.rules.length} rules, +${added}/-${removed}`;
    }

    function shortGraph(graph?: string | null): string {
        if (!graph) return '-';
        const parts = graph.split('/');
        return parts.slice(-2).join('/') || graph;
    }

    function transactionEventKey(event: TransactionEvent): string {
        return [
            event.transactionId ?? 'no-tx',
            event.timestamp,
            event.startedAt,
            event.finishedAt,
            event.trigger,
            event.status,
            event.agent ?? '',
            event.graph ?? ''
        ].join('|');
    }

    function toggleExpanded(event: TransactionEvent): void {
        const key = transactionEventKey(event);
        expandedRowKey = expandedRowKey === key ? null : key;
    }

    function isExpanded(event: TransactionEvent): boolean {
        return expandedRowKey === transactionEventKey(event);
    }

    function formatDuration(event: TransactionEvent): string {
        if (!event.startedAt || !event.finishedAt) {
            return '-';
        }
        return `${Math.max(0, event.finishedAt - event.startedAt)} ms`;
    }

    function traceLabel(event: TransactionEvent): string {
        return event.traceMode === 'summary' ? 'summary' : 'full trace';
    }

    function formatTerm(value: string): string {
        if (value.startsWith('<') || value.startsWith('"') || value.startsWith('_:')) {
            return value;
        }
        return `<${value}>`;
    }

    function formatTripleLine(triple: TransactionTriple): string {
        return `${formatTerm(triple.subject)} ${formatTerm(triple.predicate)} ${formatTerm(triple.object)}`;
    }

    function triplesByContext(triples: TransactionTriple[]): Array<{ context: string; lines: string[] }> {
        const grouped = new Map<string, string[]>();

        for (const triple of triples) {
            const context = triple.context ?? '(default graph)';
            if (!grouped.has(context)) {
                grouped.set(context, []);
            }
            grouped.get(context)?.push(formatTripleLine(triple));
        }

        return Array.from(grouped.entries()).map(([context, lines]) => ({ context, lines }));
    }

    async function requestMore(enableAutoLoad = false): Promise<void> {
        if (!hasMore || isLoadingMore || !onLoadMore) {
            return;
        }

        if (enableAutoLoad) {
            autoLoadMore = true;
        }

        await onLoadMore();
    }

    function handleScroll(event: Event): void {
        if (!autoLoadMore || !hasMore || isLoadingMore) {
            return;
        }

        const target = event.currentTarget as HTMLElement;
        const remaining = target.scrollHeight - target.scrollTop - target.clientHeight;
        if (remaining <= 24) {
            void requestMore();
        }
    }
</script>

<div class="flex h-[300px] min-h-[180px] max-h-[70vh] w-full min-w-0 resize-y flex-col overflow-hidden rounded-lg border">
    <div class="flex flex-wrap items-center gap-x-3 gap-y-1 border-b bg-gray-50 px-3 py-2 text-xs text-gray-600">
        <span>Loaded: {events.length} / {totalCount}</span>
        <span>Hot: {visibleHotCount}</span>
        <span>Archived rows: {visibleColdCount}</span>
        {#if archiveRunId}
            <span class="max-w-full truncate font-mono" title={archiveRunId}>Run: {archiveRunId}</span>
        {/if}
    </div>
    <div class="min-h-0 flex-1 overflow-y-auto overflow-x-hidden" onscroll={handleScroll}>
        <table class="w-full text-left">
            <thead class="bg-gray-100 border-b sticky top-0">
                <tr>
                    <th class="p-2">Time</th>
                    <th class="p-2">Trigger</th>
                    <th class="p-2">Agent</th>
                    <th class="p-2">Graph</th>
                    <th class="p-2">Rules / Changes</th>
                </tr>
            </thead>
            <tbody>
            {#each filteredEvents as event (transactionEventKey(event))}
                <tr
                    class={coldEventKeys.has(transactionEventKey(event)) ? 'cursor-pointer border-b border-l-4 border-l-blue-300 bg-blue-50/40 hover:bg-blue-50' : 'cursor-pointer border-b hover:bg-gray-50'}
                    ondblclick={() => toggleExpanded(event)}
                    title={`Double-click for transaction ${traceLabel(event)} details`}
                >
                    <td class="p-2 text-base whitespace-nowrap text-gray-500">
                        <div class="flex flex-wrap items-center gap-1">
                            <span>{new Date(event.timestamp).toLocaleTimeString()}</span>
                            {#if coldEventKeys.has(transactionEventKey(event))}
                                <span class="rounded bg-blue-100 px-1.5 py-0.5 text-xs text-blue-700">Archived</span>
                            {/if}
                        </div>
                    </td>
                    <td class="p-2 text-base font-medium">{event.trigger}</td>
                    <td class="p-2 text-base">{event.agent?.split('/').pop() ?? '-'}</td>
                    <td class="max-w-[180px] truncate p-2 font-mono text-sm" title={event.graph ?? ''}>
                        {shortGraph(event.graph)}
                    </td>
                    <td class="p-2 text-sm" title={`tx: ${event.transactionId ?? '-'}, status: ${event.status}, trace: ${traceLabel(event)}${event.error ? `, error: ${event.error}` : ''}`}>
                        {summarizeRules(event)}
                    </td>
                </tr>

                {#if isExpanded(event)}
                    <tr class="border-b bg-gray-50">
                        <td colspan="5" class="p-3">
                            <div class="space-y-3">
                                <div class="grid grid-cols-1 gap-2 text-sm md:grid-cols-4">
                                    <div class="bg-white rounded border p-2">
                                        <div class="text-gray-500">Transaction</div>
                                        <div class="font-semibold">{event.transactionId ?? '-'}</div>
                                    </div>
                                    <div class="bg-white rounded border p-2">
                                        <div class="text-gray-500">Status</div>
                                        <div class="font-semibold">{event.status}</div>
                                    </div>
                                    <div class="bg-white rounded border p-2">
                                        <div class="text-gray-500">Duration</div>
                                        <div class="font-semibold">{formatDuration(event)}</div>
                                    </div>
                                    <div class="bg-white rounded border p-2">
                                        <div class="text-gray-500">Merge Delta</div>
                                        <div class="font-semibold">
                                            {event.traceMode === 'summary' ? 'not captured' : `+${event.mergeAdded.length} / -${event.mergeRemoved.length}`}
                                        </div>
                                    </div>
                                </div>

                                {#if event.error}
                                    <div class="rounded border border-red-200 bg-red-50 p-2 text-sm text-red-700">
                                        <span class="font-semibold">Error:</span> {event.error}
                                    </div>
                                {/if}

                                {#if event.requestBody}
                                    <div>
                                        <h4 class="mb-1 text-sm font-semibold text-gray-700">Request Body</h4>
                                        <pre class="max-h-40 overflow-auto rounded border bg-white p-2 text-sm">{event.requestBody}</pre>
                                    </div>
                                {:else if event.traceMode === 'summary'}
                                    <div class="rounded border border-blue-200 bg-blue-50 p-2 text-sm text-blue-700">
                                        Summary trace: {event.ruleCount ?? event.rules.length} rules executed. Triple-level request, merge, and per-rule diffs are disabled by the server scenario configuration.
                                    </div>
                                {/if}

                                {#if event.traceMode !== 'summary'}
                                    <div class="grid grid-cols-1 md:grid-cols-2 gap-3">
                                        <div>
                                            <h4 class="mb-1 text-sm font-semibold text-gray-700">Merge Added ({event.mergeAdded.length})</h4>
                                            <div class="bg-white border rounded p-2 max-h-40 overflow-auto">
                                                {#if event.mergeAdded.length === 0}
                                                    <div class="text-sm text-gray-400">No added triples.</div>
                                                {:else}
                                                    <div class="space-y-2">
                                                        {#each triplesByContext(event.mergeAdded) as group}
                                                            <div>
                                                                <div class="break-all font-mono text-xs text-gray-500">&lt;context = {group.context}&gt; :</div>
                                                                <ul class="space-y-1 mt-1">
                                                                    {#each group.lines as line}
                                                                        <li class="break-all font-mono text-sm">{line}</li>
                                                                    {/each}
                                                                </ul>
                                                            </div>
                                                        {/each}
                                                    </div>
                                                {/if}
                                            </div>
                                        </div>

                                        <div>
                                            <h4 class="mb-1 text-sm font-semibold text-gray-700">Merge Removed ({event.mergeRemoved.length})</h4>
                                            <div class="bg-white border rounded p-2 max-h-40 overflow-auto">
                                                {#if event.mergeRemoved.length === 0}
                                                    <div class="text-sm text-gray-400">No removed triples.</div>
                                                {:else}
                                                    <div class="space-y-2">
                                                        {#each triplesByContext(event.mergeRemoved) as group}
                                                            <div>
                                                                <div class="break-all font-mono text-xs text-gray-500">&lt;context = {group.context}&gt; :</div>
                                                                <ul class="space-y-1 mt-1">
                                                                    {#each group.lines as line}
                                                                        <li class="break-all font-mono text-sm">{line}</li>
                                                                    {/each}
                                                                </ul>
                                                            </div>
                                                        {/each}
                                                    </div>
                                                {/if}
                                            </div>
                                        </div>
                                    </div>

                                    <div>
                                        <h4 class="mb-1 text-sm font-semibold text-gray-700">Per Rule Changes</h4>
                                        <div class="space-y-2 max-h-64 overflow-auto pr-1">
                                            {#if event.rules.length === 0}
                                                <div class="rounded border bg-white p-2 text-sm text-gray-400">No rule-level changes.</div>
                                            {:else}
                                                {#each event.rules as rule}
                                                    <div class="bg-white border rounded p-2">
                                                        <div class="flex justify-between items-center mb-1">
                                                            <div class="text-sm font-semibold">{rule.ruleName}</div>
                                                            <div class="text-sm text-gray-500">+{rule.added.length} / -{rule.removed.length}</div>
                                                        </div>
                                                        {#if rule.error}
                                                            <div class="mb-1 text-sm text-red-700">Error: {rule.error}</div>
                                                        {/if}
                                                        <div class="grid grid-cols-1 md:grid-cols-2 gap-2">
                                                            <div>
                                                                <div class="mb-1 text-xs text-gray-500">Added</div>
                                                                {#if rule.added.length === 0}
                                                                    <div class="text-sm text-gray-400">None</div>
                                                                {:else}
                                                                    <div class="space-y-2">
                                                                        {#each triplesByContext(rule.added) as group}
                                                                            <div>
                                                                                <div class="break-all font-mono text-xs text-gray-500">&lt;context = {group.context}&gt; :</div>
                                                                                <ul class="space-y-1 mt-1">
                                                                                    {#each group.lines as line}
                                                                                        <li class="break-all font-mono text-sm">{line}</li>
                                                                                    {/each}
                                                                                </ul>
                                                                            </div>
                                                                        {/each}
                                                                    </div>
                                                                {/if}
                                                            </div>
                                                            <div>
                                                                <div class="mb-1 text-xs text-gray-500">Removed</div>
                                                                {#if rule.removed.length === 0}
                                                                    <div class="text-sm text-gray-400">None</div>
                                                                {:else}
                                                                    <div class="space-y-2">
                                                                        {#each triplesByContext(rule.removed) as group}
                                                                            <div>
                                                                                <div class="break-all font-mono text-xs text-gray-500">&lt;context = {group.context}&gt; :</div>
                                                                                <ul class="space-y-1 mt-1">
                                                                                    {#each group.lines as line}
                                                                                        <li class="break-all font-mono text-sm">{line}</li>
                                                                                    {/each}
                                                                                </ul>
                                                                            </div>
                                                                        {/each}
                                                                    </div>
                                                                {/if}
                                                            </div>
                                                        </div>
                                                    </div>
                                                {/each}
                                            {/if}
                                        </div>
                                    </div>
                                {/if}
                            </div>
                        </td>
                    </tr>
                {/if}
            {/each}
            {#if filteredEvents.length === 0}
                <tr>
                    <td colspan="5" class="p-4 text-center text-gray-500">
                        {normalizedFilterText ? 'No matching transaction updates.' : 'No transaction updates yet.'}
                    </td>
                </tr>
            {/if}
            {#if hasMore}
                <tr>
                    <td colspan="5" class="p-3 text-center">
                        <button
                            type="button"
                            class="rounded border border-gray-300 bg-white px-3 py-1.5 text-sm hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-60"
                            onclick={() => requestMore(true)}
                            disabled={isLoadingMore}
                        >
                            {isLoadingMore ? 'Loading...' : 'Load More'}
                        </button>
                    </td>
                </tr>
            {/if}
            </tbody>
        </table>
    </div>
</div>
