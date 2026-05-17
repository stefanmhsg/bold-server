export interface MazeServerClientConfig {
    httpBaseUrl: string;
    webSocketUrl: string;
    adminMazeUrl: string;
    adminResetUrl: string;
}

export interface MazeServerRuntimeConfig {
    internalHttpBaseUrl: string;
    client: MazeServerClientConfig;
}

const DEFAULT_HTTP_BASE_URL = 'http://localhost:8080';

export function resolveMazeServerRuntimeConfig(env: Record<string, string | undefined>): MazeServerRuntimeConfig {
    const internalHttpBaseUrl = normalizeHttpBaseUrl(
        env.MASE_SERVER_INTERNAL_HTTP_URL
            ?? env.MASE_SERVER_HTTP_URL
            ?? env.PUBLIC_MASE_SERVER_HTTP_URL
            ?? DEFAULT_HTTP_BASE_URL
    );
    const publicHttpBaseUrl = normalizeHttpBaseUrl(
        env.PUBLIC_MASE_SERVER_HTTP_URL
            ?? env.MASE_SERVER_PUBLIC_HTTP_URL
            ?? env.MASE_SERVER_HTTP_URL
            ?? DEFAULT_HTTP_BASE_URL
    );
    const publicWebSocketUrl = normalizeWebSocketUrl(
        env.PUBLIC_MASE_SERVER_WS_URL
            ?? env.MASE_SERVER_PUBLIC_WS_URL
            ?? deriveWebSocketUrl(publicHttpBaseUrl)
    );

    return {
        internalHttpBaseUrl,
        client: createMazeServerClientConfig(publicHttpBaseUrl, publicWebSocketUrl)
    };
}

export function createMazeServerClientConfig(
    httpBaseUrl = DEFAULT_HTTP_BASE_URL,
    webSocketUrl = deriveWebSocketUrl(httpBaseUrl)
): MazeServerClientConfig {
    const normalizedHttpBaseUrl = normalizeHttpBaseUrl(httpBaseUrl);
    const normalizedWebSocketUrl = normalizeWebSocketUrl(webSocketUrl);

    return {
        httpBaseUrl: normalizedHttpBaseUrl,
        webSocketUrl: normalizedWebSocketUrl,
        adminMazeUrl: `${normalizedHttpBaseUrl}/admin/maze`,
        adminResetUrl: `${normalizedHttpBaseUrl}/admin/maze/reset`
    };
}

export function normalizeHttpBaseUrl(value: string): string {
    return value.trim().replace(/\/+$/, '') || DEFAULT_HTTP_BASE_URL;
}

export function normalizeWebSocketUrl(value: string): string {
    return value.trim().replace(/\/+$/, '') || deriveWebSocketUrl(DEFAULT_HTTP_BASE_URL);
}

export function deriveWebSocketUrl(httpBaseUrl: string): string {
    try {
        const url = new URL(normalizeHttpBaseUrl(httpBaseUrl));
        url.protocol = url.protocol === 'https:' ? 'wss:' : 'ws:';
        url.pathname = '/ws';
        url.search = '';
        url.hash = '';
        return url.toString().replace(/\/+$/, '');
    } catch {
        return 'ws://localhost:8080/ws';
    }
}
