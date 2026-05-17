import type { MazeEvent } from './mazeState.svelte';

const DB_NAME = 'mase-viewer-event-archive';
const DB_VERSION = 2;
const EVENT_STORE = 'events';
const RUN_ID_STORAGE_KEY = 'maze-viewer.current-run-id.v1';

export const ARCHIVE_EVENT_TYPES = ['AGENT_MOVED', 'TRANSACTION', 'UI_UPSERT', 'UI_DELETE'] as const;
export type ArchiveEventType = typeof ARCHIVE_EVENT_TYPES[number];
export type ArchiveEventTypeCounts = Record<ArchiveEventType, number>;
export type ArchiveExportStatus = 'saved' | 'downloaded' | 'empty' | 'canceled' | 'unavailable';

export interface ArchiveExportResult {
    status: ArchiveExportStatus;
    count: number;
    fileName?: string;
    message?: string;
}

export interface ArchivedMazeEvent {
    archiveId?: number;
    runId: string;
    archivedAt: number;
    type: MazeEvent['type'];
    timestamp: number;
    agent?: string | null;
    cell?: string | null;
    graph?: string | null;
    transactionId?: number | null;
    event: MazeEvent;
}

export interface ArchivedEventPage<T extends MazeEvent> {
    events: T[];
    nextCursor: number | null;
    hasMore: boolean;
}

export interface ArchiveEventQuery {
    searchText?: string;
}

export interface ArchiveExportOptions {
    eventTypes?: readonly ArchiveEventType[];
}

type StoredEventType = ArchiveEventType;

export function emptyArchiveEventTypeCounts(): ArchiveEventTypeCounts {
    return {
        AGENT_MOVED: 0,
        TRANSACTION: 0,
        UI_UPSERT: 0,
        UI_DELETE: 0
    };
}

type DirectoryPicker = (options?: { mode?: 'read' | 'readwrite' }) => Promise<DirectoryHandle>;

interface DirectoryHandle {
    getFileHandle(name: string, options?: { create?: boolean }): Promise<FileHandle>;
}

interface FileHandle {
    createWritable(): Promise<WritableFile>;
}

interface WritableFile {
    write(data: Blob): Promise<void>;
    close(): Promise<void>;
}

class EventArchive {
    private dbPromise: Promise<IDBDatabase> | null = null;

    getCurrentRunId(): string {
        if (typeof window === 'undefined') {
            return 'server-render';
        }

        const existing = window.localStorage.getItem(RUN_ID_STORAGE_KEY);
        if (existing) {
            return existing;
        }

        return this.startNewRun();
    }

    startNewRun(): string {
        const runId = `run-${new Date().toISOString().replace(/[:.]/g, '-')}`;

        if (typeof window !== 'undefined') {
            window.localStorage.setItem(RUN_ID_STORAGE_KEY, runId);
        }

        return runId;
    }

    async appendEvents(events: MazeEvent[], runId: string): Promise<number> {
        if (events.length === 0 || !this.isAvailable()) {
            return 0;
        }

        const db = await this.openDb();
        await new Promise<void>((resolve, reject) => {
            const tx = db.transaction(EVENT_STORE, 'readwrite');
            const store = tx.objectStore(EVENT_STORE);
            const archivedAt = Date.now();

            for (const event of events) {
                store.add(this.toArchiveRecord(event, runId, archivedAt));
            }

            tx.oncomplete = () => resolve();
            tx.onerror = () => reject(tx.error ?? new Error('Failed to archive events'));
            tx.onabort = () => reject(tx.error ?? new Error('Event archive transaction aborted'));
        });

        return events.length;
    }

    async getRecentEventsByType<T extends MazeEvent>(type: StoredEventType, limit: number): Promise<T[]> {
        const page = await this.getEventsByType<T>(type, limit);
        return page.events;
    }

    async getEventsByType<T extends MazeEvent>(
        type: StoredEventType,
        limit: number,
        beforeArchiveId?: number | null,
        query: ArchiveEventQuery = {}
    ): Promise<ArchivedEventPage<T>> {
        if (limit <= 0 || !this.isAvailable()) {
            return { events: [], nextCursor: null, hasMore: false };
        }

        const db = await this.openDb();

        return new Promise<ArchivedEventPage<T>>((resolve, reject) => {
            const records: ArchivedMazeEvent[] = [];
            const tx = db.transaction(EVENT_STORE, 'readonly');
            const index = tx.objectStore(EVENT_STORE).index('typeArchiveId');
            const upperArchiveId = beforeArchiveId === null || beforeArchiveId === undefined
                ? Number.MAX_SAFE_INTEGER
                : beforeArchiveId - 1;

            if (upperArchiveId < 0) {
                resolve({ events: [], nextCursor: null, hasMore: false });
                return;
            }

            const range = IDBKeyRange.bound([type, 0], [type, upperArchiveId]);
            const request = index.openCursor(range, 'prev');
            const normalizedSearch = query.searchText?.trim().toLowerCase() ?? '';

            request.onsuccess = () => {
                const cursor = request.result;
                if (!cursor || records.length > limit) {
                    const pageRecords = records.slice(0, limit);
                    const nextCursor = this.nextCursorForRecords(pageRecords);

                    resolve({
                        events: pageRecords.map((record) => record.event as T),
                        nextCursor,
                        hasMore: records.length > limit || Boolean(cursor)
                    });
                    return;
                }

                const record = cursor.value as ArchivedMazeEvent;
                if (!normalizedSearch || this.recordMatchesSearch(record, normalizedSearch)) {
                    records.push(record);
                }
                cursor.continue();
            };

            request.onerror = () => reject(request.error ?? new Error('Failed to read archived events'));
        });
    }

