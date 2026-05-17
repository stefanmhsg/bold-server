import {
    eventArchive,
    emptyArchiveEventTypeCounts,
    type ArchiveEventType,
    type ArchiveEventTypeCounts,
    type ArchiveExportResult
} from './eventArchive';

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
const COLD_EVENT_PAGE_SIZE = 100;
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
    archiveTypeCounts = $state<ArchiveEventTypeCounts>(emptyArchiveEventTypeCounts());
    archiveError = $state<string | null>(null);
    archiveRunId = $state(eventArchive.getCurrentRunId());
    agentEventsHasMore = $state(false);
    transactionEventsHasMore = $state(false);
    isLoadingAgentEvents = $state(false);
    isLoadingTransactionEvents = $state(false);
    agentColdRowsLoaded = $state(0);
    transactionColdRowsLoaded = $state(0);
    socket: WebSocket | null = null;
    private pendingEvents: MazeEvent[] = [];
    private flushHandle: number | null = null;
    private runtimeCanvasListeners = new Set<(event: RuntimeCanvasEvent) => void>();
    private replayDedupeUntil = 0;
    private replaySeenSignatures = new Set<string>();
    private currentRunId = eventArchive.getCurrentRunId();
    private archiveWrite: Promise<void> = Promise.resolve();
    private suppressResetTransactionsUntil = 0;
    private agentArchiveCursor: number | null = null;
    private transactionArchiveCursor: number | null = null;
    private agentColdBrowsingActive = false;
    private transactionColdBrowsingActive = false;

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
            const [agentPage, transactionPage, count, countsByType] = await Promise.all([
                eventArchive.getEventsByType<AgentMovedEvent>('AGENT_MOVED', HOT_AGENT_EVENT_LIMIT),
                eventArchive.getEventsByType<TransactionEvent>('TRANSACTION', HOT_TRANSACTION_EVENT_LIMIT),
                eventArchive.count(),
                eventArchive.countByType()
            ]);

            this.agentEvents = agentPage.events;
            this.transactionEvents = transactionPage.events;
            this.agentArchiveCursor = agentPage.nextCursor;
            this.transactionArchiveCursor = transactionPage.nextCursor;
            this.agentEventsHasMore = agentPage.hasMore;
            this.transactionEventsHasMore = transactionPage.hasMore;
            this.archiveCount = count;
            this.archiveTypeCounts = countsByType;
            this.updateHasMoreFromCounts();
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

    clearTables(): void {
        this.agentEvents = [];
        this.transactionEvents = [];
        this.agentArchiveCursor = null;
        this.transactionArchiveCursor = null;
        this.agentColdBrowsingActive = false;
        this.transactionColdBrowsingActive = false;
        this.agentColdRowsLoaded = 0;
        this.transactionColdRowsLoaded = 0;
        this.updateHasMoreFromCounts();
    }

    private async clearArchiveAndTables(): Promise<void> {
        this.clearTables();
        this.uiEvents = [];
        this.uiDeleteEvents = [];
        this.replaySeenSignatures.clear();

        try {
            await this.waitForArchiveWrites();
            await eventArchive.clear();
            this.archiveCount = 0;
            this.archiveTypeCounts = emptyArchiveEventTypeCounts();
            this.updateHasMoreFromCounts();
            this.archiveError = null;
        } catch (error) {
            this.archiveError = error instanceof Error ? error.message : String(error);
            console.warn('Failed to clear archived logs', error);
        }
    }

    async exportLogsNdjson(eventTypes?: readonly ArchiveEventType[]): Promise<ArchiveExportResult> {
        this.flushQueuedEventsNow();
        await this.waitForArchiveWrites();
        return eventArchive.exportNdjson({ eventTypes });
    }

    async resetForNewRun(): Promise<void> {
        this.flushQueuedEventsNow();
        this.pendingEvents = [];
        await this.clearArchiveAndTables();
        this.currentRunId = eventArchive.startNewRun();
        this.archiveRunId = this.currentRunId;
        this.suppressResetTransactionsUntil = Date.now() + REPLAY_DEDUPE_WINDOW_MS;
        this.seedReplayDedupeSignatures();
    }

    async loadMoreAgentEvents(searchText = ''): Promise<void> {
        if (this.isLoadingAgentEvents || !this.agentEventsHasMore) {
            return;
        }

        this.isLoadingAgentEvents = true;
        try {
            await this.waitForArchiveWrites();
            const appended = await this.loadMoreEvents<AgentMovedEvent>(
                'AGENT_MOVED',
                () => this.agentArchiveCursor,
                (cursor) => this.agentArchiveCursor = cursor,
                () => this.agentEventsHasMore,
                (hasMore) => this.agentEventsHasMore = hasMore,
                () => this.agentEvents,
                (events) => this.agentEvents = events,
                searchText
            );
            this.agentColdRowsLoaded += appended;
            this.agentColdBrowsingActive = true;
        } catch (error) {
            this.archiveError = error instanceof Error ? error.message : String(error);
            console.warn('Failed to load archived agent events', error);
        } finally {
            this.isLoadingAgentEvents = false;
        }
    }

    async loadMoreTransactionEvents(searchText = ''): Promise<void> {
        if (this.isLoadingTransactionEvents || !this.transactionEventsHasMore) {
            return;
        }

        this.isLoadingTransactionEvents = true;
        try {
            await this.waitForArchiveWrites();
            const appended = await this.loadMoreEvents<TransactionEvent>(
                'TRANSACTION',
                () => this.transactionArchiveCursor,
                (cursor) => this.transactionArchiveCursor = cursor,
                () => this.transactionEventsHasMore,
                (hasMore) => this.transactionEventsHasMore = hasMore,
                () => this.transactionEvents,
                (events) => this.transactionEvents = events,
                searchText
            );
            this.transactionColdRowsLoaded += appended;
            this.transactionColdBrowsingActive = true;
        } catch (error) {
            this.archiveError = error instanceof Error ? error.message : String(error);
            console.warn('Failed to load archived transaction events', error);
        } finally {
            this.isLoadingTransactionEvents = false;
        }
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

    private async loadMoreEvents<T extends MazeEvent>(
        type: T['type'],
        getCursor: () => number | null,
        setCursor: (cursor: number | null) => void,
        getHasMore: () => boolean,
        setHasMore: (hasMore: boolean) => void,
        getEvents: () => T[],
        setEvents: (events: T[]) => void,
        searchText: string
    ): Promise<number> {
        let appended = 0;

        while (getHasMore() && appended === 0) {
            const page = await eventArchive.getEventsByType<T>(
                type,
                COLD_EVENT_PAGE_SIZE,
                getCursor(),
                { searchText }
            );
            setCursor(page.nextCursor);
            setHasMore(page.hasMore);

            if (page.events.length === 0) {
                break;
            }

            const current = getEvents();
            const merged = this.appendUniqueEvents(current, page.events);
            appended = merged.length - current.length;
            setEvents(merged);
        }

        return appended;
    }

    private appendUniqueEvents<T extends MazeEvent>(current: T[], incoming: T[]): T[] {
        const seen = new Set(current.map((event) => this.visibleEventKey(event)));
        const uniqueIncoming = incoming.filter((event) => {
            const key = this.visibleEventKey(event);
            if (seen.has(key)) {
                return false;
            }

            seen.add(key);
            return true;
        });

        return [...current, ...uniqueIncoming];
    }

    private prependVisibleEvent<T extends MazeEvent>(
        events: T[],
        event: T,
        hotLimit: number,
        coldBrowsingActive: boolean
    ): T[] {
        const next = [event, ...events];
        return coldBrowsingActive ? next : next.slice(0, hotLimit);
    }

    private visibleEventKey(event: MazeEvent): string {
        return JSON.stringify(event);
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
                    this.agentEvents = this.prependVisibleEvent(
                        this.agentEvents,
                        mazeEvent,
                        HOT_AGENT_EVENT_LIMIT,
                        this.agentColdBrowsingActive
                    );
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
                    this.transactionEvents = this.prependVisibleEvent(
                        this.transactionEvents,
                        mazeEvent,
                        HOT_TRANSACTION_EVENT_LIMIT,
                        this.transactionColdBrowsingActive
                    );
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
                this.archiveTypeCounts = this.addEventTypeCounts(this.archiveTypeCounts, events);
                this.updateHasMoreFromCounts();
                this.archiveError = null;
            })
            .catch((error) => {
                this.archiveError = error instanceof Error ? error.message : String(error);
                console.warn('Failed to archive events', error);
            });
    }

    connect(webSocketUrl = "ws://localhost:8080/ws") {
        if (this.socket) return;

        this.status = "connecting";
        this.socket = new WebSocket(webSocketUrl);

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

    private addEventTypeCounts(current: ArchiveEventTypeCounts, events: MazeEvent[]): ArchiveEventTypeCounts {
        const next = { ...current };
        for (const event of events) {
            next[event.type as ArchiveEventType] += 1;
        }
        return next;
    }

    private updateHasMoreFromCounts(): void {
        this.agentEventsHasMore = this.archiveTypeCounts.AGENT_MOVED > this.agentEvents.length;
        this.transactionEventsHasMore = this.archiveTypeCounts.TRANSACTION > this.transactionEvents.length;
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
