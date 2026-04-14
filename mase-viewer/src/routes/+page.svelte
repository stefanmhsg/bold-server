<script lang="ts">
    import { mazeState } from '$lib/mazeState.svelte';
    import { onMount } from 'svelte';
    import type { PageData } from './$types';
    import MazeCanvas from '$lib/components/MazeCanvas.svelte';
    import AgentEventLog from '$lib/components/AgentEventLog.svelte';
    import CellEventLog from '$lib/components/CellEventLog.svelte';
    import { showOptimalRoute } from '$lib/routeOverlayStore';

    let { data } = $props<{ data: PageData }>();

    let selectedCellData = $state<string | null>(null);
    let selectedCellId = $state<string | null>(null);
    let isLoadingCell = $state(false);

    let selectedAgentData = $state<string | null>(null);
    let selectedAgentId = $state<string | null>(null);
    let isLoadingAgent = $state(false);
    let turtleInput = $state<string>('');
    let isPosting = $state(false);
    let postMessage = $state<{ type: 'success' | 'error', text: string } | null>(null);
    let eventFilterText = $state('');

    onMount(() => {
        mazeState.connect();
    });

    /**
     * Remove unused @prefix declarations from Turtle RDF.
     * Only keeps prefixes that are actually used in the content.
     * Mase-Server includes all prefixes that exist in the store, thus filter for readability.
     */
    function cleanUnusedPrefixes(turtle: string): string {
        const lines = turtle.split('\n');
        const prefixLines: { prefix: string; line: string; index: number }[] = [];
        const contentLines: string[] = [];
        
        // Separate prefix declarations from content
        lines.forEach((line, index) => {
            const prefixMatch = line.match(/^@prefix\s+(\w+):/);
            if (prefixMatch) {
                prefixLines.push({ prefix: prefixMatch[1], line, index });
            } else {
                contentLines.push(line);
            }
        });
        
        // Find which prefixes are actually used in the content
        const usedPrefixes = new Set<string>();
        const content = contentLines.join('\n');
        
        prefixLines.forEach(({ prefix }) => {
            // Check if prefix is used with colon notation (e.g., "maze:", "rdf:")
            const prefixPattern = new RegExp(`\\b${prefix}:`, 'g');
            if (prefixPattern.test(content)) {
                usedPrefixes.add(prefix);
            }
        });
        
        // Rebuild with only used prefixes
        const usedPrefixLines = prefixLines
            .filter(({ prefix }) => usedPrefixes.has(prefix))
            .map(({ line }) => line);
        
        // Return cleaned content
        if (usedPrefixLines.length > 0) {
            return [...usedPrefixLines, '', ...contentLines].join('\n');
        } else {
            return contentLines.join('\n');
        }
    }

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
                const rawData = await response.text();
                selectedCellData = cleanUnusedPrefixes(rawData);
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
        turtleInput = `<${agentUri}> foaf:knows "me" .`;
        postMessage = null;

        try {
            // The agentUri is the full URI (e.g. http://127.0.1.1:8080/agents/mybot)
            // We fetch it directly, requesting Turtle format
            const response = await fetch(agentUri, {
                headers: {
                    'Accept': 'text/turtle'
                }
            });

            if (response.ok) {
                const rawData = await response.text();
                selectedAgentData = cleanUnusedPrefixes(rawData);
            } else {
                selectedAgentData = `Error: ${response.status} ${response.statusText}`;
            }
        } catch (e) {
            selectedAgentData = `Error fetching agent data: ${e}`;
        } finally {
            isLoadingAgent = false;
        }
    }

    async function handleAgentPost() {
        if (!selectedAgentId || !turtleInput.trim()) return;

        isPosting = true;
        postMessage = null;

        try {
            const response = await fetch(selectedAgentId, {
                method: 'POST',
                headers: {
                    'Content-Type': 'text/turtle',
                    'Accept': '*/*'
                },
                body: turtleInput
            });

            if (response.ok) {
                const responseText = await response.text();
                postMessage = { type: 'success', text: `Success: ${responseText}` };
                turtleInput = '';
                // Auto-reload agent data after successful POST
                await handleAgentSelect(selectedAgentId);
            } else {
                const errorText = await response.text();
                postMessage = { type: 'error', text: `Error ${response.status}: ${errorText}` };
            }
        } catch (e) {
            postMessage = { type: 'error', text: `Error posting data: ${e}` };
        } finally {
            isPosting = false;
        }
    }
