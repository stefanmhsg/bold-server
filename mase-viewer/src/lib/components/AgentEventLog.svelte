<script lang="ts">
    import type { AgentMovedEvent } from '$lib/mazeState.svelte';

    let { events, onAgentSelect } = $props<{ 
        events: AgentMovedEvent[];
        onAgentSelect?: (agentUri: string) => void;
    }>();

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

<div class="border rounded-lg overflow-hidden resize-y h-[300px] min-h-[180px] max-h-[70vh]">
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
                {#each events as event}
                    <tr class="border-b hover:bg-gray-50">
                        <td class="p-2 text-gray-500 text-sm whitespace-nowrap">
                            {new Date(event.timestamp).toLocaleTimeString()}
                        </td>
                        <td 
                            class="p-2 font-medium cursor-pointer hover:bg-blue-50 hover:text-blue-600" 
                            ondblclick={() => onAgentSelect?.(event.agent)}
                            title="Double-click to inspect agent graph">
                            {event.agent.split('/').pop()}
                        </td>
                        <td class="p-2 font-mono text-xs truncate max-w-[200px]" title={event.cell}>
                            {formatCellLocation(event.cell)}
                        </td>
                    </tr>
                {/each}
                {#if events.length === 0}
                    <tr>
                        <td colspan="3" class="p-4 text-center text-gray-500">
                            No agent movements yet.
                        </td>
                    </tr>
                {/if}
            </tbody>
        </table>
    </div>
</div>
