import { eventArchive, type ArchiveExportResult } from './eventArchive';

export interface BaseEvent {
    type: string;
    timestamp: number;
}

export interface TransactionTriple {
    subject: string;
    predicate: string;
    object: string;
    context: string | null;
}

export interface RuleChange {
    ruleName: string;
    error?: string | null;
    added: TransactionTriple[];
    removed: TransactionTriple[];
}

export interface AgentMovedEvent extends BaseEvent {
    type: 'AGENT_MOVED';
    agent: string;
    cell: string;
}

export interface UiCommand {
    id: string;
    konvaType: string;
    layer?: string;
    attrs: Record<string, any>;
}

export interface UiUpsertEvent extends BaseEvent, UiCommand {
    type: 'UI_UPSERT';
}

export interface UiDeleteEvent extends BaseEvent {
    type: 'UI_DELETE';
    id: string;
}

export interface TransactionEvent extends BaseEvent {
    type: 'TRANSACTION';
    trigger: 'POST' | 'STARTUP' | string;
    status: 'COMMITTED' | 'ROLLED_BACK' | 'FAILED' | string;
    agent?: string | null;
    graph?: string | null;
    requestBody?: string | null;
    error?: string | null;
    traceMode?: 'off' | 'summary' | 'full' | string | null;
    transactionId?: number;
    ruleCount?: number;
    startedAt: number;
    finishedAt: number;
    mergeAdded: TransactionTriple[];
    mergeRemoved: TransactionTriple[];
    rules: RuleChange[];
}

export type MazeEvent = AgentMovedEvent | UiUpsertEvent | UiDeleteEvent | TransactionEvent;
export type RuntimeCanvasEvent = AgentMovedEvent | UiUpsertEvent | UiDeleteEvent;

const HOT_AGENT_EVENT_LIMIT = 50;
const HOT_TRANSACTION_EVENT_LIMIT = 50;
const HOT_UI_EVENT_LIMIT = 50;
const REPLAY_DEDUPE_WINDOW_MS = 3000;
const REPLAY_DEDUPE_MAX_SIGNATURES = 2000;

export class MazeStore {
    agentEvents = $state<AgentMovedEvent[]>([]);
    uiEvents = $state<UiUpsertEvent[]>([]);
    uiDeleteEvents = $state<UiDeleteEvent[]>([]);
    transactionEvents = $state<TransactionEvent[]>([]);
    status = $state<string>("disconnected");
    archiveCount = $state(0);
    archiveError = $state<string | null>(null);
    socket: WebSocket | null = null;
    private pendingEvents: MazeEvent[] = [];
    private flushHandle: number | null = null;
    private runtimeCanvasListeners = new Set<(event: RuntimeCanvasEvent) => void>();
    private replayDedupeUntil = 0;
    private replaySeenSignatures = new Set<string>();
    private currentRunId = eventArchive.getCurrentRunId();
    private archiveWrite: Promise<void> = Promise.resolve();
    private suppressResetTransactionsUntil = 0;

    constructor() {
        void this.hydrateArchivedLogs();
    }

    subscribeRuntimeCanvasEvents(listener: (event: RuntimeCanvasEvent) => void): () => void {
        this.runtimeCanvasListeners.add(listener);

        return () => {
            this.runtimeCanvasListeners.delete(listener);
        };
    }

    private emitRuntimeCanvasEvent(event: RuntimeCanvasEvent): void {
        for (const listener of this.runtimeCanvasListeners) {
            try {
                listener(event);
            } catch (error) {
                console.error('Runtime canvas listener failed', error);
            }
        }
    }