    async count(): Promise<number> {
        if (!this.isAvailable()) {
            return 0;
        }

        const db = await this.openDb();

        return new Promise<number>((resolve, reject) => {
            const tx = db.transaction(EVENT_STORE, 'readonly');
            const request = tx.objectStore(EVENT_STORE).count();
            request.onsuccess = () => resolve(request.result);
            request.onerror = () => reject(request.error ?? new Error('Failed to count archived events'));
        });
    }

    async countByType(): Promise<ArchiveEventTypeCounts> {
        const counts = emptyArchiveEventTypeCounts();
        if (!this.isAvailable()) {
            return counts;
        }

        const db = await this.openDb();

        await Promise.all(ARCHIVE_EVENT_TYPES.map((type) => new Promise<void>((resolve, reject) => {
            const tx = db.transaction(EVENT_STORE, 'readonly');
            const request = tx.objectStore(EVENT_STORE).index('type').count(type);
            request.onsuccess = () => {
                counts[type] = request.result;
                resolve();
            };
            request.onerror = () => reject(request.error ?? new Error(`Failed to count ${type} events`));
        })));

        return counts;
    }

    async clear(): Promise<void> {
        if (!this.isAvailable()) {
            return;
        }

        const db = await this.openDb();
        await new Promise<void>((resolve, reject) => {
            const tx = db.transaction(EVENT_STORE, 'readwrite');
            tx.objectStore(EVENT_STORE).clear();
            tx.oncomplete = () => resolve();
            tx.onerror = () => reject(tx.error ?? new Error('Failed to clear archived events'));
            tx.onabort = () => reject(tx.error ?? new Error('Event archive clear aborted'));
        });
    }

    async exportNdjson(options: ArchiveExportOptions = {}): Promise<ArchiveExportResult> {
        if (!this.isAvailable()) {
            return { status: 'unavailable', count: 0, message: 'IndexedDB is not available in this browser.' };
        }

        const eventTypes = this.normalizeEventTypes(options.eventTypes);
        if (eventTypes.length === 0) {
            return { status: 'empty', count: 0, message: 'No event types selected.' };
        }

        const records = await this.getAllRecords(eventTypes);
        if (records.length === 0) {
            return { status: 'empty', count: 0, message: 'No logs to export.' };
        }

        const fileName = this.createFileName(records[0]?.runId ?? this.getCurrentRunId());
        const body = `${records.map((record) => JSON.stringify(record)).join('\n')}\n`;
        const blob = new Blob([body], { type: 'application/x-ndjson;charset=utf-8' });
        const status = await this.saveBlob(blob, fileName);

        return {
            status,
            count: records.length,
            fileName,
            message: status === 'saved'
                ? `Saved ${records.length} log events to ${fileName}.`
                : status === 'downloaded'
                    ? `Downloaded ${records.length} log events to ${fileName}.`
                    : 'Log export canceled.'
        };
    }

    private isAvailable(): boolean {
        return typeof window !== 'undefined' && 'indexedDB' in window;
    }

    private openDb(): Promise<IDBDatabase> {
        if (this.dbPromise) {
            return this.dbPromise;
        }

        this.dbPromise = new Promise<IDBDatabase>((resolve, reject) => {
            const request = window.indexedDB.open(DB_NAME, DB_VERSION);

            request.onupgradeneeded = () => {
                const db = request.result;
                let store: IDBObjectStore;

                if (!db.objectStoreNames.contains(EVENT_STORE)) {
                    store = db.createObjectStore(EVENT_STORE, {
                        keyPath: 'archiveId',
                        autoIncrement: true
                    });
                } else {
                    if (!request.transaction) {
                        throw new Error('Missing IndexedDB upgrade transaction');
                    }

                    store = request.transaction.objectStore(EVENT_STORE);
                }

                this.ensureIndex(store, 'runId', 'runId');
                this.ensureIndex(store, 'type', 'type');
                this.ensureIndex(store, 'timestamp', 'timestamp');
                this.ensureIndex(store, 'typeTimestamp', ['type', 'timestamp']);
                this.ensureIndex(store, 'typeArchiveId', ['type', 'archiveId']);
                this.ensureIndex(store, 'agent', 'agent');
                this.ensureIndex(store, 'graph', 'graph');
                this.ensureIndex(store, 'transactionId', 'transactionId');
            };

            request.onsuccess = () => resolve(request.result);
            request.onerror = () => {
                this.dbPromise = null;
                reject(request.error ?? new Error('Failed to open event archive'));
            };
        });

        return this.dbPromise;
    }

