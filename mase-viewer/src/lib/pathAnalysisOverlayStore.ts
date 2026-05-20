import { writable } from 'svelte/store';
import type { PathAnalysisInvalidEntry, PathAnalysisStep } from './pathAnalysis';

export interface PathAnalysisOverlayState {
    visible: boolean;
    sourceLabel: string | null;
    steps: PathAnalysisStep[];
    invalid: PathAnalysisInvalidEntry[];
    currentStep: number | null;
    dimFutureSteps: boolean;
    fitRequest: number;
}

const EMPTY_STATE: PathAnalysisOverlayState = {
    visible: false,
    sourceLabel: null,
    steps: [],
    invalid: [],
    currentStep: null,
    dimFutureSteps: true,
    fitRequest: 0
};

function createPathAnalysisOverlayStore() {
    const { subscribe, set, update } = writable<PathAnalysisOverlayState>(EMPTY_STATE);

    return {
        subscribe,
        setPath(
            steps: PathAnalysisStep[],
            invalid: PathAnalysisInvalidEntry[],
            sourceLabel: string
        ) {
            update((state) => ({
                ...state,
                visible: steps.length > 0,
                sourceLabel,
                steps,
                invalid,
                currentStep: steps.at(-1)?.step ?? null
            }));
        },
        clearPath() {
            set({ ...EMPTY_STATE, fitRequest: Date.now() });
        },
        setVisible(visible: boolean) {
            update((state) => ({ ...state, visible }));
        },
        setCurrentStep(step: number) {
            update((state) => {
                const maxStep = state.steps.at(-1)?.step ?? 0;
                const currentStep = maxStep > 0 ? Math.min(Math.max(1, step), maxStep) : null;
                return { ...state, currentStep };
            });
        },
        setDimFutureSteps(dimFutureSteps: boolean) {
            update((state) => ({ ...state, dimFutureSteps }));
        },
        requestFitToPath() {
            update((state) => ({ ...state, fitRequest: state.fitRequest + 1 }));
        }
    };
}

export const pathAnalysisOverlay = createPathAnalysisOverlayStore();
