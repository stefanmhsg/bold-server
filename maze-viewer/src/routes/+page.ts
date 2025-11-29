import type { PageLoad } from './$types';
import type { MazeLayout } from '$lib/types';

export const load: PageLoad = async ({ fetch }) => {
    try {
        const response = await fetch('http://localhost:8080/admin/maze');
        if (response.ok) {
            const maze: MazeLayout = await response.json();
            return { maze };
        }
    } catch (error) {
        console.error("Failed to load maze layout", error);
    }
    return { maze: null };
};
