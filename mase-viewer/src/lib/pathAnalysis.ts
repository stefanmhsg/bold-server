import type { AgentMovedEvent } from './mazeState.svelte';
import type { Cell, MazeLayout } from './types';

export interface PathAnalysisStep {
    step: number;
    sequenceIndex: number;
    original: string;
    normalizedCell: string;
    cellId: string;
    agent?: string | null;
    timestamp?: number | null;
}

export interface PathAnalysisInvalidEntry {
    index: number;
    value: string;
    reason: string;
}

export interface PathAnalysisParseResult {
    steps: PathAnalysisStep[];
    invalid: PathAnalysisInvalidEntry[];
}

interface ResolvedCellToken {
    normalizedCell: string;
    cellId: string;
}

const CELL_MARKER = '/cells/';
const TOKEN_SPLIT_PATTERN = /[\s,;]+/;

export function parsePathAnalysisInput(input: string, maze: MazeLayout): PathAnalysisParseResult {
    const tokens = splitPathInput(input);
    return parsePathTokens(tokens, maze, (token, index, step) => ({
        step,
        sequenceIndex: index + 1,
        original: token
    }));
}

export function agentMovementEventsToPath(
    events: readonly AgentMovedEvent[],
    maze: MazeLayout
): PathAnalysisParseResult {
    const tokens = events.map((event) => event.cell);

    return parsePathTokens(tokens, maze, (token, index, step) => ({
        step,
        sequenceIndex: index + 1,
        original: token,
        agent: events[index]?.agent ?? null,
        timestamp: events[index]?.timestamp ?? null
    }));
}

export function normalizeCellResource(value: string): string | null {
    const cleaned = cleanPathToken(value);
    if (!cleaned) {
        return null;
    }

    const markerIndex = cleaned.indexOf(CELL_MARKER);
    if (markerIndex !== -1) {
        return trimCellPath(cleaned.substring(markerIndex));
    }

    if (cleaned.startsWith('cells/')) {
        return trimCellPath(`/${cleaned}`);
    }

    return null;
}

export function shortCellLabel(cellId: string): string {
    return normalizeCellResource(cellId)?.substring(CELL_MARKER.length) ?? cellId;
}

function splitPathInput(input: string): string[] {
    return input
        .split(TOKEN_SPLIT_PATTERN)
        .map(cleanPathToken)
        .filter((token) => token.length > 0);
}

function parsePathTokens(
    tokens: readonly string[],
    maze: MazeLayout,
    createPartialStep: (token: string, index: number, step: number) => Partial<PathAnalysisStep>
): PathAnalysisParseResult {
    const resolver = createCellResolver(maze);
    const steps: PathAnalysisStep[] = [];
    const invalid: PathAnalysisInvalidEntry[] = [];

    tokens.forEach((token, index) => {
        const resolved = resolver(token);
        if (!resolved) {
            invalid.push({
                index: index + 1,
                value: token,
                reason: 'No matching cell in the current maze'
            });
            return;
        }

        const step = steps.length + 1;
        steps.push({
            ...createPartialStep(token, index, step),
            step,
            sequenceIndex: index + 1,
            original: token,
            normalizedCell: resolved.normalizedCell,
            cellId: resolved.cellId
        });
    });

    return { steps, invalid };
}

function createCellResolver(maze: MazeLayout): (token: string) => ResolvedCellToken | null {
    const exactById = new Map<string, Cell>();
    const byNormalized = new Map<string, Cell>();

    for (const cell of maze.cells) {
        exactById.set(cell.id, cell);

        const normalized = normalizeCellResource(cell.id);
        if (normalized && !byNormalized.has(normalized)) {
            byNormalized.set(normalized, cell);
        }
    }

    return (token: string) => {
        const cleaned = cleanPathToken(token);
        if (!cleaned) {
            return null;
        }

        const exact = exactById.get(cleaned);
        if (exact) {
            return {
                normalizedCell: normalizeCellResource(exact.id) ?? cleaned,
                cellId: exact.id
            };
        }

        const normalized = normalizeCellResource(cleaned);
        if (!normalized) {
            return null;
        }

        const normalizedExact = exactById.get(normalized);
        if (normalizedExact) {
            return {
                normalizedCell: normalized,
                cellId: normalizedExact.id
            };
        }

        const normalizedMatch = byNormalized.get(normalized);
        if (normalizedMatch) {
            return {
                normalizedCell: normalized,
                cellId: normalizedMatch.id
            };
        }

        for (const cell of maze.cells) {
            if (cell.id.endsWith(normalized)) {
                return {
                    normalizedCell: normalized,
                    cellId: cell.id
                };
            }
        }

        return null;
    };
}

function cleanPathToken(value: string): string {
    return value
        .trim()
        .replace(/^[<"']+/, '')
        .replace(/[>"'.]+$/, '');
}

function trimCellPath(value: string): string {
    const path = value.split(/[?#]/, 1)[0] ?? value;
    return path.replace(/\/+$/, '');
}