    private async hydrateArchivedLogs(): Promise<void> {
        if (typeof window === 'undefined') {
            return;
        }

        try {
            const [agentEvents, transactionEvents, count] = await Promise.all([
                eventArchive.getRecentEventsByType<AgentMovedEvent>('AGENT_MOVED', HOT_AGENT_EVENT_LIMIT),
                eventArchive.getRecentEventsByType<TransactionEvent>('TRANSACTION', HOT_TRANSACTION_EVENT_LIMIT),
                eventArchive.count()
            ]);

            this.agentEvents = agentEvents;
            this.transactionEvents = transactionEvents;
            this.archiveCount = count;
            this.archiveError = null;
        } catch (error) {
            this.archiveError = error instanceof Error ? error.message : String(error);
            console.warn('Failed to restore archived logs', error);
        }

        this.seedReplayDedupeSignatures();
    }

    private seedReplayDedupeSignatures(): void {
        this.replaySeenSignatures.clear();

        const recentAgentEvents = this.agentEvents.slice(-REPLAY_DEDUPE_MAX_SIGNATURES);
        for (const event of recentAgentEvents) {
            this.replaySeenSignatures.add(this.signatureForEvent(event));
        }

        const remaining = Math.max(0, REPLAY_DEDUPE_MAX_SIGNATURES - this.replaySeenSignatures.size);
        if (remaining > 0) {
            const recentTransactionEvents = this.transactionEvents.slice(-remaining);
            for (const event of recentTransactionEvents) {
                this.replaySeenSignatures.add(this.signatureForEvent(event));
            }
        }
    }

    private signatureForPayload(payload: Omit<MazeEvent, 'timestamp'>): string {
        return JSON.stringify(payload);
    }

    private signatureForEvent(event: MazeEvent): string {
        const { timestamp: _timestamp, ...payload } = event;
        return this.signatureForPayload(payload);
    }

    async clearLogs(): Promise<void> {
        this.agentEvents = [];
        this.uiEvents = [];
        this.uiDeleteEvents = [];
        this.transactionEvents = [];
        this.replaySeenSignatures.clear();

        try {
            await this.waitForArchiveWrites();
            await eventArchive.clear();
            this.archiveCount = 0;
            this.archiveError = null;
        } catch (error) {
            this.archiveError = error instanceof Error ? error.message : String(error);
            console.warn('Failed to clear archived logs', error);
        }
    }

    async exportLogsNdjson(): Promise<ArchiveExportResult> {
        this.flushQueuedEventsNow();
        await this.waitForArchiveWrites();
        return eventArchive.exportNdjson();
    }

    async resetForNewRun(): Promise<void> {
        this.flushQueuedEventsNow();
        this.pendingEvents = [];
        await this.clearLogs();
        this.currentRunId = eventArchive.startNewRun();
        this.suppressResetTransactionsUntil = Date.now() + REPLAY_DEDUPE_WINDOW_MS;
        this.seedReplayDedupeSignatures();
    }

    private waitForArchiveWrites(): Promise<void> {
        return this.archiveWrite;
    }

    private flushQueuedEventsNow(): void {
        if (this.flushHandle !== null) {
            cancelAnimationFrame(this.flushHandle);
            this.flushHandle = null;
        }

        this.flushPendingEvents();
    }

    private queueEvent(event: MazeEvent): void {
        this.pendingEvents.push(event);
        if (this.flushHandle !== null) {
            return;
        }

        this.flushHandle = requestAnimationFrame(() => {
            this.flushHandle = null;
            this.flushPendingEvents();
        });
    }

    private flushPendingEvents(): void {
        if (this.pendingEvents.length === 0) {
            return;
        }

        const batch = this.pendingEvents;
        this.pendingEvents = [];

        for (const mazeEvent of batch) {
            switch (mazeEvent.type) {
                case 'AGENT_MOVED':
                    this.agentEvents = [...this.agentEvents, mazeEvent].slice(-HOT_AGENT_EVENT_LIMIT);
                    this.emitRuntimeCanvasEvent(mazeEvent);
                    break;
                case 'UI_UPSERT':
                    this.uiEvents = [...this.uiEvents, mazeEvent].slice(-HOT_UI_EVENT_LIMIT);
                    this.emitRuntimeCanvasEvent(mazeEvent);
                    break;
                case 'UI_DELETE':
                    this.uiDeleteEvents = [...this.uiDeleteEvents, mazeEvent].slice(-HOT_UI_EVENT_LIMIT);
                    this.emitRuntimeCanvasEvent(mazeEvent);
                    break;
                case 'TRANSACTION':
                    this.transactionEvents = [...this.transactionEvents, mazeEvent].slice(-HOT_TRANSACTION_EVENT_LIMIT);
                    break;
            }
        }

        this.archiveEvents(batch);
    }

