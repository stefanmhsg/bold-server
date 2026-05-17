<script lang="ts">
    import { onMount, onDestroy } from 'svelte';
    import Konva from 'konva';
    import type { MazeLayout, Cell } from '$lib/types';
    import { mazeState, type RuntimeCanvasEvent, type UiCommand } from '$lib/mazeState.svelte';
    import { getOptimalRouteOverlay } from '$lib/optimalRoutes';
    import { showOptimalRoute } from '$lib/routeOverlayStore';

    let { maze, uiSnapshot, scenarioName, onCellSelect } = $props<{ 
        maze: MazeLayout,
        uiSnapshot: UiCommand[],
        scenarioName?: string | null,
        onCellSelect?: (cellId: string) => void 
    }>();

    let container: HTMLDivElement;
    let stage: Konva.Stage;
    let mazeLayer: Konva.Layer;
    let uiLayer: Konva.Layer;

    let agentLayer: Konva.Layer;
    let tooltipLayer: Konva.Layer;
    let cellTooltip: Konva.Label;
    let cellTooltipText: Konva.Text;
    let unsubscribeRuntimeCanvasEvents: (() => void) | null = null;
    let cellRects: Map<string, Konva.Rect> = new Map();
    let cellBaseFillById: Map<string, string> = new Map();
    let cellUiFillById: Map<string, string> = new Map();
    let agents: Map<string, Konva.Star> = new Map();
    let agentColors: Map<string, string> = new Map();
    let agentPositions: Map<string, string> = new Map();
    let uiNodes: Map<string, Konva.Shape | Konva.Group> = new Map();
    let uiPathImageCache: Map<string, CachedPathRender> = new Map();
    let uiPathImagePromises: Map<string, Promise<CachedPathRender>> = new Map();
    let uiPathRenderKeysById: Map<string, string> = new Map();
    let pendingLayerDraws: Set<Konva.Layer> = new Set();
    let drawFrameHandle: number | null = null;

    type CachedPathRender = {
        image: HTMLImageElement;
        bounds: { x: number; y: number; width: number; height: number };
        pad: number;
    };


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

    function handleRuntimeCanvasEvent(event: RuntimeCanvasEvent) {
        if (event.type === "AGENT_MOVED") {
            updateAgentPosition(event.agent, event.cell);
            return;
        }

        if (event.type === "UI_UPSERT") {
            applyUiUpsert(event);
            return;
        }

        if (event.type === "UI_DELETE") {
            removeUiNode(event.id);
        }
    }

    const CELL_SIZE = 60;
    const WALL_THICKNESS = 4;
    const PADDING = 20;

    const optimalRouteOverlay = $derived(getOptimalRouteOverlay(scenarioName));
    const optimalRouteColor = $derived(optimalRouteOverlay?.color ?? '#ffc3ff');

    const KONVA_REGISTRY: Record<
        string,
        new (config: any) => Konva.Shape | Konva.Group
    > = {
        Rect: Konva.Rect,
        Circle: Konva.Circle,
        Line: Konva.Line,
        Arrow: Konva.Arrow,
        Text: Konva.Text,
        Star: Konva.Star,
        Path: Konva.Path
    };

    function isPathCommand(cmd: UiCommand): boolean {
        return cmd.konvaType === "Path" && typeof cmd.attrs?.data === "string";
    }

    function scheduleLayerDraw(targetLayer: Konva.Layer) {
        pendingLayerDraws.add(targetLayer);
        if (drawFrameHandle !== null) {
            return;
        }

        drawFrameHandle = requestAnimationFrame(() => {
            drawFrameHandle = null;

            for (const layerToDraw of pendingLayerDraws) {
                layerToDraw.batchDraw();
            }

            pendingLayerDraws.clear();
        });
    }

    function pathRenderCacheKey(attrs: Record<string, any>): string {
        // Cache by geometry and paint attributes that affect raster output.
        return JSON.stringify({
            data: attrs.data,
            fill: attrs.fill ?? null,
            stroke: attrs.stroke ?? null,
            strokeWidth: attrs.strokeWidth ?? null,
            lineJoin: attrs.lineJoin ?? null,
            lineCap: attrs.lineCap ?? null
        });
    }

    async function getCachedPathImage(attrs: Record<string, any>): Promise<CachedPathRender> {
        const key = pathRenderCacheKey(attrs);
        const existingImage = uiPathImageCache.get(key);
        if (existingImage) return existingImage;

        const existingPromise = uiPathImagePromises.get(key);
        if (existingPromise) return existingPromise;

        const promise = rasterizePathToImage(attrs).then((render) => {
            uiPathImageCache.set(key, render);
            uiPathImagePromises.delete(key);
            return render;
        }).catch((error) => {
            uiPathImagePromises.delete(key);
            throw error;
        });

        uiPathImagePromises.set(key, promise);
        return promise;
    }

    function rasterizePathToImage(attrs: Record<string, any>): Promise<CachedPathRender> {
        return new Promise((resolve, reject) => {
            try {
                const path = new Konva.Path({
                    data: attrs.data,
                    fill: attrs.fill,
                    stroke: attrs.stroke,
                    strokeWidth: attrs.strokeWidth ?? 0,
                    lineJoin: attrs.lineJoin,
                    lineCap: attrs.lineCap
                });

                const bounds = path.getClientRect({ skipTransform: true, skipShadow: true });
                const pad = 2;
                const width = Math.max(1, Math.ceil(bounds.width + pad * 2));
                const height = Math.max(1, Math.ceil(bounds.height + pad * 2));

                const canvas = document.createElement("canvas");
                canvas.width = width;
                canvas.height = height;

                const ctx = canvas.getContext("2d");
                if (!ctx) {
                    reject(new Error("Could not create canvas context for path rasterization"));
                    return;
                }

                ctx.translate(-bounds.x + pad, -bounds.y + pad);

                const p2d = new Path2D(String(attrs.data));
                if (attrs.fill) {
                    ctx.fillStyle = String(attrs.fill);
                    ctx.fill(p2d);
                }
                if (attrs.stroke && (attrs.strokeWidth ?? 0) > 0) {
                    ctx.strokeStyle = String(attrs.stroke);
                    ctx.lineWidth = Number(attrs.strokeWidth ?? 0);
                    if (attrs.lineJoin) ctx.lineJoin = String(attrs.lineJoin) as CanvasLineJoin;
                    if (attrs.lineCap) ctx.lineCap = String(attrs.lineCap) as CanvasLineCap;
                    ctx.stroke(p2d);
                }

                const img = new Image();
                img.onload = () => resolve({ image: img, bounds, pad });
                img.onerror = () => reject(new Error("Failed to load rasterized path image"));
                img.src = canvas.toDataURL("image/png");
            } catch (error) {
                reject(error instanceof Error ? error : new Error("Unknown path rasterization error"));
            }
        });
    }

    async function upsertPathAsImage(cmd: UiCommand, targetLayer: Konva.Layer) {
        const resolvedAttrs = resolveUiAttrs(cmd);
        const cacheKey = pathRenderCacheKey(resolvedAttrs);
        uiPathRenderKeysById.set(cmd.id, cacheKey);

        try {
            const cached = await getCachedPathImage(resolvedAttrs);

            // Ignore stale async completions for nodes that were updated again.
            if (uiPathRenderKeysById.get(cmd.id) !== cacheKey) return;

            let node = uiNodes.get(cmd.id);
            if (!(node instanceof Konva.Image)) {
                node?.destroy();
                node = new Konva.Image({ id: cmd.id, image: new Image() });
                targetLayer.add(node);
                uiNodes.set(cmd.id, node);
            }

            const imageAttrs: Record<string, any> = { ...resolvedAttrs };
            delete imageAttrs.data;
            delete imageAttrs.fill;
            delete imageAttrs.stroke;
            delete imageAttrs.strokeWidth;
            delete imageAttrs.lineJoin;
            delete imageAttrs.lineCap;

            imageAttrs.image = cached.image;
            imageAttrs.width = cached.image.width;
            imageAttrs.height = cached.image.height;
            // Place and rotate around the rendered path center, so anchor positions
            // remain intuitive even when SVG path coordinates are absolute.
            imageAttrs.x = Number(resolvedAttrs.x ?? 0);
            imageAttrs.y = Number(resolvedAttrs.y ?? 0);
            imageAttrs.offsetX = cached.pad + cached.bounds.width / 2;
            imageAttrs.offsetY = cached.pad + cached.bounds.height / 2;

            node.setAttrs(imageAttrs);
            scheduleLayerDraw(targetLayer);
        } catch (error) {
            console.warn("Path rasterization failed, falling back to Konva.Path", error);

            const Ctor = KONVA_REGISTRY[cmd.konvaType];
            if (!Ctor) return;

            let node = uiNodes.get(cmd.id);
            if (!node) {
                node = new Ctor({ id: cmd.id, ...resolvedAttrs });
                targetLayer.add(node);
                uiNodes.set(cmd.id, node);
            } else {
                node.setAttrs(resolvedAttrs);
            }
            scheduleLayerDraw(targetLayer);
        }
    }

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

        mazeLayer = new Konva.Layer();
        stage.add(mazeLayer);

        uiLayer = new Konva.Layer();
        stage.add(uiLayer);

        agentLayer = new Konva.Layer();
        stage.add(agentLayer);

        tooltipLayer = new Konva.Layer({ listening: false });
        stage.add(tooltipLayer);
        createCellTooltip();

        drawMaze();

        // Apply initial UI snapshot
        uiSnapshot.forEach((cmd: UiCommand) => {
            applyUiUpsert(cmd);
        });

        unsubscribeRuntimeCanvasEvents = mazeState.subscribeRuntimeCanvasEvents(handleRuntimeCanvasEvent);

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

        // Handle window/container resize
        const resizeObserver = new ResizeObserver(() => {
            if (!container || !stage) return;
            
            const newWidth = container.clientWidth;
            const newHeight = container.clientHeight;
            
            if (newWidth > 0 && newHeight > 0) {
                stage.width(newWidth);
                stage.height(newHeight);
            }
        });

        resizeObserver.observe(container);

        return () => {
            resizeObserver.disconnect();
        };
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
        if (unsubscribeRuntimeCanvasEvents) {
            unsubscribeRuntimeCanvasEvents();
            unsubscribeRuntimeCanvasEvents = null;
        }
        if (drawFrameHandle !== null) {
            cancelAnimationFrame(drawFrameHandle);
            drawFrameHandle = null;
        }
        pendingLayerDraws.clear();
        if (stage) stage.destroy();
    });

    function drawMaze() {
        if (!maze) return;

        maze.cells.forEach((cell: Cell) => {
            const x = cell.x * CELL_SIZE + PADDING;
            const y = cell.y * CELL_SIZE + PADDING;

            cellUiFillById.set(cell.id, '#ffffff');
            const baseFill = getCellBaseFill(cell);

            // Draw Cell Background
            const rect = new Konva.Rect({
                x: x,
                y: y,
                width: CELL_SIZE,
                height: CELL_SIZE,
                fill: baseFill,
                stroke: '#ddd',
                strokeWidth: 1
            });

            cellRects.set(cell.id, rect);
            cellBaseFillById.set(cell.id, baseFill);
            
            if (onCellSelect) {
                rect.on('dblclick', () => {
                    onCellSelect(cell.id);
                });
            }

            // Visual feedback and coordinate tooltip for interactivity.
            rect.on('mouseenter', () => {
                stage.container().style.cursor = onCellSelect ? 'pointer' : 'default';
                rect.fill('#f0f9ff');
                showCellTooltip(cell);
                mazeLayer.draw();
            });
            rect.on('mousemove', () => {
                positionCellTooltip();
            });
            rect.on('mouseleave', () => {
                stage.container().style.cursor = 'default';
                rect.fill(cellBaseFillById.get(cell.id) ?? '#ffffff');
                hideCellTooltip();
                mazeLayer.draw();
            });

            mazeLayer.add(rect);

            // Draw Walls
            drawWalls(cell, x, y);

            // Draw Label (if special)
            if (cell.id === maze.startCell) {
                addLabel(x, y, "START", "green");
            } else if (cell.id === maze.exitCell || cell.connections.exit) {
                addLabel(x, y, "EXIT", "red");
            }

        });

        mazeLayer.draw();
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

        walls.forEach(w => mazeLayer.add(w));
    }

    function isOptimalRouteCell(cell: Cell): boolean {
        if (!$showOptimalRoute) {
            return false;
        }

        if (!optimalRouteOverlay) {
            return false;
        }

        const marker = '/cells/';
        const idx = cell.id.indexOf(marker);
        if (idx === -1) return false;

        const suffix = cell.id.substring(idx + marker.length);
        return optimalRouteOverlay.cellIdSuffixes.has(suffix);
    }

    function getCellBaseFill(cell: Cell): string {
        const uiFill = cellUiFillById.get(cell.id) ?? '#ffffff';
        return isOptimalRouteCell(cell) ? optimalRouteColor : uiFill;
    }

    function refreshAllCellFills() {
        maze.cells.forEach((cell: Cell) => {
            const rect = cellRects.get(cell.id);
            if (!rect) return;

            const fill = getCellBaseFill(cell);
            cellBaseFillById.set(cell.id, fill);
            rect.fill(fill);
        });

        mazeLayer.batchDraw();
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
        mazeLayer.add(label);
    }

    function createCellTooltip() {
        cellTooltip = new Konva.Label({
            opacity: 0.92,
            visible: false,
            listening: false
        });

        cellTooltip.add(new Konva.Tag({
            fill: '#111827',
            pointerDirection: 'down',
            pointerWidth: 8,
            pointerHeight: 6,
            cornerRadius: 4,
            shadowColor: 'black',
            shadowBlur: 8,
            shadowOpacity: 0.18,
            shadowOffset: { x: 0, y: 2 }
        }));

        cellTooltipText = new Konva.Text({
            text: '',
            fontFamily: 'Arial',
            fontSize: 13,
            fill: '#ffffff',
            padding: 6
        });
        cellTooltip.add(cellTooltipText);
        tooltipLayer.add(cellTooltip);
    }

    function showCellTooltip(cell: Cell) {
        if (!cellTooltip || !cellTooltipText) return;

        cellTooltipText.text(shortCellCoordinate(cell));
        cellTooltip.visible(true);
        positionCellTooltip();
    }

    function positionCellTooltip() {
        if (!cellTooltip || !tooltipLayer || !cellTooltip.visible()) return;

        const pointer = stage.getPointerPosition();
        if (!pointer) return;

        cellTooltip.absolutePosition({
            x: pointer.x + 12,
            y: pointer.y - 8
        });
        tooltipLayer.batchDraw();
    }

    function hideCellTooltip() {
        if (!cellTooltip || !tooltipLayer) return;

        cellTooltip.visible(false);
        tooltipLayer.batchDraw();
    }

    function shortCellCoordinate(cell: Cell): string {
        const marker = '/cells/';
        const idx = cell.id.indexOf(marker);
        if (idx !== -1) {
            return cell.id.substring(idx + marker.length);
        }

        return cell.label || `${cell.x}/${cell.y}`;
    }

    $effect(() => {
        $showOptimalRoute;
        optimalRouteOverlay;

        if (!mazeLayer) return;
        refreshAllCellFills();
    });
    
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
            agentColors.delete(agentId);
            
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

        const cell = maze.cells.find((c: { id: string; }) => c.id === cellId);
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
					outerRadius: outerRadius,
					numPoints: 5
				});
            }
        });
    }

    function applyUiUpsert(cmd: UiCommand) {
        const { id, konvaType, layer: layerName, attrs } = cmd;
        const effectiveLayer = normalizeLayerHint(typeof attrs?.layer === "string" ? attrs.layer : layerName);

        // Semantic hook: ui:layer "cellBackground" + ui:fill "..." updates the base cell fill.
        if (effectiveLayer.toLowerCase() === "cellbackground") {
            const cellId = getCellIdFromUiId(id);
            const fill = attrs?.fill;

            if (cellId && typeof fill === "string") {
                setCellBackground(cellId, fill);
            }
            return;
        }

        const targetLayer = resolveTargetLayer(effectiveLayer);

        if (isPathCommand(cmd)) {
            void upsertPathAsImage(cmd, targetLayer);
            return;
        }

        let node = uiNodes.get(id);

        if (!node) {
            const Ctor = KONVA_REGISTRY[konvaType];
            if (!Ctor) {
                console.warn("Unknown Konva node type", konvaType);
                return;
            }

            const resolvedAttrs = resolveUiAttrs(cmd);

            const createdNode = new Ctor({
                id,
                ...resolvedAttrs
            });

            targetLayer.add(createdNode);
            uiNodes.set(id, createdNode);
        } else {
            const resolvedAttrs = resolveUiAttrs(cmd);
            if (node.getLayer() !== targetLayer) {
                node.moveTo(targetLayer);
            }
            node.setAttrs(resolvedAttrs);
        }

        scheduleLayerDraw(targetLayer);
    }

    function setCellBackground(cellId: string, fill: string) {
        const rect = cellRects.get(cellId);
        if (!rect) return;

        cellUiFillById.set(cellId, fill);

        const cell = maze.cells.find((c: Cell) => c.id === cellId);
        const effectiveFill = cell ? getCellBaseFill(cell) : fill;

        cellBaseFillById.set(cellId, effectiveFill);
        rect.fill(effectiveFill);
        scheduleLayerDraw(mazeLayer);
    }

    function removeUiNode(id: string) {
        const node = uiNodes.get(id);
        if (!node) return;

        const nodeLayer = node.getLayer();
        node.destroy();
        uiNodes.delete(id);

        if (nodeLayer) {
            scheduleLayerDraw(nodeLayer);
        }
    }

    function normalizeLayerHint(layerHint?: string): string {
        if (!layerHint) {
            return 'overlay';
        }

        return String(layerHint).trim().replace(/^"|"$/g, '');
    }

    function resolveTargetLayer(layerHint?: string): Konva.Layer {
        const normalized = (layerHint ?? 'overlay').toLowerCase();

        if (normalized === 'agent') {
            return agentLayer;
        }

        if (normalized === 'maze' || normalized === 'base') {
            return mazeLayer;
        }

        // Treat overlay/layer/ui and unknown values as UI overlay layer.
        return uiLayer;
    }

    function resolveUiAttrs(cmd: UiCommand) {
        const { attrs, id, konvaType } = cmd;
        
        const resolved = { ...attrs };

        // ui:layer is transport metadata, not a Konva attribute.
        delete resolved.layer;

        // Apply Arrow defaults if not present
        if (konvaType === "Arrow") {
            resolved.points = resolved.points ?? [-15, 0, 15, 0];
            resolved.pointerLength = resolved.pointerLength ?? 8;
            resolved.pointerWidth = resolved.pointerWidth ?? 8;
        }

        // Convert direction (N/E/S/W) to rotation degrees
        if (resolved.direction) {
            resolved.rotation = resolveDirection(String(resolved.direction), konvaType);
            delete resolved.direction;
        }

        // Resolve anchor-based positioning if anchor is present
        if (resolved.anchor) {
            const cellId = getCellIdFromUiId(id);
            if (cellId) {
                const cell = maze.cells.find((c: { id: string; }) => c.id === cellId);
                if (cell) {
                    const anchor = String(resolved.anchor);
                    const offsetX = Number(resolved.offsetX ?? 0);
                    const offsetY = Number(resolved.offsetY ?? 0);

                    const { x, y } = resolveAnchor(cell, anchor);

                    resolved.x = x + offsetX;
                    resolved.y = y + offsetY;

                    delete resolved.anchor;
                    delete resolved.offsetX;
                    delete resolved.offsetY;
                }
            }
        }

        coerceNumericAttrs(resolved);

        return resolved;
    }

    function coerceNumericAttrs(attrs: Record<string, any>) {
        const numericKeys = [
            'x',
            'y',
            'width',
            'height',
            'radius',
            'offsetX',
            'offsetY',
            'rotation',
            'opacity',
            'strokeWidth',
            'pointerLength',
            'pointerWidth',
            'scaleX',
            'scaleY'
        ];

        for (const key of numericKeys) {
            const value = attrs[key];
            if (typeof value === 'string') {
                const parsed = Number(value);
                if (!Number.isNaN(parsed)) {
                    attrs[key] = parsed;
                }
            }
        }
    }

    /**
     * Extract cell ID from UI element ID
     * e.g., "http://127.0.1.1:8080/cells/5#ui-lock" -> "http://127.0.1.1:8080/cells/5"
     * OR, e.g., "http://127.0.1.1:8080/cells/12/24#ui-something" -> "http://127.0.1.1:8080/cells/12/24"
     * @param uiId
     */
    function getCellIdFromUiId(uiId: string): string | null {
        const marker = "#ui";
        const idx = uiId.indexOf(marker);
        if (idx === -1) return null;
        return uiId.substring(0, idx);
    }

    /**
     * Convert cardinal direction to rotation angle in degrees.
     * Arrow defaults point East at 0° (Konva Arrow points on +X axis).
     * Footstep Path defaults point North at 0°.
     */
    function resolveDirection(direction: string, konvaType: string): number {
        const normalized = direction.toUpperCase();

        if (konvaType === "Arrow") {
            switch (normalized) {
                case "N": return 270;
                case "E": return 0;
                case "S": return 90;
                case "W": return 180;
                default:  return 0;
            }
        }

        // Default mapping for Path and other shapes whose 0° points North.
        switch (normalized) {
            case "N": return 0;
            case "E": return 90;
            case "S": return 180;
            case "W": return 270;
            default:  return 0;
        }
    }

    /**
     * E.g., "NW", "C", "SE", etc.
     * @param cell
     * @param anchor
     */
    function resolveAnchor(
        cell: Cell,
        anchor: string
    ): { x: number; y: number } {
        const baseX = cell.x * CELL_SIZE + PADDING;
        const baseY = cell.y * CELL_SIZE + PADDING;
    
        const margin = 0.5; // fraction of cell
        const left   = baseX + CELL_SIZE * 0.25;
        const right  = baseX + CELL_SIZE * 0.75;
        const top    = baseY + CELL_SIZE * 0.25;
        const bottom = baseY + CELL_SIZE * 0.75;

        const cx = baseX + CELL_SIZE / 2;
        const cy = baseY + CELL_SIZE / 2;

        switch (anchor) {
            case "NW": return { x: left,  y: top };
            case "N":  return { x: cx,    y: top };
            case "NE": return { x: right, y: top };
            case "W":  return { x: left,  y: cy };
            case "C":  return { x: cx,    y: cy };
            case "E":  return { x: right, y: cy };
            case "SW": return { x: left,  y: bottom };
            case "S":  return { x: cx,    y: bottom };
            case "SE": return { x: right, y: bottom };
            default:   return { x: cx,    y: cy };
        }
    }

</script>

<div bind:this={container} class="w-full h-full"></div>
