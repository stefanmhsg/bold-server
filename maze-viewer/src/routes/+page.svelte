<script lang="ts">
    import { mazeState } from '$lib/mazeState.svelte';
    import { onMount } from 'svelte';

    onMount(() => {
        mazeState.connect();
    });
</script>

<div class="p-4">
    <h1 class="text-2xl font-bold mb-4">Maze Live Events</h1>
    
    <div class="mb-4">
        Status: 
        <span class:text-green-600={mazeState.status === 'connected'} 
              class:text-red-600={mazeState.status === 'disconnected' || mazeState.status === 'error'}
              class="font-bold">
            {mazeState.status}
        </span>
    </div>

    <div class="border rounded-lg overflow-hidden">
        <table class="w-full text-left">
            <thead class="bg-gray-100 border-b">
                <tr>
                    <th class="p-2">Time</th>
                    <th class="p-2">Agent</th>
                    <th class="p-2">Action</th>
                    <th class="p-2">Location</th>
                </tr>
            </thead>
            <tbody>
                {#each mazeState.events as event}
                    <tr class="border-b hover:bg-gray-50">
                        <td class="p-2 text-gray-500 text-sm">
                            {new Date(event.timestamp).toLocaleTimeString()}
                        </td>
                        <td class="p-2 font-medium">{event.agent}</td>
                        <td class="p-2">{event.type}</td>
                        <td class="p-2 font-mono text-sm">{event.cell}</td>
                    </tr>
                {/each}
                {#if mazeState.events.length === 0}
                    <tr>
                        <td colspan="4" class="p-4 text-center text-gray-500">
                            No events received yet. Waiting for agents...
                        </td>
                    </tr>
                {/if}
            </tbody>
        </table>
    </div>
</div>
