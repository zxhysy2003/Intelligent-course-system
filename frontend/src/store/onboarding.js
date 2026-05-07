import { ref } from "vue";
import { defineStore } from "pinia";
import { GetOnboardingStatus, SubmitOnboarding } from "@/api/onboarding";

const defaultStatus = () => ({
    completed: false,
    currentLevel: null,
    learningGoal: "",
    tagIds: [],
});

const normalizeTagIds = (tagIds) => {
    if (!Array.isArray(tagIds)) return [];
    return tagIds
        .map((id) => Number(id))
        .filter((id) => Number.isFinite(id));
};

const normalizeStatus = (payload) => ({
    completed: Boolean(payload?.completed),
    currentLevel: payload?.currentLevel ?? null,
    learningGoal: payload?.learningGoal ?? "",
    tagIds: normalizeTagIds(payload?.tagIds),
});

export const useOnboardingStore = defineStore("onboarding", () => {
    const loaded = ref(false);
    const status = ref(defaultStatus());

    const fetchStatus = async (force = false) => {
        if (loaded.value && !force) {
            return status.value;
        }

        const res = await GetOnboardingStatus();
        if (res?.data?.code !== 200) {
            throw new Error(res?.data?.msg || "获取引导状态失败");
        }

        status.value = normalizeStatus(res.data.data);
        loaded.value = true;
        return status.value;
    };

    const submit = async (payload) => {
        const res = await SubmitOnboarding(payload);
        if (res?.data?.code !== 200) {
            throw new Error(res?.data?.msg || "提交引导信息失败");
        }

        status.value = normalizeStatus({
            completed: true,
            currentLevel: payload.currentLevel,
            learningGoal: payload.learningGoal,
            tagIds: payload.tagIds,
        });
        loaded.value = true;
        return res.data.data;
    };

    const reset = () => {
        loaded.value = false;
        status.value = defaultStatus();
    };

    return {
        loaded,
        status,
        fetchStatus,
        submit,
        reset,
    };
});
