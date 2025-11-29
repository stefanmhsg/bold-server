<script lang="ts">
    import { onMount, onDestroy } from 'svelte';
    import Konva from 'konva';
    import type { MazeLayout, Cell } from '$lib/types';

    let { maze } = $props<{ maze: MazeLayout }>();

    let container: HTMLDivElement;
    let stage: Konva.Stage;
    let layer: Konva.Layer;

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
        });

        layer.draw();
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
            y: y + CELL_SIZE / 2 - 6,
            width: CELL_SIZE,
            text: text,
            fontSize: 12,
            fontFamily: 'Arial',
            fill: color,
            align: 'center',
            fontStyle: 'bold'
        });
        layer.add(label);
    }

</script>

<div bind:this={container} class="border rounded shadow-lg bg-white w-full h-[80vh] overflow-hidden"></div>
