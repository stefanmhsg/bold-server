<script lang="ts">
    import { onMount, onDestroy } from 'svelte';
    import Konva from 'konva';
    import type { MazeLayout, Cell } from '$lib/types';
    import { mazeState } from '$lib/mazeState.svelte';

    let { maze, onCellSelect } = $props<{ 
        maze: MazeLayout,
        onCellSelect?: (cellId: string) => void 
    }>();

    let container: HTMLDivElement;
    let stage: Konva.Stage;
    let layer: Konva.Layer;

    let agentLayer: Konva.Layer;
    let agents: Map<string, Konva.Star> = new Map();
    let locks: Map<string, Konva.Rect> = new Map(); // Store lock shapes
    let agentColors: Map<string, string> = new Map();
    let agentPositions: Map<string, string> = new Map();
    const AGENT_COLORS = [
        '#ef4444', // red
        '#3b82f6', // blue
        '#10b981', // emerald
        '#f59e0b', // amber
        '#8b5cf6', // violet
        '#ec4899', // pink
        '#06b6d4', // cyan
        '#84cc16', // lime
        '#6366f1', // indigo
        '#d946ef', // fuchsia
    ];

    const newestEvent = $derived(mazeState.events[0]);

    const CELL_SIZE = 60;
    const WALL_THICKNESS = 4;
    const PADDING = 20;

    onMount(() => {
        if (!maze || !container) return;

        const width = container.clientWidth;
        const height = container.clientHeight;

        stage = new Konva.Stage({
            container: container,
            width: width,
            height: height,
            draggable: true
        });

        layer = new Konva.Layer();
        stage.add(layer);

        agentLayer = new Konva.Layer();
        stage.add(agentLayer);

        drawMaze();
        fitToView(width, height);

        stage.on('wheel', (e) => {
            e.evt.preventDefault();
            const scaleBy = 1.1;
            const oldScale = stage.scaleX();
            const pointer = stage.getPointerPosition();

            if (!pointer) return;

            const mousePointTo = {
                x: (pointer.x - stage.x()) / oldScale,
                y: (pointer.y - stage.y()) / oldScale,
            };

            let newScale = e.evt.deltaY > 0 ? oldScale / scaleBy : oldScale * scaleBy;

            stage.scale({ x: newScale, y: newScale });

            const newPos = {
                x: pointer.x - mousePointTo.x * newScale,
                y: pointer.y - mousePointTo.y * newScale,
            };
            stage.position(newPos);
        });
    });

    function fitToView(stageWidth: number, stageHeight: number) {
        const mazeWidth = maze.width * CELL_SIZE + PADDING * 2;
        const mazeHeight = maze.height * CELL_SIZE + PADDING * 2;
        
        const scaleX = stageWidth / mazeWidth;
        const scaleY = stageHeight / mazeHeight;
        
        let scale = Math.min(scaleX, scaleY);
        if (scale > 1) scale = 1;
        
        const x = (stageWidth - mazeWidth * scale) / 2;
        const y = (stageHeight - mazeHeight * scale) / 2;
        
        stage.scale({ x: scale, y: scale });
        stage.position({ x: x, y: y });
    }

    onDestroy(() => {
        if (stage) stage.destroy();
    });

    function drawMaze() {
        if (!maze) return;

        maze.cells.forEach(cell => {
            const x = cell.x * CELL_SIZE + PADDING;
            const y = cell.y * CELL_SIZE + PADDING;

            // Draw Cell Background
            const rect = new Konva.Rect({
                x: x,
                y: y,
                width: CELL_SIZE,
                height: CELL_SIZE,
                fill: '#ffffff',
                stroke: '#ddd',
                strokeWidth: 1
            });
            
            if (onCellSelect) {
                rect.on('dblclick', () => {
                    onCellSelect(cell.id);
                });
                // Visual feedback for interactivity
                rect.on('mouseenter', () => {
                    stage.container().style.cursor = 'pointer';
                    rect.fill('#f0f9ff'); // Light blue highlight
                    layer.draw();
                });
                rect.on('mouseleave', () => {
                    stage.container().style.cursor = 'default';
                    rect.fill('#ffffff');
                    layer.draw();
                });
            }

            layer.add(rect);

            // Draw Walls
            drawWalls(cell, x, y);

            // Draw Label (if special)
            if (cell.id === maze.startCell) {
                addLabel(x, y, "START", "green");
            } else if (cell.id === maze.exitCell || cell.connections.exit) {
                addLabel(x, y, "EXIT", "red");
            }

            // Draw Items
            if (cell.items.length > 0) {
                drawItems(cell, x, y);
            }

            // Draw Lock
            if (cell.lock) {
                drawLock(cell, x, y);
            }

            // Draw Green Arrow
            if (cell.connections.green) {
                drawGreenArrow(cell, x, y);
            }
        });

        layer.draw();
    }

    function drawGreenArrow(cell: Cell, x: number, y: number) {
        const targetId = cell.connections.green;
        if (!targetId) return;

        const targetCell = maze.cells.find(c => c.id === targetId);
        if (!targetCell) return;

        const dx = targetCell.x - cell.x;
        const dy = targetCell.y - cell.y;

        // Calculate angle in degrees
        const angle = Math.atan2(dy, dx) * 180 / Math.PI;

        const arrow = new Konva.Arrow({
            x: x + CELL_SIZE / 2,
            y: y + CELL_SIZE / 2,
            points: [-15, 0, 15, 0],
            pointerLength: 8,
            pointerWidth: 8,
            fill: '#22c55e', // Green-500
            stroke: '#22c55e',
            strokeWidth: 4,
            rotation: angle,
            opacity: 0.6
        });
        
        layer.add(arrow);
    }

    function drawLock(cell: Cell, x: number, y: number) {
        const lockSize = 12;
        const padding = 4;
        
        const rect = new Konva.Rect({
            x: x + CELL_SIZE - lockSize - padding,
            y: y + padding,
            width: lockSize,
            height: lockSize,
            fill: cell.lock?.isLocked ? 'red' : 'green',
            stroke: 'black',
            strokeWidth: 1
        });
        layer.add(rect);
        locks.set(cell.id, rect); // Store reference
    }

    function drawItems(cell: Cell, x: number, y: number) {
        const itemRadius = 6;
        const padding = 4;
        const startY = y + CELL_SIZE - itemRadius * 2 - padding;
        let startX = x + padding;

        cell.items.forEach((item, index) => {
             const circle = new Konva.Circle({
                x: startX + (index * (itemRadius * 2 + padding)) + itemRadius,
                y: startY + itemRadius,
                radius: itemRadius,
                fill: 'gold',
                stroke: 'black',
                strokeWidth: 1
             });
             layer.add(circle);
        });
    }

    function drawWalls(cell: Cell, x: number, y: number) {
        const walls = [];

        // North Wall
        if (isWall(cell.connections.north)) {
            walls.push(new Konva.Line({
                points: [x, y, x + CELL_SIZE, y],
                stroke: 'black',
                strokeWidth: WALL_THICKNESS
            }));
        }

        // South Wall
        if (isWall(cell.connections.south)) {
            walls.push(new Konva.Line({
                points: [x, y + CELL_SIZE, x + CELL_SIZE, y + CELL_SIZE],
                stroke: 'black',
                strokeWidth: WALL_THICKNESS
            }));
        }

        // West Wall
        if (isWall(cell.connections.west)) {
            walls.push(new Konva.Line({
                points: [x, y, x, y + CELL_SIZE],
                stroke: 'black',
                strokeWidth: WALL_THICKNESS
            }));
        }

        // East Wall
        if (isWall(cell.connections.east)) {
            walls.push(new Konva.Line({
                points: [x + CELL_SIZE, y, x + CELL_SIZE, y + CELL_SIZE],
                stroke: 'black',
                strokeWidth: WALL_THICKNESS
            }));
        }

        walls.forEach(w => layer.add(w));
    }

    function isWall(connection: string): boolean {
        return connection === 'wall' || connection.endsWith('#Wall');
    }

    function addLabel(x: number, y: number, text: string, color: string) {
        const label = new Konva.Text({
            x: x,
            y: y + 4, // Position at top with small padding
            width: CELL_SIZE,
            text: text,
            fontSize: 10,
            fontFamily: 'Arial',
            fill: color,
            align: 'center',
            fontStyle: 'bold'
        });
        layer.add(label);
    }
    $effect(() => {
        if (!newestEvent) return;

        if (newestEvent.type === "AGENT_MOVED") {
            updateAgentPosition(newestEvent.agent, newestEvent.cell);
        } else if (newestEvent.type === "CELL_LOCKED") {
            updateCellLockState(newestEvent.cell, true);
        } else if (newestEvent.type === "CELL_UNLOCKED") {
            updateCellLockState(newestEvent.cell, false);
        }
    });

    function updateCellLockState(cellId: string, isLocked: boolean) {
        const lockShape = locks.get(cellId);
        if (lockShape) {
            lockShape.fill(isLocked ? 'red' : 'green');
            layer.draw(); // Redraw layer to show changes
        }
    }

    function updateAgentPosition(agentId: string, cellId: string) {
        const oldCellId = agentPositions.get(agentId);

        // Check if reached exit
        if (cellId === maze.exitCell) {
            // Remove agent
            const star = agents.get(agentId);
            if (star) {
                star.destroy();
                agents.delete(agentId);
            }
            agentPositions.delete(agentId);
            
            // Re-layout old cell if it existed
            if (oldCellId) {
                layoutAgentsInCell(oldCellId);
            }
            agentLayer.draw();
            return;
        }

        // Update position
        agentPositions.set(agentId, cellId);

        // Re-layout new cell
        layoutAgentsInCell(cellId);

        // Re-layout old cell if different
        if (oldCellId && oldCellId !== cellId) {
            layoutAgentsInCell(oldCellId);
        }
        
        agentLayer.draw();
    }

    function layoutAgentsInCell(cellId: string) {
        // Find all agents in this cell
        const agentsInCell: string[] = [];
        for (const [aid, cid] of agentPositions.entries()) {
            if (cid === cellId) {
                agentsInCell.push(aid);
            }
        }
        
        // Sort for stability
        agentsInCell.sort();

        const count = agentsInCell.length;
        if (count === 0) return;

        const cell = maze.cells.find(c => c.id === cellId);
        if (!cell) return;

        const cellX = cell.x * CELL_SIZE + PADDING;
        const cellY = cell.y * CELL_SIZE + PADDING;

        const gridSize = Math.ceil(Math.sqrt(count));
        const outerRadius = CELL_SIZE / (gridSize * 2.5);
        const innerRadius = outerRadius / 2;

        agentsInCell.forEach((aid, index) => {
            const row = Math.floor(index / gridSize);
            const col = index % gridSize;

            const markerX = cellX + (col + 0.5) * (CELL_SIZE / gridSize);
            const markerY = cellY + (row + 0.5) * (CELL_SIZE / gridSize);

            let star = agents.get(aid);
            if (!star) {
                // Create new star
                let color = agentColors.get(aid);
                if (!color) {
                    color = AGENT_COLORS[agentColors.size % AGENT_COLORS.length];
                    agentColors.set(aid, color);
                }

                star = new Konva.Star({
                    x: markerX,
                    y: markerY,
                    numPoints: 5,
                    innerRadius: innerRadius,
                    outerRadius: outerRadius,
                    fill: color,
                    stroke: 'black',
                    strokeWidth: 1
                });
                agentLayer.add(star);
                agents.set(aid, star);
            } else {
                // Update existing star
                star.setAttrs({
                    x: markerX,
                    y: markerY,
                    innerRadius: innerRadius,
                    outerRadius: outerRadius
                });
            }
        });
    }

</script>

<div bind:this={container} class="border rounded shadow-lg bg-white w-full h-[80vh] overflow-hidden"></div>
