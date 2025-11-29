export interface MazeEvent {
    type: string;
    agent: string;
    cell: string;
    timestamp: number;
}

export class MazeStore {
    events = $state<MazeEvent[]>([]);
    status = $state<string>("disconnected");
    socket: WebSocket | null = null;

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
                const mazeEvent: MazeEvent = { ...data, timestamp: Date.now() };
                // Add to beginning of array for newest first
                this.events.unshift(mazeEvent);
                
                // Keep only last 50 events
                if (this.events.length > 50) {
                    this.events = this.events.slice(0, 50);
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
