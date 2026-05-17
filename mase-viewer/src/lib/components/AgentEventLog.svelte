<script lang="ts">
    import type { AgentMovedEvent } from '$lib/mazeState.svelte';

    let {
        events,
        filterText = '',
        onAgentSelect,
        hasMore = false,
        isLoadingMore = false,
        onLoadMore,
        totalCount = 0,
        hotCount = 0,
        coldCount = 0,
        archiveRunId = ''
    } = $props<{
        events: AgentMovedEvent[];
        filterText?: string;
        onAgentSelect?: (agentUri: string) => void;
        hasMore?: boolean;
        isLoadingMore?: boolean;
        onLoadMore?: () => void | Promise<void>;
        totalCount?: number;
        hotCount?: number;
        coldCount?: number;
        archiveRunId?: string;
    }>();

    let autoLoadMore = $state(false);

    const normalizedFilterText = $derived(filterText.trim().toLowerCase());

    const filteredEvents = $derived.by(() => {
        if (!normalizedFilterText) {
            return events;
        }

        return events.filter((event: AgentMovedEvent) => {
            const agent = event.agent.toLowerCase();
            const location = formatCellLocation(event.cell).toLowerCase();
            return agent.includes(normalizedFilterText) || location.includes(normalizedFilterText);
        });
    });

    const coldEventKeys = $derived.by(() => {
        const keys = new Set<string>();
        if (coldCount <= 0) {
            return keys;
        }

        for (const event of events.slice(-coldCount)) {
            keys.add(agentEventKey(event));
        }

        return keys;
    });

    const visibleHotCount = $derived(Math.max(0, hotCount));
    const visibleColdCount = $derived(Math.max(0, coldCount));

    /**
     * Extract cell coordinates from cell URI.
     * Handles formats like /cells/5 or /cells/x/y
     */
    function formatCellLocation(cellUri: string): string {
        const parts = cellUri.split('/');
        const cellsIndex = parts.indexOf('cells');
        
        if (cellsIndex !== -1 && cellsIndex < parts.length - 1) {
            const coords = parts.slice(cellsIndex + 1);
            // If there are two segments after /cells/, it's x/y format
            if (coords.length >= 2) {
                return coords.join('/');
            }
            // Otherwise return the single segment
            return coords[0];
        }
        
        return cellUri.split('/').pop() || cellUri;
    }

    function agentEventKey(event: AgentMovedEvent): string {
        return [event.timestamp, event.agent, event.cell].join('|');
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
                    <th class="p-2">Agent</th>
                    <th class="p-2">Location</th>
                </tr>
            </thead>
            <tbody>
                {#each filteredEvents as event (agentEventKey(event))}
                    <tr class={coldEventKeys.has(agentEventKey(event)) ? 'border-b border-l-4 border-l-blue-300 bg-blue-50/40 hover:bg-blue-50' : 'border-b hover:bg-gray-50'}>
                        <td class="p-2 text-base whitespace-nowrap text-gray-500">
                            <div class="flex flex-wrap items-center gap-1">
                                <span>{new Date(event.timestamp).toLocaleTimeString()}</span>
                                {#if coldEventKeys.has(agentEventKey(event))}
                                    <span class="rounded bg-blue-100 px-1.5 py-0.5 text-xs text-blue-700">Archived</span>
                                {/if}
                            </div>
                        </td>
                        <td 
                            class="p-2 font-medium cursor-pointer hover:bg-blue-50 hover:text-blue-600" 
                            ondblclick={() => onAgentSelect?.(event.agent)}
                            title="Double-click to inspect agent graph">
                            {event.agent.split('/').pop()}
                        </td>
                        <td class="max-w-[200px] truncate p-2 font-mono text-sm" title={event.cell}>
                            {formatCellLocation(event.cell)}
                        </td>
                    </tr>
                {/each}
                {#if filteredEvents.length === 0}
                    <tr>
                        <td colspan="3" class="p-4 text-center text-gray-500">
                            {normalizedFilterText ? 'No matching agent movements.' : 'No agent movements yet.'}
                        </td>
                    </tr>
                {/if}
                {#if hasMore}
                    <tr>
                        <td colspan="3" class="p-3 text-center">
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
