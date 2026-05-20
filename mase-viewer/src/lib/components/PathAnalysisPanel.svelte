<script lang="ts">
    import { onMount } from 'svelte';
    import { eventArchive, type MovementPathSource } from '$lib/eventArchive';
    import {
        agentMovementEventsToPath,
        parsePathAnalysisInput,
        shortCellLabel,
        type PathAnalysisStep
    } from '$lib/pathAnalysis';
    import { pathAnalysisOverlay } from '$lib/pathAnalysisOverlayStore';
    import type { MazeLayout } from '$lib/types';

    type SourceMode = 'paste' | 'archive';
    type PanelMessage = { type: 'success' | 'error'; text: string };

    let {
        maze,
        disabled = false,
        onClose
    } = $props<{
        maze: MazeLayout;
        disabled?: boolean;
        onClose?: () => void;
    }>();

    let sourceMode = $state<SourceMode>('paste');
    let pastedInput = $state('');
    let sources = $state<MovementPathSource[]>([]);
    let selectedRunId = $state('');
    let selectedAgent = $state('');
    let isLoadingSources = $state(false);
    let isRendering = $state(false);
    let archiveError = $state<string | null>(null);
    let message = $state<PanelMessage | null>(null);

    const runIds = $derived.by(() => uniqueStrings(sources.map((source) => source.runId)));
    const selectedRunSources = $derived.by(() => sources.filter((source) => source.runId === selectedRunId));
    const selectedSource = $derived.by(() => selectedRunSources.find((source) => source.agent === selectedAgent) ?? null);
    const maxRenderedStep = $derived.by(() => {
        const steps = $pathAnalysisOverlay.steps;
        return steps.length > 0 ? steps[steps.length - 1].step : 0;
    });
    const selectedStep = $derived.by(() => {
        const currentStep = $pathAnalysisOverlay.currentStep;
        if (currentStep === null) {
            return null;
        }

        return $pathAnalysisOverlay.steps.find((step) => step.step === currentStep) ?? null;
    });

    onMount(() => {
        void loadSources();
    });

    async function loadSources() {
        isLoadingSources = true;
        archiveError = null;

        try {
            sources = await eventArchive.listMovementPathSources();
            reconcileArchiveSelection();
        } catch (error) {
            archiveError = error instanceof Error ? error.message : String(error);
        } finally {
            isLoadingSources = false;
        }
    }

    function selectSourceMode(mode: SourceMode) {
        sourceMode = mode;
        message = null;
        if (mode === 'archive' && sources.length === 0) {
            void loadSources();
        }
    }

    function selectRun(runId: string) {
        selectedRunId = runId;
        const firstAgent = sources.find((source) => source.runId === runId)?.agent ?? '';
        selectedAgent = firstAgent;
    }

    function reconcileArchiveSelection() {
        if (sources.length === 0) {
            selectedRunId = '';
            selectedAgent = '';
            return;
        }

        if (!selectedRunId || !sources.some((source) => source.runId === selectedRunId)) {
            selectedRunId = sources[0].runId;
        }

        if (!selectedAgent || !sources.some((source) => source.runId === selectedRunId && source.agent === selectedAgent)) {
            selectedAgent = sources.find((source) => source.runId === selectedRunId)?.agent ?? '';
        }
    }

    async function renderArchivePath() {
        if (!selectedRunId || !selectedAgent || disabled || isRendering) {
            return;
        }

        isRendering = true;
        message = null;

        try {
            const events = await eventArchive.getAgentMovementPath(selectedRunId, selectedAgent);
            const result = agentMovementEventsToPath(events, maze);

            if (result.steps.length === 0) {
                pathAnalysisOverlay.clearPath();
                message = {
                    type: 'error',
                    text: events.length === 0
                        ? 'No archived movement events matched that run and agent.'
                        : 'Archived movement cells do not match the current maze.'
                };
                return;
            }

            const label = `${formatArchiveRunId(selectedRunId)} / ${formatAgent(selectedAgent)}`;
            pathAnalysisOverlay.setPath(result.steps, result.invalid, label);
            message = {
                type: result.invalid.length > 0 ? 'error' : 'success',
                text: result.invalid.length > 0
                    ? `Rendered ${result.steps.length} steps; skipped ${result.invalid.length} missing cells.`
                    : `Rendered ${result.steps.length} archived steps.`
            };
        } catch (error) {
            message = {
                type: 'error',
                text: `Archive read failed: ${error instanceof Error ? error.message : String(error)}`
            };
        } finally {
            isRendering = false;
        }
    }

    function renderPastedPath() {
        if (disabled || isRendering) {
            return;
        }

        message = null;

        if (!pastedInput.trim()) {
            pathAnalysisOverlay.clearPath();
            message = { type: 'error', text: 'Paste at least one cell path.' };
            return;
        }

        const result = parsePathAnalysisInput(pastedInput, maze);
        if (result.steps.length === 0) {
            pathAnalysisOverlay.clearPath();
            message = { type: 'error', text: 'No pasted cells matched the current maze.' };
            return;
        }

        pathAnalysisOverlay.setPath(result.steps, result.invalid, 'Pasted sequence');
        message = {
            type: result.invalid.length > 0 ? 'error' : 'success',
            text: result.invalid.length > 0
                ? `Rendered ${result.steps.length} steps; skipped ${result.invalid.length} missing cells.`
                : `Rendered ${result.steps.length} pasted steps.`
        };
    }

    function renderCurrentMode() {
        if (sourceMode === 'archive') {
            void renderArchivePath();
        } else {
            renderPastedPath();
        }
    }

    function clearOverlay() {
        pathAnalysisOverlay.clearPath();
        message = null;
    }

    function handleCurrentStepInput(event: Event) {
        const value = Number((event.currentTarget as HTMLInputElement).value);
        if (!Number.isNaN(value)) {
            pathAnalysisOverlay.setCurrentStep(value);
        }
    }

    function formatArchiveRunId(runId: string): string {
        return runId.replace(/^run-/, '');
    }

    function formatAgent(agent: string): string {
        const marker = '/agents/';
        const markerIndex = agent.indexOf(marker);
        if (markerIndex !== -1) {
            return agent.substring(markerIndex + marker.length);
        }

        const segments = agent.split(/[/#]/).filter(Boolean);
        return segments[segments.length - 1] ?? agent;
    }

    function formatTimestamp(timestamp?: number): string {
        if (!timestamp) {
            return 'unknown time';
        }

        return new Date(timestamp).toLocaleTimeString();
    }

    function sourceDescription(source: MovementPathSource): string {
        return `${source.count} moves, ${formatTimestamp(source.firstTimestamp)} - ${formatTimestamp(source.lastTimestamp)}`;
    }

    function stepDescription(step: PathAnalysisStep): string {
        const parts = [`Step ${step.step}`, shortCellLabel(step.cellId)];
        if (step.agent) {
            parts.push(formatAgent(step.agent));
        }
        if (step.timestamp) {
            parts.push(formatTimestamp(step.timestamp));
        }
        return parts.join(' | ');
    }

    function uniqueStrings(values: string[]): string[] {
        return [...new Set(values)];
    }
</script>

<div class="rounded border border-gray-200 bg-white p-4 shadow-sm">
    <div class="mb-3 flex flex-wrap items-center justify-between gap-2">
        <div>
            <h2 class="text-lg font-semibold text-gray-900">Path Analysis</h2>
            {#if $pathAnalysisOverlay.sourceLabel}
                <div class="mt-1 max-w-full truncate text-xs text-gray-500">{$pathAnalysisOverlay.sourceLabel}</div>
            {/if}
        </div>
        <div class="flex gap-2">
            <button
                type="button"
                class="rounded border border-gray-300 bg-white px-2 py-1 text-xs hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-60"
                onclick={() => pathAnalysisOverlay.setVisible(!$pathAnalysisOverlay.visible)}
                disabled={$pathAnalysisOverlay.steps.length === 0}
            >
                {$pathAnalysisOverlay.visible ? 'Hide' : 'Show'}
            </button>
            {#if onClose}
                <button
                    type="button"
                    class="rounded border border-gray-300 bg-white px-2 py-1 text-xs hover:bg-gray-50"
                    onclick={onClose}
                >
                    Close
                </button>
            {/if}
        </div>
    </div>

    <div class="mb-3 inline-flex rounded border border-gray-300 bg-gray-50 p-0.5 text-sm">
        <button
            type="button"
            class={sourceMode === 'paste' ? 'rounded bg-white px-3 py-1 font-medium text-gray-900 shadow-sm' : 'rounded px-3 py-1 text-gray-600 hover:text-gray-900'}
            onclick={() => selectSourceMode('paste')}
        >
            Pasted
        </button>
        <button
            type="button"
            class={sourceMode === 'archive' ? 'rounded bg-white px-3 py-1 font-medium text-gray-900 shadow-sm' : 'rounded px-3 py-1 text-gray-600 hover:text-gray-900'}
            onclick={() => selectSourceMode('archive')}
        >
            Archived
        </button>
    </div>

    {#if sourceMode === 'archive'}
        <div class="grid gap-3 sm:grid-cols-2">
            <label class="block text-sm">
                <span class="mb-1 block font-medium text-gray-700">Run</span>
                <select
                    class="w-full rounded border border-gray-300 bg-white px-2 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                    value={selectedRunId}
                    onchange={(event) => selectRun((event.currentTarget as HTMLSelectElement).value)}
                    disabled={disabled || isLoadingSources || runIds.length === 0}
                >
                    {#each runIds as runId (runId)}
                        <option value={runId}>{formatArchiveRunId(runId)}</option>
                    {/each}
                </select>
            </label>

            <label class="block text-sm">
                <span class="mb-1 block font-medium text-gray-700">Agent</span>
                <select
                    class="w-full rounded border border-gray-300 bg-white px-2 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                    value={selectedAgent}
                    onchange={(event) => selectedAgent = (event.currentTarget as HTMLSelectElement).value}
                    disabled={disabled || isLoadingSources || selectedRunSources.length === 0}
                >
                    {#each selectedRunSources as source (`${source.runId}:${source.agent}`)}
                        <option value={source.agent}>{formatAgent(source.agent)}</option>
                    {/each}
                </select>
            </label>
        </div>

        <div class="mt-2 flex flex-wrap items-center gap-2 text-xs text-gray-500">
            <button
                type="button"
                class="rounded border border-gray-300 bg-white px-2 py-1 hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-60"
                onclick={loadSources}
                disabled={disabled || isLoadingSources}
            >
                {isLoadingSources ? 'Refreshing...' : 'Refresh'}
            </button>
            {#if selectedSource}
                <span>{sourceDescription(selectedSource)}</span>
            {:else if !isLoadingSources}
                <span>No archived movement paths found.</span>
            {/if}
        </div>
    {:else}
        <label class="block text-sm">
            <span class="mb-1 block font-medium text-gray-700">Cell sequence</span>
            <textarea
                bind:value={pastedInput}
                class="h-36 w-full resize-y rounded border border-gray-300 bg-white p-2 font-mono text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                placeholder="/cells/1/1&#10;/cells/1/2"
                disabled={disabled}
            ></textarea>
        </label>
    {/if}

    <div class="mt-3 flex flex-wrap gap-2">
        <button
            type="button"
            class="inline-flex items-center justify-center gap-2 rounded bg-blue-600 px-3 py-2 text-sm font-medium text-white hover:bg-blue-700 disabled:cursor-not-allowed disabled:bg-gray-300"
            onclick={renderCurrentMode}
            disabled={disabled || isRendering || (sourceMode === 'archive' && (!selectedRunId || !selectedAgent))}
        >
            {isRendering ? 'Rendering...' : 'Render'}
        </button>
        <button
            type="button"
            class="rounded border border-gray-300 bg-white px-3 py-2 text-sm hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-60"
            onclick={() => pathAnalysisOverlay.requestFitToPath()}
            disabled={$pathAnalysisOverlay.steps.length === 0}
        >
            Fit
        </button>
        <button
            type="button"
            class="rounded border border-gray-300 bg-white px-3 py-2 text-sm hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-60"
            onclick={clearOverlay}
            disabled={$pathAnalysisOverlay.steps.length === 0}
        >
            Clear
        </button>
    </div>

    {#if maxRenderedStep > 0}
        <div class="mt-4 rounded border border-gray-200 bg-gray-50 p-3">
            <div class="mb-2 flex flex-wrap items-center justify-between gap-2 text-sm">
                <span class="font-medium text-gray-700">
                    {$pathAnalysisOverlay.steps.length} steps
                    {#if $pathAnalysisOverlay.invalid.length > 0}
                        / {$pathAnalysisOverlay.invalid.length} skipped
                    {/if}
                </span>
                <label class="flex items-center gap-2 text-xs text-gray-600">
                    <input
                        type="checkbox"
                        checked={$pathAnalysisOverlay.dimFutureSteps}
                        onchange={(event) => pathAnalysisOverlay.setDimFutureSteps((event.currentTarget as HTMLInputElement).checked)}
                    />
                    Dim future
                </label>
            </div>
            <input
                class="w-full"
                type="range"
                min="1"
                max={maxRenderedStep}
                value={$pathAnalysisOverlay.currentStep ?? maxRenderedStep}
                oninput={handleCurrentStepInput}
            />
            {#if selectedStep}
                <div class="mt-2 truncate text-xs text-gray-600" title={stepDescription(selectedStep)}>
                    {stepDescription(selectedStep)}
                </div>
            {/if}
        </div>
    {/if}

    {#if message}
        <div class={message.type === 'success' ? 'mt-3 rounded border border-green-200 bg-green-50 px-3 py-2 text-sm text-green-700' : 'mt-3 rounded border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-800'}>
            {message.text}
        </div>
    {/if}

    {#if archiveError}
        <div class="mt-3 rounded border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">
            Archive error: {archiveError}
        </div>
    {/if}

    {#if $pathAnalysisOverlay.invalid.length > 0}
        <div class="mt-3 text-xs text-gray-600">
            <div class="font-medium text-gray-700">Skipped cells</div>
            <ul class="mt-1 space-y-1">
                {#each $pathAnalysisOverlay.invalid.slice(0, 5) as invalid (`${invalid.index}:${invalid.value}`)}
                    <li class="truncate" title={`${invalid.index}: ${invalid.value}`}>
                        {invalid.index}: {invalid.value}
                    </li>
                {/each}
            </ul>
        </div>
    {/if}
</div>
