<script lang="ts">
    import { mazeState } from '$lib/mazeState.svelte';
    import { invalidateAll } from '$app/navigation';
    import { onDestroy, onMount } from 'svelte';
    import type { PageData } from './$types';
    import MazeCanvas from '$lib/components/MazeCanvas.svelte';
    import AgentEventLog from '$lib/components/AgentEventLog.svelte';
    import CellEventLog from '$lib/components/CellEventLog.svelte';
    import { showOptimalRoute } from '$lib/routeOverlayStore';
    import { ARCHIVE_EVENT_TYPES, type ArchiveEventType } from '$lib/eventArchive';

    const EVENT_TYPE_LABELS: Record<ArchiveEventType, string> = {
        AGENT_MOVED: 'Agent Movements',
        TRANSACTION: 'Cell Updates',
        UI_UPSERT: 'UI Upserts',
        UI_DELETE: 'UI Deletes'
    };
    const SUCCESS_MESSAGE_AUTO_DISMISS_MS = 15000;

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
    let isResetting = $state(false);
    let isExportingLogs = $state(false);
    let pendingOperationText = $state<string | null>(null);
    let resetDialogOpen = $state(false);
    let exportDialogOpen = $state(false);
    let canvasRevision = $state(0);
    let resetMessage = $state<{ type: 'success' | 'error', text: string } | null>(null);
    let resetMessageTimer: ReturnType<typeof setTimeout> | null = null;
    let exportTypeSelection = $state<Record<ArchiveEventType, boolean>>(createDefaultExportTypeSelection());

    const selectedExportTypes = $derived.by(() => ARCHIVE_EVENT_TYPES.filter((type) => exportTypeSelection[type]));
    const canExportSelectedTypes = $derived(selectedExportTypes.length > 0);

    onMount(() => {
        mazeState.connect(data.serverConfig.webSocketUrl);
    });

    onDestroy(() => {
        if (resetMessageTimer) {
            clearTimeout(resetMessageTimer);
        }
    });

    function showResetMessage(message: { type: 'success' | 'error', text: string }, autoDismiss = message.type === 'success') {
        resetMessage = message;

        if (resetMessageTimer) {
            clearTimeout(resetMessageTimer);
            resetMessageTimer = null;
        }

        if (autoDismiss) {
            resetMessageTimer = setTimeout(() => {
                resetMessage = null;
                resetMessageTimer = null;
            }, SUCCESS_MESSAGE_AUTO_DISMISS_MS);
        }
    }

    function clearResetMessage() {
        resetMessage = null;
        if (resetMessageTimer) {
            clearTimeout(resetMessageTimer);
            resetMessageTimer = null;
        }
    }

    function openResetDialog() {
        if (isResetting || isExportingLogs) return;
        clearResetMessage();
        resetDialogOpen = true;
    }

    function closeResetDialog() {
        if (isResetting || isExportingLogs) return;
        resetDialogOpen = false;
    }

    function openExportDialog() {
        if (isResetting || isExportingLogs) return;
        clearResetMessage();
        exportDialogOpen = true;
    }

    function closeExportDialog() {
        if (isResetting || isExportingLogs) return;
        exportDialogOpen = false;
    }

    function createDefaultExportTypeSelection(): Record<ArchiveEventType, boolean> {
        return {
            AGENT_MOVED: true,
            TRANSACTION: true,
            UI_UPSERT: true,
            UI_DELETE: true
        };
    }

    function setExportTypeSelection(type: ArchiveEventType, selected: boolean) {
        exportTypeSelection = { ...exportTypeSelection, [type]: selected };
    }

    function selectAllExportTypes() {
        exportTypeSelection = createDefaultExportTypeSelection();
    }

    function selectAgentFocusedExportTypes() {
        exportTypeSelection = {
            AGENT_MOVED: true,
            TRANSACTION: true,
            UI_UPSERT: false,
            UI_DELETE: false
        };
    }

    function eventTypeCount(type: ArchiveEventType): number {
        return mazeState.archiveTypeCounts[type] ?? 0;
    }

    function visibleHotRows(totalRows: number, coldRows: number): number {
        return Math.max(0, totalRows - coldRows);
    }

    function formatArchiveRunId(runId: string): string {
        return runId.replace(/^run-/, '');
    }

    function resolveServerResourceUrl(resourceUri: string): string {
        try {
            const publicBase = new URL(data.serverConfig.httpBaseUrl);
            const resource = new URL(resourceUri, publicBase);

            if (resource.origin !== publicBase.origin && isMazeDereferencePath(resource.pathname) && isLikelyInternalHost(resource.hostname)) {
                return new URL(`${resource.pathname}${resource.search}${resource.hash}`, publicBase).toString();
            }

            return resource.toString();
        } catch {
            return resourceUri;
        }
    }

    function isMazeDereferencePath(pathname: string): boolean {
        return pathname.startsWith('/cells/') || pathname.startsWith('/agents/');
    }

    function isLikelyInternalHost(hostname: string): boolean {
        const host = hostname.toLowerCase();
        return host === 'localhost'
            || host === '0.0.0.0'
            || host.startsWith('127.')
            || host.startsWith('10.')
            || host.startsWith('192.168.')
            || /^172\.(1[6-9]|2\d|3[0-1])\./.test(host)
            || !host.includes('.');
    }

    function selectedEventTypesForExport(): ArchiveEventType[] {
        return [...selectedExportTypes];
    }

    function summarizeExportSelection(count: number, fileName?: string): string {
        const selectedLabels = selectedExportTypes.map((type) => EVENT_TYPE_LABELS[type]).join(', ');
        return count > 0
            ? `Exported ${count} log events${fileName ? ` to ${fileName}` : ''}${selectedLabels ? ` (${selectedLabels})` : ''}.`
            : 'No logs were available to export for the selected event types.';
    }

    async function handleAdminReset(action: 'export' | 'discard') {
        if (isResetting || isExportingLogs) return;

        const eventTypes = action === 'export' ? selectedEventTypesForExport() : [];
        if (action === 'export' && eventTypes.length === 0) {
            showResetMessage({ type: 'error', text: 'Select at least one event type before exporting logs.' }, false);
            return;
        }

        isResetting = true;
        pendingOperationText = action === 'export'
            ? 'Preparing and saving selected log export...'
            : 'Resetting store and refreshing viewer...';
        clearResetMessage();

        try {
            let exportedCount: number | null = null;
            let exportFileName: string | undefined;

            if (action === 'export') {
                isExportingLogs = true;
                const exportResult = await mazeState.exportLogsNdjson(eventTypes);
                isExportingLogs = false;

                if (exportResult.status === 'canceled') {
                    resetDialogOpen = false;
                    showResetMessage({ type: 'error', text: 'Reset canceled. Log export was canceled.' }, false);
                    return;
                }

                if (exportResult.status === 'unavailable') {
                    resetDialogOpen = false;
                    showResetMessage({ type: 'error', text: exportResult.message ?? 'Reset canceled. Log export is unavailable.' }, false);
                    return;
                }

                exportedCount = exportResult.count;
                exportFileName = exportResult.fileName;
                pendingOperationText = 'Resetting store and refreshing viewer...';
            }

            const response = await fetch(data.serverConfig.adminResetUrl, {
                method: 'POST',
                headers: {
                    'Accept': 'application/json'
                }
            });

            if (response.ok) {
                await resetViewerState();
                resetDialogOpen = false;

                if (action === 'export' && exportedCount !== null) {
                    showResetMessage({ type: 'success', text: `Reset completed. ${summarizeExportSelection(exportedCount, exportFileName)}` });
                } else {
                    showResetMessage({ type: 'success', text: 'Reset completed. Logs discarded.' });
                }
            } else {
                const errorText = await response.text();
                resetDialogOpen = false;
                showResetMessage({
                    type: 'error',
                    text: `Reset failed (${response.status}): ${errorText || response.statusText}`
                }, false);
            }
        } catch (e) {
            resetDialogOpen = false;
            showResetMessage({
                type: 'error',
                text: `Reset failed: ${e instanceof Error ? e.message : String(e)}`
            }, false);
        } finally {
            pendingOperationText = null;
            isExportingLogs = false;
            isResetting = false;
        }
    }

    async function resetViewerState() {
        selectedCellData = null;
        selectedCellId = null;
        isLoadingCell = false;
        selectedAgentData = null;
        selectedAgentId = null;
        isLoadingAgent = false;
        turtleInput = '';
        isPosting = false;
        postMessage = null;
        eventFilterText = '';

        await mazeState.resetForNewRun();
        await invalidateAll();
        canvasRevision += 1;
    }

    function handleClearTables() {
        mazeState.clearTables();
    }

    async function handleExportLogs() {
        if (isExportingLogs || isResetting) return;

        clearResetMessage();

        try {
            const eventTypes = selectedEventTypesForExport();
            if (eventTypes.length === 0) {
                showResetMessage({ type: 'error', text: 'Select at least one event type before exporting logs.' }, false);
                return;
            }

            isExportingLogs = true;
            pendingOperationText = 'Preparing and saving selected log export...';
            const exportResult = await mazeState.exportLogsNdjson(eventTypes);

            if (exportResult.status === 'canceled') {
                exportDialogOpen = false;
                showResetMessage({ type: 'error', text: 'Log export canceled.' }, false);
                return;
            }

            if (exportResult.status === 'unavailable') {
                exportDialogOpen = false;
                showResetMessage({ type: 'error', text: exportResult.message ?? 'Log export is unavailable.' }, false);
                return;
            }

            if (exportResult.status === 'empty') {
                exportDialogOpen = false;
                showResetMessage({ type: 'success', text: 'No logs available to export for the selected event types.' });
                return;
            }

            showResetMessage({
                type: 'success',
                text: summarizeExportSelection(exportResult.count, exportResult.fileName)
            });
            exportDialogOpen = false;
        } catch (e) {
            showResetMessage({
                type: 'error',
                text: `Log export failed: ${e instanceof Error ? e.message : String(e)}`
            }, false);
        } finally {
            pendingOperationText = null;
            isExportingLogs = false;
        }
    }

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
        const usedPrefixes: string[] = [];
        const content = contentLines.join('\n');
        
        prefixLines.forEach(({ prefix }) => {
            // Check if prefix is used with colon notation (e.g., "maze:", "rdf:")
            const prefixPattern = new RegExp(`\\b${prefix}:`, 'g');
            if (prefixPattern.test(content) && !usedPrefixes.includes(prefix)) {
                usedPrefixes.push(prefix);
            }
        });
        
        // Rebuild with only used prefixes
        const usedPrefixLines = prefixLines
            .filter(({ prefix }) => usedPrefixes.includes(prefix))
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
            const response = await fetch(resolveServerResourceUrl(cellId), {
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
            const response = await fetch(resolveServerResourceUrl(agentUri), {
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
            const response = await fetch(resolveServerResourceUrl(selectedAgentId), {
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

<div class="grid grid-cols-[repeat(auto-fit,minmax(min(100%,32rem),1fr))] items-start gap-6 p-4">
    <!-- Left Column: Maze Visualization -->
    <div class="flex min-w-0 flex-col gap-6">
        <div class="flex flex-wrap items-center justify-between gap-3">
            <div class="flex min-w-0 flex-wrap items-center gap-3">
                <h1 class="text-2xl font-bold">Maze Viewer</h1>
                {#if data.scenarioName}
                    <span class="px-2 py-1 text-xs font-semibold rounded bg-blue-50 text-blue-700 border border-blue-200">
                        Scenario: {data.scenarioName}
                    </span>
                {/if}
            </div>
            <div class="flex shrink-0 flex-wrap items-center justify-end gap-2">
                <button
                    onclick={openResetDialog}
                    class="inline-flex items-center justify-center gap-2 rounded border border-red-300 bg-red-50 px-3 py-1.5 text-sm font-medium text-red-700 hover:bg-red-100 disabled:cursor-not-allowed disabled:opacity-60"
                    type="button"
                    title="Reset RDF store"
                    disabled={isResetting || isExportingLogs}
                >
                    {#if isResetting || isExportingLogs}
                        <span class="h-4 w-4 animate-spin rounded-full border-2 border-current border-r-transparent" aria-hidden="true"></span>
                    {/if}
                    {isResetting ? 'Working...' : isExportingLogs ? 'Exporting...' : 'Reset Store'}
                </button>
                <button
                    onclick={() => showOptimalRoute.update((v) => !v)}
                    class="px-3 py-1.5 text-sm rounded border border-gray-300 bg-white hover:bg-gray-50"
                    type="button"
                >
                    {$showOptimalRoute ? 'Hide Optimal Route' : 'Show Optimal Route'}
                </button>
            </div>
        </div>

        {#if resetMessage}
            <div class={resetMessage.type === 'success' ? 'flex items-start justify-between gap-3 rounded border border-green-200 bg-green-50 px-3 py-2 text-sm text-green-700' : 'flex items-start justify-between gap-3 rounded border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700 break-words'}>
                <span>{resetMessage.text}</span>
                <button
                    type="button"
                    class="shrink-0 rounded px-1 text-current hover:bg-white/60"
                    title="Close message"
                    onclick={clearResetMessage}
                >
                    x
                </button>
            </div>
        {/if}
        
        {#if data.maze}
            <div class="h-[600px] min-w-0 max-w-full resize overflow-hidden rounded border-2 border-gray-300 bg-white">
                {#key canvasRevision}
                    <MazeCanvas maze={data.maze} uiSnapshot={data.uiSnapshot || []} scenarioName={data.scenarioName} onCellSelect={handleCellSelect} />
                {/key}
            </div>
        {:else}
            <div class="p-8 bg-gray-100 rounded text-center text-gray-500">
                Loading maze layout... (Ensure server is running at {data.serverConfig.httpBaseUrl})
                {#if data.loadError}
                    <div class="mt-2 text-sm text-red-600">{data.loadError}</div>
                {/if}
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
    <div class="flex min-w-0 flex-col gap-4">
        <div class="flex justify-between items-center">
            <h2 class="text-xl font-bold">Live Events</h2>
            <span class:text-green-600={mazeState.status === 'connected'} 
                  class:text-red-600={mazeState.status === 'disconnected' || mazeState.status === 'error'}
                  class="font-bold text-sm uppercase">
                {mazeState.status}
            </span>
        </div>

        <div class="flex flex-wrap gap-x-3 gap-y-1 text-xs text-gray-500">
            <span class="font-medium text-gray-600">Archive: {mazeState.archiveCount}</span>
            <span class="font-mono" title={mazeState.archiveRunId}>Run: {formatArchiveRunId(mazeState.archiveRunId)}</span>
            {#each ARCHIVE_EVENT_TYPES as type}
                <span>{EVENT_TYPE_LABELS[type]}: {eventTypeCount(type)}</span>
            {/each}
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
                onclick={handleClearTables}
            >
                Clear Tables
            </button>
            <button
                type="button"
                class="inline-flex items-center justify-center gap-2 rounded border border-gray-300 bg-white px-3 py-2 text-sm hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-60"
                onclick={openExportDialog}
                disabled={isExportingLogs || isResetting}
            >
                {#if isExportingLogs}
                    <span class="h-4 w-4 animate-spin rounded-full border-2 border-current border-r-transparent" aria-hidden="true"></span>
                {/if}
                {isExportingLogs ? 'Exporting...' : 'Export Logs'}
            </button>
        </div>

        <div>
            <h3 class="font-semibold mb-2 text-gray-700">Agent Movements</h3>
            <AgentEventLog
                events={mazeState.agentEvents}
                filterText={eventFilterText}
                onAgentSelect={handleAgentSelect}
                hasMore={mazeState.agentEventsHasMore}
                isLoadingMore={mazeState.isLoadingAgentEvents}
                onLoadMore={() => mazeState.loadMoreAgentEvents(eventFilterText)}
                totalCount={eventTypeCount('AGENT_MOVED')}
                hotCount={visibleHotRows(mazeState.agentEvents.length, mazeState.agentColdRowsLoaded)}
                coldCount={mazeState.agentColdRowsLoaded}
                archiveRunId={formatArchiveRunId(mazeState.archiveRunId)}
            />
        </div>

        <div>
            <h3 class="font-semibold mb-2 text-gray-700">Cell Updates</h3>
            <CellEventLog
                events={mazeState.transactionEvents}
                filterText={eventFilterText}
                hasMore={mazeState.transactionEventsHasMore}
                isLoadingMore={mazeState.isLoadingTransactionEvents}
                onLoadMore={() => mazeState.loadMoreTransactionEvents(eventFilterText)}
                totalCount={eventTypeCount('TRANSACTION')}
                hotCount={visibleHotRows(mazeState.transactionEvents.length, mazeState.transactionColdRowsLoaded)}
                coldCount={mazeState.transactionColdRowsLoaded}
                archiveRunId={formatArchiveRunId(mazeState.archiveRunId)}
            />
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

{#if resetDialogOpen}
    <div class="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
        <div class="w-full max-w-md rounded border border-gray-200 bg-white p-4 shadow-xl">
            <div class="mb-4">
                <h2 class="text-lg font-semibold text-gray-900">Reset Store</h2>
                <p class="mt-1 text-sm text-gray-600">Export current logs before resetting?</p>
            </div>

            <div class="mb-4 rounded border border-gray-200 bg-gray-50 p-3">
                <div class="mb-2 flex flex-wrap items-center justify-between gap-2">
                    <div class="text-sm font-medium text-gray-700">Export Event Types</div>
                    <div class="flex gap-2">
                        <button
                            type="button"
                            class="rounded border border-gray-300 bg-white px-2 py-1 text-xs hover:bg-gray-50"
                            onclick={selectAllExportTypes}
                            disabled={isResetting || isExportingLogs}
                        >
                            All
                        </button>
                        <button
                            type="button"
                            class="rounded border border-gray-300 bg-white px-2 py-1 text-xs hover:bg-gray-50"
                            onclick={selectAgentFocusedExportTypes}
                            disabled={isResetting || isExportingLogs}
                        >
                            Agent-focused
                        </button>
                    </div>
                </div>
                <div class="grid grid-cols-1 gap-2 sm:grid-cols-2">
                    {#each ARCHIVE_EVENT_TYPES as type}
                        <label class="flex items-center justify-between gap-2 rounded border border-gray-200 bg-white px-2 py-1.5 text-sm">
                            <span class="flex items-center gap-2">
                                <input
                                    type="checkbox"
                                    checked={exportTypeSelection[type]}
                                    onchange={(event) => setExportTypeSelection(type, (event.currentTarget as HTMLInputElement).checked)}
                                    disabled={isResetting || isExportingLogs}
                                />
                                <span>{EVENT_TYPE_LABELS[type]}</span>
                            </span>
                            <span class="text-xs text-gray-500">{eventTypeCount(type)}</span>
                        </label>
                    {/each}
                </div>
                {#if pendingOperationText}
                    <div class="mt-3 flex items-center gap-2 rounded border border-blue-200 bg-blue-50 px-2 py-1.5 text-sm text-blue-700">
                        <span class="h-4 w-4 animate-spin rounded-full border-2 border-current border-r-transparent" aria-hidden="true"></span>
                        <span>{pendingOperationText}</span>
                    </div>
                {/if}
            </div>

            <div class="flex flex-wrap justify-end gap-2">
                <button
                    type="button"
                    class="rounded border border-gray-300 bg-white px-3 py-2 text-sm hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-60"
                    onclick={closeResetDialog}
                    disabled={isResetting || isExportingLogs}
                >
                    Cancel
                </button>
                <button
                    type="button"
                    class="rounded border border-red-300 bg-red-50 px-3 py-2 text-sm font-medium text-red-700 hover:bg-red-100 disabled:cursor-not-allowed disabled:opacity-60"
                    onclick={() => handleAdminReset('discard')}
                    disabled={isResetting || isExportingLogs}
                >
                    {isResetting && !isExportingLogs ? 'Working...' : 'Discard Logs'}
                </button>
                <button
                    type="button"
                    class="inline-flex items-center justify-center gap-2 rounded bg-blue-600 px-3 py-2 text-sm font-medium text-white hover:bg-blue-700 disabled:cursor-not-allowed disabled:bg-gray-300"
                    onclick={() => handleAdminReset('export')}
                    disabled={isResetting || isExportingLogs || !canExportSelectedTypes}
                >
                    {#if isResetting || isExportingLogs}
                        <span class="h-4 w-4 animate-spin rounded-full border-2 border-current border-r-transparent" aria-hidden="true"></span>
                    {/if}
                    {isExportingLogs ? 'Exporting...' : isResetting ? 'Working...' : 'Export Logs'}
                </button>
            </div>
        </div>
    </div>
{/if}

{#if exportDialogOpen}
    <div class="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
        <div class="w-full max-w-md rounded border border-gray-200 bg-white p-4 shadow-xl">
            <div class="mb-4">
                <h2 class="text-lg font-semibold text-gray-900">Export Logs</h2>
            </div>

            <div class="mb-4 rounded border border-gray-200 bg-gray-50 p-3">
                <div class="mb-2 flex flex-wrap items-center justify-between gap-2">
                    <div class="text-sm font-medium text-gray-700">Event Types</div>
                    <div class="flex gap-2">
                        <button
                            type="button"
                            class="rounded border border-gray-300 bg-white px-2 py-1 text-xs hover:bg-gray-50"
                            onclick={selectAllExportTypes}
                            disabled={isExportingLogs}
                        >
                            All
                        </button>
                        <button
                            type="button"
                            class="rounded border border-gray-300 bg-white px-2 py-1 text-xs hover:bg-gray-50"
                            onclick={selectAgentFocusedExportTypes}
                            disabled={isExportingLogs}
                        >
                            Agent-focused
                        </button>
                    </div>
                </div>
                <div class="grid grid-cols-1 gap-2 sm:grid-cols-2">
                    {#each ARCHIVE_EVENT_TYPES as type}
                        <label class="flex items-center justify-between gap-2 rounded border border-gray-200 bg-white px-2 py-1.5 text-sm">
                            <span class="flex items-center gap-2">
                                <input
                                    type="checkbox"
                                    checked={exportTypeSelection[type]}
                                    onchange={(event) => setExportTypeSelection(type, (event.currentTarget as HTMLInputElement).checked)}
                                    disabled={isExportingLogs}
                                />
                                <span>{EVENT_TYPE_LABELS[type]}</span>
                            </span>
                            <span class="text-xs text-gray-500">{eventTypeCount(type)}</span>
                        </label>
                    {/each}
                </div>
                {#if pendingOperationText}
                    <div class="mt-3 flex items-center gap-2 rounded border border-blue-200 bg-blue-50 px-2 py-1.5 text-sm text-blue-700">
                        <span class="h-4 w-4 animate-spin rounded-full border-2 border-current border-r-transparent" aria-hidden="true"></span>
                        <span>{pendingOperationText}</span>
                    </div>
                {/if}
            </div>

            <div class="flex flex-wrap justify-end gap-2">
                <button
                    type="button"
                    class="rounded border border-gray-300 bg-white px-3 py-2 text-sm hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-60"
                    onclick={closeExportDialog}
                    disabled={isExportingLogs}
                >
                    Cancel
                </button>
                <button
                    type="button"
                    class="inline-flex items-center justify-center gap-2 rounded bg-blue-600 px-3 py-2 text-sm font-medium text-white hover:bg-blue-700 disabled:cursor-not-allowed disabled:bg-gray-300"
                    onclick={handleExportLogs}
                    disabled={isExportingLogs || !canExportSelectedTypes}
                >
                    {#if isExportingLogs}
                        <span class="h-4 w-4 animate-spin rounded-full border-2 border-current border-r-transparent" aria-hidden="true"></span>
                    {/if}
                    {isExportingLogs ? 'Exporting...' : 'Export Logs'}
                </button>
            </div>
        </div>
    </div>
{/if}
