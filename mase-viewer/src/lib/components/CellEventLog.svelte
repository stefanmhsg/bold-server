<script lang="ts">
    //import type { CellStateEvent } from '$lib/mazeState.svelte';

    //let { events } = $props<{ events: CellStateEvent[] }>();
</script>

<div class="border rounded-lg overflow-hidden max-h-[300px] overflow-y-auto">
    <table class="w-full text-left">
        <thead class="bg-gray-100 border-b sticky top-0">
            <tr>
                <th class="p-2">Time</th>
                <th class="p-2">State</th>
                <th class="p-2">Cell</th>
            </tr>
        </thead>
        <tbody>
            {#if 0 === 0}
                <tr>
                    <td colspan="3" class="p-4 text-center text-gray-500">
                        No cell updates yet.
                    </td>
                </tr>
            {/if}
        </tbody>
    </table>
</div>
