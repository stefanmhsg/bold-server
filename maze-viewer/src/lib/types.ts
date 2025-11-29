export interface MazeLayout {
    width: number;
    height: number;
    startCell: string;
    exitCell: string;
    cells: Cell[];
}

export interface Cell {
    id: string;
    x: number;
    y: number;
    label: string;
    connections: Connections;
    items: Item[];
    lock: Lock | null;
}

export interface Connections {
    north: string;
    south: string;
    east: string;
    west: string;
    exit?: string;
}

export interface Item {
    type: string;
    value: string;
}

export interface Lock {
    isLocked: boolean;
    keyNeeded: string | null;
}
