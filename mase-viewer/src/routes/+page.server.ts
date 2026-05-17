import { env } from '$env/dynamic/private';
import { resolveMazeServerRuntimeConfig } from '$lib/mazeServerConfig';
import type { MazeAdminSnapshot } from '$lib/types';
import type { PageServerLoad } from './$types';

export const load: PageServerLoad = async ({ fetch }) => {
    const serverConfig = resolveMazeServerRuntimeConfig(env);

    try {
        const response = await fetch(`${serverConfig.internalHttpBaseUrl}/admin/maze`);
        if (!response.ok) {
            throw new Error(`Failed to fetch maze admin snapshot: ${response.status} ${response.statusText}`);
        }

        const snapshot: MazeAdminSnapshot = await response.json();

        return {
            maze: snapshot.layout,
            uiSnapshot: snapshot.ui,
            scenarioName: snapshot.scenario ?? null,
            serverConfig: serverConfig.client,
            loadError: null
        };

    } catch (error) {
        console.error("Failed to load maze layout", error);
        return {
            maze: null,
            uiSnapshot: [],
            scenarioName: null,
            serverConfig: serverConfig.client,
            loadError: error instanceof Error ? error.message : String(error)
        };
    }
};
