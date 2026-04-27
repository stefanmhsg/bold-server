<script lang="ts">
    import type { TransactionEvent, TransactionTriple } from '$lib/mazeState.svelte';

    let { events, filterText = '' } = $props<{ events: TransactionEvent[]; filterText?: string }>();

    let expandedRow = $state<number | null>(null);

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

    function summarizeRules(event: TransactionEvent): string {
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

    function toggleExpanded(index: number): void {
        expandedRow = expandedRow === index ? null : index;
    }

    function isExpanded(index: number): boolean {
        return expandedRow === index;
    }

    function formatDuration(event: TransactionEvent): string {
        if (!event.startedAt || !event.finishedAt) {
            return '-';
        }
        return `${Math.max(0, event.finishedAt - event.startedAt)} ms`;
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
</script>

<div class="h-[300px] min-h-[180px] max-h-[70vh] w-full min-w-0 resize-y overflow-hidden rounded-lg border">
    <div class="h-full overflow-y-auto overflow-x-hidden">
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
            {#each filteredEvents as event, index}
                <tr
                    class="border-b hover:bg-gray-50 cursor-pointer"
                    ondblclick={() => toggleExpanded(index)}
                    title="Double-click for full transaction details"
                >
                    <td class="p-2 text-base whitespace-nowrap text-gray-500">
                        {new Date(event.timestamp).toLocaleTimeString()}
                    </td>
                    <td class="p-2 text-base font-medium">{event.trigger}</td>
                    <td class="p-2 text-base">{event.agent?.split('/').pop() ?? '-'}</td>
                    <td class="max-w-[180px] truncate p-2 font-mono text-sm" title={event.graph ?? ''}>
                        {shortGraph(event.graph)}
                    </td>
                    <td class="p-2 text-sm" title={`status: ${event.status}${event.error ? `, error: ${event.error}` : ''}`}>
                        {summarizeRules(event)}
                    </td>
                </tr>

                {#if isExpanded(index)}
                    <tr class="border-b bg-gray-50">
                        <td colspan="5" class="p-3">
                            <div class="space-y-3">
                                <div class="grid grid-cols-1 gap-2 text-sm md:grid-cols-3">
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
                                        <div class="font-semibold">+{event.mergeAdded.length} / -{event.mergeRemoved.length}</div>
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
                                {/if}

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
            </tbody>
        </table>
    </div>
</div>
