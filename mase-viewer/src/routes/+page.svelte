<script lang="ts">
    import { mazeState } from '$lib/mazeState.svelte';
    import { onMount } from 'svelte';
    import type { PageData } from './$types';
    import MazeCanvas from '$lib/components/MazeCanvas.svelte';
    import AgentEventLog from '$lib/components/AgentEventLog.svelte';
    import CellEventLog from '$lib/components/CellEventLog.svelte';

    let { data } = $props<{ data: PageData }>();

    let selectedCellData = $state<string | null>(null);
    let selectedCellId = $state<string | null>(null);
    let isLoadingCell = $state(false);

    let selectedAgentData = $state<string | null>(null);
    let selectedAgentId = $state<string | null>(null);
    let isLoadingAgent = $state(false);

    onMount(() => {
        mazeState.connect();
    });

    async function handleCellSelect(cellId: string) {
        selectedCellId = cellId;
        isLoadingCell = true;
        selectedCellData = null;

        try {
            // The cellId is the full URI (e.g. http://127.0.1.1:8080/cells/5)
            // We fetch it directly, requesting Turtle format
            const response = await fetch(cellId, {
                headers: {
                    'Accept': 'text/turtle'
                }
            });

            if (response.ok) {
                selectedCellData = await response.text();
            } else {
                selectedCellData = `Error: ${response.status} ${response.statusText}`;
            }
        } catch (e) {
            selectedCellData = `Error fetching cell data: ${e}`;
        } finally {
            isLoadingCell = false;
        }
    }

    async function handleAgentSelect(agentUri: string) {
        selectedAgentId = agentUri;
        isLoadingAgent = true;
        selectedAgentData = null;

        try {
            // The agentUri is the full URI (e.g. http://127.0.1.1:8080/agents/mybot)
            // We fetch it directly, requesting Turtle format
            const response = await fetch(agentUri, {
                headers: {
                    'Accept': 'text/turtle'
                }
            });

            if (response.ok) {
                selectedAgentData = await response.text();
            } else {
                selectedAgentData = `Error: ${response.status} ${response.statusText}`;
            }
        } catch (e) {
            selectedAgentData = `Error fetching agent data: ${e}`;
        } finally {
            isLoadingAgent = false;
        }
    }
</script>

<div class="p-4 flex flex-col lg:flex-row gap-6">
    <!-- Left Column: Maze Visualization -->
    <div class="flex-1 flex flex-col gap-6">
        <h1 class="text-2xl font-bold">Maze Viewer</h1>
        
        {#if data.maze}
            <div class="resize overflow-hidden border-2 border-gray-300 rounded bg-white" style="height: 600px;">
                <MazeCanvas maze={data.maze} uiSnapshot={data.uiSnapshot || []} onCellSelect={handleCellSelect} />
            </div>
        {:else}
            <div class="p-8 bg-gray-100 rounded text-center text-gray-500">
                Loading maze layout... (Ensure server is running at localhost:8080)
            </div>
        {/if}

        <!-- Cell Data Inspector -->
        {#if selectedCellId}
            <div class="border rounded-lg shadow-sm p-4 bg-white overflow-auto">
                <h2 class="text-lg font-bold mb-2 flex items-center gap-2 justify-between">
                    <div class="flex items-center gap-2">
                        <span>Cell Inspector</span>
                        <span class="text-sm font-normal text-gray-500 font-mono bg-gray-100 px-2 py-1 rounded">
                            {selectedCellId}
                        </span>
                    </div>
                    <button 
                        onclick={() => selectedCellId = null}
                        class="text-gray-500 hover:text-gray-700 hover:bg-gray-100 rounded px-2 py-1"
                        title="Close inspector">
                        ✕
                    </button>
                </h2>
                
                {#if isLoadingCell}
                    <div class="text-gray-500 italic p-4">Loading RDF data...</div>
                {:else if selectedCellData}
                    <pre class="bg-gray-900 text-gray-100 p-4 rounded overflow-x-auto text-sm font-mono leading-relaxed">{selectedCellData}</pre>
                {:else}
                    <div class="text-gray-400 italic">No data available</div>
                {/if}
            </div>
        {/if}

        <!-- Agent Inspector -->
        {#if selectedAgentId}
            <div class="border rounded-lg shadow-sm p-4 bg-white overflow-auto">
                <h2 class="text-lg font-bold mb-2 flex items-center gap-2 justify-between">
                    <div class="flex items-center gap-2">
                        <span>Agent Inspector</span>
                        <span class="text-sm font-normal text-gray-500 font-mono bg-gray-100 px-2 py-1 rounded">
                            {selectedAgentId}
                        </span>
                    </div>
                    <button 
                        onclick={() => selectedAgentId = null}
                        class="text-gray-500 hover:text-gray-700 hover:bg-gray-100 rounded px-2 py-1"
                        title="Close inspector">
                        ✕
                    </button>
                </h2>
                
                {#if isLoadingAgent}
                    <div class="text-gray-500 italic p-4">Loading agent RDF data...</div>
                {:else if selectedAgentData}
                    <pre class="bg-gray-900 text-gray-100 p-4 rounded overflow-x-auto text-sm font-mono leading-relaxed">{selectedAgentData}</pre>
                {:else}
                    <div class="text-gray-400 italic">No data available</div>
                {/if}
            </div>
        {/if}
    </div>

    <!-- Right Column: Event Logs -->
    <div class="w-full lg:w-[500px] flex flex-col gap-4 flex-1">
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
            <AgentEventLog events={mazeState.agentEvents} onAgentSelect={handleAgentSelect} />
        </div>

        <div>
            <h3 class="font-semibold mb-2 text-gray-700">Cell Updates</h3>
            <CellEventLog />
        </div>
    </div>
</div>
