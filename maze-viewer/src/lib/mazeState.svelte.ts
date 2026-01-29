export interface BaseEvent {
    type: string;
    timestamp: number;
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

export type MazeEvent = AgentMovedEvent | UiUpsertEvent;

export class MazeStore {
    events = $state<MazeEvent[]>([]);
    status = $state<string>("disconnected");
    socket: WebSocket | null = null;

    // Derived views for specific event types
    agentEvents = $derived(this.events.filter(e => e.type === 'AGENT_MOVED') as AgentMovedEvent[]);
    uiEvents = $derived(this.events.filter(e => e.type === 'UI_UPSERT') as UiUpsertEvent[]);

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

export const mazeState = new MazeStore();