</script>

<div class="p-4 flex flex-col lg:flex-row lg:flex-wrap gap-6">
    <!-- Left Column: Maze Visualization -->
    <div class="min-w-0 flex-[1_1_48rem] lg:min-w-[40rem] flex flex-col gap-6">
        <div class="flex items-center justify-between gap-3">
            <div class="flex items-center gap-3">
                <h1 class="text-2xl font-bold">Maze Viewer</h1>
                {#if data.scenarioName}
                    <span class="px-2 py-1 text-xs font-semibold rounded bg-blue-50 text-blue-700 border border-blue-200">
                        Scenario: {data.scenarioName}
                    </span>
                {/if}
            </div>
            <button
                onclick={() => showOptimalRoute.update((v) => !v)}
                class="px-3 py-1.5 text-sm rounded border border-gray-300 bg-white hover:bg-gray-50"
                type="button"
            >
                {$showOptimalRoute ? 'Hide Optimal Route' : 'Show Optimal Route'}
            </button>
        </div>
        
        {#if data.maze}
            <div class="w-full max-w-full resize overflow-hidden border-2 border-gray-300 rounded bg-white" style="height: 600px;">
                <MazeCanvas maze={data.maze} uiSnapshot={data.uiSnapshot || []} scenarioName={data.scenarioName} onCellSelect={handleCellSelect} />
            </div>
        {:else}
            <div class="p-8 bg-gray-100 rounded text-center text-gray-500">
                Loading maze layout... (Ensure server is running at localhost:8080)
            </div>
        {/if}

        <!-- Cell Data Inspector -->
        {#if selectedCellId}
            <div class="min-w-0 border rounded-lg shadow-sm p-4 bg-white overflow-auto">
                <h2 class="text-lg font-bold mb-2 flex items-center gap-2 justify-between">
                    <div class="flex min-w-0 flex-1 items-center gap-2">
                        <span>Cell Inspector</span>
                        <span class="max-w-full truncate text-sm font-normal text-gray-500 font-mono bg-gray-100 px-2 py-1 rounded">
                            {selectedCellId}
                        </span>
                    </div>
                    <div class="flex gap-2">
                        <button 
                            onclick={() => selectedCellId && handleCellSelect(selectedCellId)}
                            class="text-gray-500 hover:text-gray-700 hover:bg-gray-100 rounded px-2 py-1"
                            title="Reload cell data"
                            disabled={isLoadingCell}>
                            ↻
                        </button>
                        <button 
                            onclick={() => selectedCellId = null}
                            class="text-gray-500 hover:text-gray-700 hover:bg-gray-100 rounded px-2 py-1"
                            title="Close inspector">
                            ✕
                        </button>
                    </div>
                </h2>
                
                {#if isLoadingCell}
                    <div class="text-gray-500 italic p-4">Loading RDF data...</div>
                {:else if selectedCellData}
                    <pre class="w-full max-w-full bg-gray-900 text-gray-100 p-4 rounded overflow-x-auto text-sm font-mono leading-relaxed">{selectedCellData}</pre>
                {:else}
                    <div class="text-gray-400 italic">No data available</div>
                {/if}
            </div>
        {/if}
    </div>

    <!-- Right Column: Event Logs -->
    <div class="w-full min-w-0 flex flex-col gap-4 lg:flex-[1_1_28rem] lg:min-w-[28rem]">
        <div class="flex justify-between items-center">
            <h2 class="text-xl font-bold">Live Events</h2>
            <span class:text-green-600={mazeState.status === 'connected'} 
                  class:text-red-600={mazeState.status === 'disconnected' || mazeState.status === 'error'}
                  class="font-bold text-sm uppercase">
                {mazeState.status}
            </span>
        </div>

        <div class="flex flex-col sm:flex-row gap-2">
            <input
                bind:value={eventFilterText}
                type="text"
                placeholder="Filter events by Agent / Graph / Location"
                class="flex-1 rounded border border-gray-300 bg-white px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
            <button
                type="button"
                class="px-3 py-2 text-sm rounded border border-gray-300 bg-white hover:bg-gray-50"
                onclick={() => mazeState.clearLogs()}
            >
                Clear Logs
            </button>
        </div>

        <div>
            <h3 class="font-semibold mb-2 text-gray-700">Agent Movements</h3>
            <AgentEventLog events={mazeState.agentEvents} filterText={eventFilterText} onAgentSelect={handleAgentSelect} />
        </div>

        <div>
            <h3 class="font-semibold mb-2 text-gray-700">Cell Updates</h3>
            <CellEventLog events={mazeState.transactionEvents} filterText={eventFilterText} />
        </div>

        <!-- Agent Inspector -->
        {#if selectedAgentId}
            <div class="min-w-0 border rounded-lg shadow-sm p-4 bg-white overflow-auto">
                <h2 class="text-lg font-bold mb-2 flex items-center gap-2 justify-between">
                    <div class="flex min-w-0 flex-1 items-center gap-2">
                        <span>Agent Inspector</span>
                        <span class="max-w-full truncate text-sm font-normal text-gray-500 font-mono bg-gray-100 px-2 py-1 rounded">
                            {selectedAgentId}
                        </span>
                    </div>
                    <div class="flex gap-2">
                        <button 
                            onclick={() => selectedAgentId && handleAgentSelect(selectedAgentId)}
                            class="text-gray-500 hover:text-gray-700 hover:bg-gray-100 rounded px-2 py-1"
                            title="Reload agent data"
                            disabled={isLoadingAgent}>
                            ↻
                        </button>
                        <button 
                            onclick={() => selectedAgentId = null}
                            class="text-gray-500 hover:text-gray-700 hover:bg-gray-100 rounded px-2 py-1"
                            title="Close inspector">
                            ✕
                        </button>
                    </div>
                </h2>
                
                {#if isLoadingAgent}
                    <div class="text-gray-500 italic p-4">Loading agent RDF data...</div>
                {:else if selectedAgentData}
                    <pre class="w-full max-w-full bg-gray-900 text-gray-100 p-4 rounded overflow-x-auto text-sm font-mono leading-relaxed mb-4">{selectedAgentData}</pre>
                {:else}
                    <div class="text-gray-400 italic mb-4">No data available</div>
                {/if}

                <!-- POST Turtle RDF Form -->
                <div class="border-t pt-4">
                    <h3 class="font-semibold mb-2 text-gray-700">POST RDF Triples</h3>
                    <textarea 
                        bind:value={turtleInput}
                        class="w-full h-32 p-3 bg-gray-50 border border-gray-300 rounded font-mono text-sm resize-y focus:outline-none focus:ring-2 focus:ring-blue-500"
                        disabled={isPosting}
                    ></textarea>
                    
                    <div class="flex items-center gap-3 mt-2">
                        <button 
                            onclick={handleAgentPost}
                            disabled={isPosting || !turtleInput.trim()}
                            class="px-4 py-2 bg-blue-600 text-white rounded hover:bg-blue-700 disabled:bg-gray-300 disabled:cursor-not-allowed text-sm font-medium">
                            {isPosting ? 'Posting...' : 'POST to Agent Graph'}
                        </button>
                        
                        {#if postMessage}
                            <div class={postMessage.type === 'success' ? 'text-green-600 text-sm' : 'text-red-600 text-sm'}>
                                {postMessage.text}
                            </div>
                        {/if}
                    </div>
                </div>
            </div>
        {/if}
    </div>
</div>
