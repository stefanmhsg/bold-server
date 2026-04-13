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
    startedAt: number;
    finishedAt: number;
    mergeAdded: TransactionTriple[];
    mergeRemoved: TransactionTriple[];
    rules: RuleChange[];
}

export type MazeEvent = AgentMovedEvent | UiUpsertEvent | UiDeleteEvent | TransactionEvent;

export class MazeStore {
    events = $state<MazeEvent[]>([]);
    status = $state<string>("disconnected");
    socket: WebSocket | null = null;

    // Derived views for specific event types
    agentEvents = $derived(this.events.filter(e => e.type === 'AGENT_MOVED') as AgentMovedEvent[]);
    uiEvents = $derived(this.events.filter(e => e.type === 'UI_UPSERT') as UiUpsertEvent[]);
    uiDeleteEvents = $derived(this.events.filter(e => e.type === 'UI_DELETE') as UiDeleteEvent[]);
    transactionEvents = $derived(this.events.filter(e => e.type === 'TRANSACTION') as TransactionEvent[]);

    connect() {
        if (this.socket) return;

        this.status = "connecting";
        this.socket = new WebSocket("ws://localhost:8080/ws");

        this.socket.onopen = () => {
            this.status = "connected";
            console.log("Connected to Maze Server");
        };

        this.socket.onmessage = (event) => {
            try {
                const data = JSON.parse(event.data);
                
                // Ignore heartbeat messages
                if (data.type === "PING") {
                    return;
                }

                if (!isMazeEventPayload(data)) {
                    return;
                }

                // Add timestamp for display
                const mazeEvent = { ...data, timestamp: Date.now() } as MazeEvent;
                
                // Add to beginning of array for newest first
                this.events.unshift(mazeEvent);
                
                // Keep only last 100 events total
                if (this.events.length > 100) {
                    this.events = this.events.slice(0, 100);
                }
            } catch (e) {
                console.error("Failed to parse message", e);
            }
        };

        this.socket.onclose = () => {
            this.status = "disconnected";
            this.socket = null;
        };

        this.socket.onerror = (error) => {
            console.error("WebSocket error", error);
            this.status = "error";
        };
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
