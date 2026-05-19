<script lang="ts">
    import { onMount } from 'svelte';

    let {
        serverHttpBaseUrl,
        disabled = false,
        onClose
    } = $props<{
        serverHttpBaseUrl: string;
        disabled?: boolean;
        onClose?: () => void;
    }>();

    interface DemoHttpTranscript {
        method: string;
        url: string;
        requestHeaders: Record<string, string>;
        requestBody: string | null;
        responseStatus: number;
        responseHeaders: Record<string, string[]>;
        responseBody: string | null;
    }

    interface DemoAgentStepResponse {
        step: number;
        status: string;
        agent: string;
        currentCell: string | null;
        phase: string;
        decision: string;
        complete: boolean;
        transcript: DemoHttpTranscript | null;
        error: string | null;
    }

    interface DemoFetchOptions {
        method: 'GET' | 'POST';
        headers?: Record<string, string>;
        body?: string;
    }

    let agentName = $state('demo-agent');
    let preferGreenSignifiers = $state(true);
    let demoState = $state<DemoAgentStepResponse | null>(null);
    let isBusy = $state(false);
    let panelError = $state<string | null>(null);

    const isActionDisabled = $derived(disabled || isBusy);
    const isNextDisabled = $derived.by(() => isActionDisabled || demoState?.complete === true || demoState?.status === 'error');

    onMount(() => {
        void refreshState();
    });

    function endpoint(path: string): string {
        return `${serverHttpBaseUrl.replace(/\/+$/, '')}/admin/demo-agent${path}`;
    }

    async function refreshState(): Promise<void> {
        await callDemoEndpoint('/state', { method: 'GET' });
    }

    async function resetDemoAgent(): Promise<void> {
        await callDemoEndpoint('/reset', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                agentName,
                preferGreenSignifiers
            })
        });
    }

    async function nextRequest(): Promise<void> {
        await callDemoEndpoint('/next', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                preferGreenSignifiers
            })
        });
    }

    async function callDemoEndpoint(path: string, init: DemoFetchOptions): Promise<void> {
        if (isBusy) return;
        isBusy = true;
        panelError = null;

        try {
            const response = await fetch(endpoint(path), {
                method: init.method,
                headers: {
                    'Accept': 'application/json',
                    ...(init.headers ?? {})
                },
                body: init.body
            });
            const body = await response.text();
            if (!response.ok) {
                throw new Error(body || `${response.status} ${response.statusText}`);
            }
            demoState = JSON.parse(body) as DemoAgentStepResponse;
            agentName = demoState.agent || agentName;
        } catch (error) {
            panelError = error instanceof Error ? error.message : String(error);
        } finally {
            isBusy = false;
        }
    }

    function shortCell(cell: string | null): string {
        if (!cell) return '-';
        const marker = '/cells/';
        const index = cell.indexOf(marker);
        return index >= 0 ? cell.slice(index + marker.length) : cell;
    }

    function responseContentType(transcript: DemoHttpTranscript): string {
        return transcript.responseHeaders['content-type']?.[0]
            ?? transcript.responseHeaders['Content-Type']?.[0]
            ?? '-';
    }

    function formatHeaders(headers: Record<string, string>): string {
        return Object.entries(headers)
            .map(([name, value]) => `${name}: ${value}`)
            .join('\n');
    }

    function cleanUnusedPrefixes(turtle: string): string {
        const lines = turtle.split('\n');
        const prefixLines: { prefix: string; line: string }[] = [];
        const contentLines: string[] = [];

        for (const line of lines) {
            const prefixMatch = line.match(/^@prefix\s+([A-Za-z][\w-]*):/);
            if (prefixMatch) {
                prefixLines.push({ prefix: prefixMatch[1], line });
            } else {
                contentLines.push(line);
            }
        }

        if (prefixLines.length === 0) {
            return turtle;
        }

        const content = contentLines.join('\n');
        const usedPrefixLines = prefixLines
            .filter(({ prefix }) => new RegExp(`\\b${prefix}:`).test(content))
            .map(({ line }) => line);

        return usedPrefixLines.length > 0
            ? [...usedPrefixLines, '', ...contentLines].join('\n')
            : contentLines.join('\n');
    }

    function formatTurtleBody(body: string | null): string {
        return body ? cleanUnusedPrefixes(body) : '';
    }
</script>

