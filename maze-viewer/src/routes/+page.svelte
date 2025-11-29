<script lang="ts">
    import { mazeState } from '$lib/mazeState.svelte';
    import { onMount } from 'svelte';
    import type { PageData } from './$types';
    import MazeCanvas from '$lib/components/MazeCanvas.svelte';

    let { data } = $props<{ data: PageData }>();

    onMount(() => {
        mazeState.connect();
    });
</script>

<div class="p-4 flex flex-col lg:flex-row gap-6">
    <!-- Left Column: Maze Visualization -->
    <div class="flex-1">
        <h1 class="text-2xl font-bold mb-4">Maze Viewer</h1>
        {#if data.maze}
            <div class="overflow-auto">
                <MazeCanvas maze={data.maze} />
            </div>
        {:else}
            <div class="p-8 bg-gray-100 rounded text-center text-gray-500">
                Loading maze layout... (Ensure server is running at localhost:8080)
            </div>
        {/if}
    </div>

    <!-- Right Column: Event Log -->
    <div class="w-full lg:w-1/3">
        <h2 class="text-xl font-bold mb-4">Live Events</h2>
        
        <div class="mb-4">
            Status: 
            <span class:text-green-600={mazeState.status === 'connected'} 
                  class:text-red-600={mazeState.status === 'disconnected' || mazeState.status === 'error'}
                  class="font-bold">
                {mazeState.status}
            </span>
        </div>

        <div class="border rounded-lg overflow-hidden max-h-[600px] overflow-y-auto">
            <table class="w-full text-left">
                <thead class="bg-gray-100 border-b sticky top-0">
                    <tr>
                        <th class="p-2">Time</th>
                        <th class="p-2">Agent</th>
                        <th class="p-2">Action</th>
                        <th class="p-2">Loc</th>
                    </tr>
                </thead>
                <tbody>
                    {#each mazeState.events as event}
                        <tr class="border-b hover:bg-gray-50">
                            <td class="p-2 text-gray-500 text-sm whitespace-nowrap">
                                {new Date(event.timestamp).toLocaleTimeString()}
                            </td>
                            <td class="p-2 font-medium">{event.agent}</td>
                            <td class="p-2 text-sm">{event.type}</td>
                            <td class="p-2 font-mono text-xs truncate max-w-[100px]" title={event.cell}>
                                {event.cell.split('/').pop()}
                            </td>
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
</div>
