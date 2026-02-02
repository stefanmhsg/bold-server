<script lang="ts">
    import type { AgentMovedEvent } from '$lib/mazeState.svelte';

    let { events } = $props<{ events: AgentMovedEvent[] }>();
</script>

<div class="border rounded-lg overflow-hidden max-h-[300px] overflow-y-auto">
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
                    <td class="p-2 font-medium">{event.agent}</td>
                    <td class="p-2 font-mono text-xs truncate max-w-[200px]" title={event.cell}>
                        {event.cell.split('/').pop()}
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
