<script lang="ts">
    import type { CellStateEvent } from '$lib/mazeState.svelte';

    let { events } = $props<{ events: CellStateEvent[] }>();
</script>

<div class="border rounded-lg overflow-hidden max-h-[300px] overflow-y-auto">
    <table class="w-full text-left">
        <thead class="bg-gray-100 border-b sticky top-0">
            <tr>
                <th class="p-2">Time</th>
                <th class="p-2">State</th>
                <th class="p-2">Cell</th>
            </tr>
        </thead>
        <tbody>
            {#each events as event}
                <tr class="border-b hover:bg-gray-50">
                    <td class="p-2 text-gray-500 text-sm whitespace-nowrap">
                        {new Date(event.timestamp).toLocaleTimeString()}
                    </td>
                    <td class="p-2 text-sm font-bold" 
                        class:text-red-600={event.type === 'CELL_LOCKED'}
                        class:text-green-600={event.type === 'CELL_UNLOCKED'}>
                        {event.type === 'CELL_LOCKED' ? 'LOCKED' : 'UNLOCKED'}
                    </td>
                    <td class="p-2 font-mono text-xs truncate max-w-[100px]" title={event.cell}>
                        {event.cell.split('/').pop()}
                    </td>
                </tr>
            {/each}
            {#if events.length === 0}
                <tr>
                    <td colspan="3" class="p-4 text-center text-gray-500">
                        No cell updates yet.
                    </td>
                </tr>
            {/if}
        </tbody>
    </table>
</div>
