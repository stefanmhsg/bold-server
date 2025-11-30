<script lang="ts">
    import { mazeState } from '$lib/mazeState.svelte';
    import { onMount } from 'svelte';
    import type { PageData } from './$types';
    import MazeCanvas from '$lib/components/MazeCanvas.svelte';
    import AgentEventLog from '$lib/components/AgentEventLog.svelte';
    import CellEventLog from '$lib/components/CellEventLog.svelte';

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

    <!-- Right Column: Event Logs -->
    <div class="w-full lg:w-1/3 flex flex-col gap-4">
        <div class="flex justify-between items-center">
            <h2 class="text-xl font-bold">Live Events</h2>
            <span class:text-green-600={mazeState.status === 'connected'} 
                  class:text-red-600={mazeState.status === 'disconnected' || mazeState.status === 'error'}
                  class="font-bold text-sm uppercase">
                {mazeState.status}
            </span>
        </div>

        <div>
            <h3 class="font-semibold mb-2 text-gray-700">Agent Movements</h3>
            <AgentEventLog events={mazeState.agentEvents} />
        </div>

        <div>
            <h3 class="font-semibold mb-2 text-gray-700">Cell Updates</h3>
            <CellEventLog events={mazeState.cellEvents} />
        </div>
    </div>
</div>
