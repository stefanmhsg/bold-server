import type { PageLoad } from './$types';
import type { MazeAdminSnapshot } from '$lib/types';


export const load: PageLoad = async ({ fetch }) => {
    try {
        const response = await fetch('http://localhost:8080/admin/maze');
        if (!response.ok) {
            throw new Error('Failed to fetch maze admin snapshot');
        }

        const snapshot: MazeAdminSnapshot = await response.json();

        return {
            maze: snapshot.layout,
            uiSnapshot: snapshot.ui
        };

    } catch (error) {
        console.error("Failed to load maze layout", error);
    }
    return { maze: null, uiSnapshot: [] };
};
