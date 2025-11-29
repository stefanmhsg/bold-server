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

        const width = maze.width * CELL_SIZE + PADDING * 2;
        const height = maze.height * CELL_SIZE + PADDING * 2;

        stage = new Konva.Stage({
            container: container,
            width: width,
            height: height
        });

        layer = new Konva.Layer();
        stage.add(layer);

        drawMaze();
    });

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
                fill: getCellColor(cell),
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
            } else if (cell.items.length > 0) {
                 // Draw item indicator (small circle)
                 const circle = new Konva.Circle({
                    x: x + CELL_SIZE / 2,
                    y: y + CELL_SIZE / 2,
                    radius: 5,
                    fill: 'gold',
                    stroke: 'black',
                    strokeWidth: 1
                 });
                 layer.add(circle);
            }
        });

        layer.draw();
    }

    function getCellColor(cell: Cell): string {
        if (cell.lock && cell.lock.locked) return '#ffebee'; // Light red for locked
        return '#ffffff';
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

<div bind:this={container} class="border rounded shadow-lg bg-white inline-block"></div>