<div class="flex max-h-[32rem] flex-col rounded-lg border bg-white shadow-sm">
    <div class="mb-3 flex flex-wrap items-center justify-between gap-2">
        <div class="min-w-0 p-4 pb-0">
            <h3 class="font-semibold text-gray-800">Demo Agent</h3>
            <div class="text-xs uppercase tracking-wide text-gray-500">{demoState?.phase ?? 'READY'}</div>
        </div>
        <div class="flex items-center gap-2 p-4 pb-0">
            <div class={demoState?.status === 'error' ? 'rounded bg-red-50 px-2 py-1 text-xs font-semibold text-red-700' : demoState?.complete ? 'rounded bg-green-50 px-2 py-1 text-xs font-semibold text-green-700' : 'rounded bg-blue-50 px-2 py-1 text-xs font-semibold text-blue-700'}>
                {demoState?.status ?? 'ready'}
            </div>
            {#if onClose}
                <button
                    type="button"
                    class="flex h-8 w-8 items-center justify-center rounded text-lg leading-none text-gray-500 hover:bg-gray-100 hover:text-gray-700"
                    title="Close Demo Agent"
                    aria-label="Close Demo Agent"
                    onclick={onClose}
                >
                    x
                </button>
            {/if}
        </div>
    </div>

    <div class="overflow-auto p-4 pt-0">
        <div class="grid grid-cols-1 gap-3 sm:grid-cols-[1fr_auto]">
            <label class="min-w-0 text-sm text-gray-700">
                <span class="mb-1 block font-medium">Agent</span>
                <input
                    bind:value={agentName}
                    class="w-full rounded border border-gray-300 px-2 py-1.5 font-mono text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                    disabled={isActionDisabled}
                />
            </label>
            <label class="flex items-end gap-2 pb-1 text-sm text-gray-700">
                <input
                    type="checkbox"
                    bind:checked={preferGreenSignifiers}
                    disabled={isActionDisabled}
                />
                <span>Prefer green signifiers</span>
            </label>
        </div>

        <div class="mt-3 flex flex-wrap gap-2">
            <button
                type="button"
                class="rounded border border-gray-300 bg-white px-3 py-1.5 text-sm hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-60"
                onclick={resetDemoAgent}
                disabled={isActionDisabled}
            >
                {isBusy ? 'Working...' : 'Reset Demo Agent'}
            </button>
            <button
                type="button"
                class="rounded bg-blue-600 px-3 py-1.5 text-sm font-medium text-white hover:bg-blue-700 disabled:cursor-not-allowed disabled:bg-gray-300"
                onclick={nextRequest}
                disabled={isNextDisabled}
            >
                Next Request
            </button>
        </div>

        {#if panelError}
            <div class="mt-3 rounded border border-red-200 bg-red-50 p-2 text-sm text-red-700">{panelError}</div>
        {/if}

        {#if demoState}
            <div class="mt-3 grid grid-cols-1 gap-2 text-sm sm:grid-cols-3">
                <div class="rounded border bg-gray-50 p-2">
                    <div class="text-xs text-gray-500">Step</div>
                    <div class="font-semibold">{demoState.step}</div>
                </div>
                <div class="rounded border bg-gray-50 p-2">
                    <div class="text-xs text-gray-500">Current Cell</div>
                    <div class="break-all font-mono text-xs">{shortCell(demoState.currentCell)}</div>
                </div>
                <div class="rounded border bg-gray-50 p-2">
                    <div class="text-xs text-gray-500">Agent</div>
                    <div class="break-all font-mono text-xs">{demoState.agent}</div>
                </div>
            </div>

            <div class={demoState.error ? 'mt-3 rounded border border-red-200 bg-red-50 p-2 text-sm text-red-700' : 'mt-3 rounded border border-blue-100 bg-blue-50 p-2 text-sm text-blue-800'}>
                {demoState.decision}
            </div>
        {/if}

        {#if demoState?.transcript}
            <div class="mt-3 grid grid-cols-1 gap-3 xl:grid-cols-2">
                <div>
                    <div class="mb-1 text-sm font-semibold text-gray-700">Request</div>
                    <div class="rounded border bg-gray-950 p-2 text-xs text-gray-100">
                        <div class="break-all font-semibold">{demoState.transcript.method} {demoState.transcript.url}</div>
                        <pre class="mt-2 max-h-24 overflow-auto whitespace-pre-wrap">{formatHeaders(demoState.transcript.requestHeaders)}</pre>
                        {#if demoState.transcript.requestBody}
                            <pre class="mt-2 max-h-32 overflow-auto whitespace-pre-wrap border-t border-gray-700 pt-2">{formatTurtleBody(demoState.transcript.requestBody)}</pre>
                        {/if}
                    </div>
                </div>
                <div>
                    <div class="mb-1 text-sm font-semibold text-gray-700">Response</div>
                    <div class="rounded border bg-gray-950 p-2 text-xs text-gray-100">
                        <div class="font-semibold">HTTP {demoState.transcript.responseStatus}</div>
                        <div class="mt-1 break-all text-gray-300">Content-Type: {responseContentType(demoState.transcript)}</div>
                        {#if demoState.transcript.responseBody}
                            <pre class="mt-2 max-h-40 overflow-auto whitespace-pre-wrap border-t border-gray-700 pt-2">{formatTurtleBody(demoState.transcript.responseBody)}</pre>
                        {:else}
                            <div class="mt-2 border-t border-gray-700 pt-2 text-gray-400">No response body.</div>
                        {/if}
                    </div>
                </div>
            </div>
        {/if}
    </div>
</div>