    private toArchiveRecord(event: MazeEvent, runId: string, archivedAt: number): ArchivedMazeEvent {
        return {
            runId,
            archivedAt,
            type: event.type,
            timestamp: event.timestamp,
            agent: 'agent' in event ? event.agent : '',
            cell: 'cell' in event ? event.cell : '',
            graph: 'graph' in event ? event.graph ?? '' : '',
            transactionId: 'transactionId' in event ? event.transactionId ?? -1 : -1,
            event
        };
    }

    private ensureIndex(store: IDBObjectStore, name: string, keyPath: string | string[]): void {
        if (!store.indexNames.contains(name)) {
            store.createIndex(name, keyPath);
        }
    }

    private nextCursorForRecords(records: ArchivedMazeEvent[]): number | null {
        let nextCursor: number | null = null;

        for (const record of records) {
            if (typeof record.archiveId !== 'number') {
                continue;
            }

            nextCursor = nextCursor === null ? record.archiveId : Math.min(nextCursor, record.archiveId);
        }

        return nextCursor;
    }

    private async getAllRecords(eventTypes: readonly ArchiveEventType[]): Promise<ArchivedMazeEvent[]> {
        const db = await this.openDb();
        const allowedTypes = new Set(eventTypes);

        return new Promise<ArchivedMazeEvent[]>((resolve, reject) => {
            const records: ArchivedMazeEvent[] = [];
            const tx = db.transaction(EVENT_STORE, 'readonly');
            const request = tx.objectStore(EVENT_STORE).openCursor();

            request.onsuccess = () => {
                const cursor = request.result;
                if (!cursor) {
                    resolve(records);
                    return;
                }

                const record = cursor.value as ArchivedMazeEvent;
                if (allowedTypes.has(record.type)) {
                    records.push(record);
                }
                cursor.continue();
            };

            request.onerror = () => reject(request.error ?? new Error('Failed to read archived events'));
        });
    }

    private normalizeEventTypes(eventTypes?: readonly ArchiveEventType[]): ArchiveEventType[] {
        if (!eventTypes) {
            return [...ARCHIVE_EVENT_TYPES];
        }

        const allowedTypes = new Set<ArchiveEventType>(ARCHIVE_EVENT_TYPES);
        return eventTypes.filter((type, index, values) => allowedTypes.has(type) && values.indexOf(type) === index);
    }

    private recordMatchesSearch(record: ArchivedMazeEvent, normalizedSearch: string): boolean {
        const parts = [
            record.type,
            record.agent ?? '',
            record.cell ?? '',
            record.graph ?? '',
            record.transactionId === null || record.transactionId === undefined ? '' : String(record.transactionId)
        ];

        if (parts.some((part) => part.toLowerCase().includes(normalizedSearch))) {
            return true;
        }

        return JSON.stringify(record.event).toLowerCase().includes(normalizedSearch);
    }

    private createFileName(runId: string): string {
        const timestamp = new Date().toISOString().replace(/[:.]/g, '-');
        const safeRunId = runId.replace(/[^a-zA-Z0-9._-]/g, '-');
        return `mase-viewer-${safeRunId}-${timestamp}.ndjson`;
    }

    private async saveBlob(blob: Blob, fileName: string): Promise<Exclude<ArchiveExportStatus, 'empty' | 'unavailable'>> {
        const windowWithPicker = window as Window & { showDirectoryPicker?: DirectoryPicker };

        if (windowWithPicker.showDirectoryPicker) {
            try {
                const directory = await windowWithPicker.showDirectoryPicker({ mode: 'readwrite' });
                const file = await directory.getFileHandle(fileName, { create: true });
                const writable = await file.createWritable();
                await writable.write(blob);
                await writable.close();
                return 'saved';
            } catch (error) {
                if (typeof error === 'object' && error !== null && 'name' in error && error.name === 'AbortError') {
                    return 'canceled';
                }

                console.warn('Directory export failed, falling back to browser download', error);
            }
        }

        const url = URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = fileName;
        link.rel = 'noopener';
        document.body.appendChild(link);
        link.click();
        link.remove();
        URL.revokeObjectURL(url);

        return 'downloaded';
    }
}

export const eventArchive = new EventArchive();