    private archiveEvents(events: MazeEvent[]): void {
        this.archiveWrite = this.archiveWrite
            .then(() => eventArchive.appendEvents(events, this.currentRunId))
            .then((archivedCount) => {
                this.archiveCount += archivedCount;
                this.archiveError = null;
            })
            .catch((error) => {
                this.archiveError = error instanceof Error ? error.message : String(error);
                console.warn('Failed to archive events', error);
            });
    }

    connect() {
        if (this.socket) return;

        this.status = "connecting";
        this.socket = new WebSocket("ws://localhost:8080/ws");

        this.socket.onopen = () => {
            this.status = "connected";
            this.replayDedupeUntil = Date.now() + REPLAY_DEDUPE_WINDOW_MS;
            console.log("Connected to Maze Server");
        };

        this.socket.onmessage = (event) => {
            try {
                const data = JSON.parse(event.data);
                
                // Ignore heartbeat messages
                if (data.type === "PING") {
                    return;
                }

                if (data.type === "ADMIN_RESET") {
                    return;
                }

                if (!isMazeEventPayload(data)) {
                    return;
                }

                if (this.shouldSuppressResetTransaction(data)) {
                    return;
                }

                const payloadSignature = this.signatureForPayload(data);
                if (Date.now() <= this.replayDedupeUntil && this.replaySeenSignatures.has(payloadSignature)) {
                    return;
                }

                // Add timestamp for display
                const mazeEvent = { ...data, timestamp: Date.now() } as MazeEvent;

                if (this.replaySeenSignatures.size >= REPLAY_DEDUPE_MAX_SIGNATURES) {
                    this.replaySeenSignatures.clear();
                }
                this.replaySeenSignatures.add(payloadSignature);

                this.queueEvent(mazeEvent);
            } catch (e) {
                console.error("Failed to parse message", e);
            }
        };

        this.socket.onclose = () => {
            if (this.flushHandle !== null) {
                cancelAnimationFrame(this.flushHandle);
                this.flushHandle = null;
            }
            this.flushPendingEvents();
            this.status = "disconnected";
            this.socket = null;
        };

        this.socket.onerror = (error) => {
            console.error("WebSocket error", error);
            this.status = "error";
        };
    }

    private shouldSuppressResetTransaction(payload: Omit<MazeEvent, 'timestamp'>): boolean {
        return Date.now() <= this.suppressResetTransactionsUntil
            && payload.type === 'TRANSACTION'
            && payload.trigger === 'RESET';
    }
}

function isMazeEventPayload(payload: any): payload is Omit<MazeEvent, 'timestamp'> {
    if (!payload || typeof payload !== 'object' || typeof payload.type !== 'string') {
        return false;
    }

    if (payload.type === 'AGENT_MOVED') {
        return typeof payload.agent === 'string' && typeof payload.cell === 'string';
    }

    if (payload.type === 'UI_UPSERT') {
        return typeof payload.id === 'string' && typeof payload.attrs === 'object';
    }

    if (payload.type === 'UI_DELETE') {
        return typeof payload.id === 'string';
    }

    if (payload.type === 'TRANSACTION') {
        return Array.isArray(payload.rules)
            && Array.isArray(payload.mergeAdded)
            && Array.isArray(payload.mergeRemoved)
            && typeof payload.trigger === 'string'
            && typeof payload.status === 'string';
    }

    return false;
}

export const mazeState = new MazeStore();
