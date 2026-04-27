<script lang="ts">
    import type { AgentMovedEvent } from '$lib/mazeState.svelte';

    let { events, filterText = '', onAgentSelect } = $props<{ 
        events: AgentMovedEvent[];
        filterText?: string;
        onAgentSelect?: (agentUri: string) => void;
    }>();

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
</script>

<div class="h-[300px] min-h-[180px] max-h-[70vh] w-full min-w-0 resize-y overflow-hidden rounded-lg border">
    <div class="h-full overflow-y-auto overflow-x-hidden">
        <table class="w-full text-left">
            <thead class="bg-gray-100 border-b sticky top-0">
                <tr>
                    <th class="p-2">Time</th>
                    <th class="p-2">Agent</th>
                    <th class="p-2">Location</th>
                </tr>
            </thead>
            <tbody>
                {#each filteredEvents as event}
                    <tr class="border-b hover:bg-gray-50">
                        <td class="p-2 text-base whitespace-nowrap text-gray-500">
                            {new Date(event.timestamp).toLocaleTimeString()}
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
            </tbody>
        </table>
    </div>
</div>
