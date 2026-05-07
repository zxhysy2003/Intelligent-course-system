<template>
  <div class="onboarding-page" v-loading="loading">
    <section class="page-head">
      <div>
        <p class="eyebrow">新用户引导</p>
        <h2 class="page-title">定制你的学习起点</h2>
        <p class="subtitle">完成三个选择后，系统会优先展示更适合当前阶段的课程。</p>
      </div>
      <el-button :icon="Refresh" @click="refreshData" :loading="loading" plain>
        刷新选项
      </el-button>
    </section>

    <section class="wizard-shell">
      <aside class="step-rail">
        <el-steps :active="activeStep" direction="vertical" finish-status="success">
          <el-step title="学习基础" />
          <el-step title="学习目标" />
          <el-step title="兴趣方向" />
        </el-steps>
      </aside>

      <main class="wizard-main">
        <section v-show="activeStep === 0" class="step-panel">
          <div class="section-head">
            <el-icon><Reading /></el-icon>
            <div>
              <h3>当前基础</h3>
              <p>选择一个最接近自己的水平。</p>
            </div>
          </div>

          <div class="option-grid level-grid">
            <button
              v-for="level in levelOptions"
              :key="level.value"
              class="choice-card"
              :class="{ selected: form.currentLevel === level.value }"
              type="button"
              @click="form.currentLevel = level.value"
            >
              <span class="choice-label">{{ level.label }}</span>
              <span class="choice-meta">{{ levelHint(level.value) }}</span>
            </button>
          </div>
        </section>

        <section v-show="activeStep === 1" class="step-panel">
          <div class="section-head">
            <el-icon><DataAnalysis /></el-icon>
            <div>
              <h3>学习目标</h3>
              <p>目标可以先留空，之后也能重新调整。</p>
            </div>
          </div>

          <div class="option-grid goal-grid">
            <button
              v-for="goal in learningGoalOptions"
              :key="goal.value"
              class="choice-card"
              :class="{ selected: form.learningGoal === goal.value }"
              type="button"
              @click="form.learningGoal = goal.value"
            >
              <span class="choice-label">{{ goal.label }}</span>
              <span class="choice-meta">{{ goalHint(goal.value) }}</span>
            </button>
          </div>
        </section>

        <section v-show="activeStep === 2" class="step-panel">
          <div class="section-head">
            <el-icon><MagicStick /></el-icon>
            <div>
              <h3>兴趣方向</h3>
              <p>至少选择一个标签，用来建立初始兴趣画像。</p>
            </div>
          </div>

          <div class="tag-toolbar">
            <span>已选 {{ form.tagIds.length }} 个</span>
            <el-button link type="primary" @click="clearTags" :disabled="!form.tagIds.length">
              清空
            </el-button>
          </div>

          <div v-if="tagGroups.length" class="tag-groups">
            <div v-for="group in tagGroups" :key="group.type" class="tag-group">
              <div class="group-title">{{ tagTypeLabel(group.type) }}</div>
              <div class="tag-list">
                <button
                  v-for="tag in group.items"
                  :key="tag.id"
                  class="tag-chip"
                  :class="{ selected: isTagSelected(tag.id) }"
                  type="button"
                  @click="toggleTag(tag.id)"
                >
                  {{ tag.name }}
                </button>
              </div>
            </div>
          </div>

          <el-empty v-else description="暂无可选标签" />
        </section>

        <div class="wizard-actions">
          <el-button @click="prevStep" :disabled="activeStep === 0 || submitting">
            上一步
          </el-button>
          <el-button
            v-if="activeStep < maxStep"
            type="primary"
            :icon="ArrowRight"
            @click="nextStep"
          >
            下一步
          </el-button>
          <el-button
            v-else
            type="primary"
            :icon="CircleCheck"
            :loading="submitting"
            @click="submitOnboarding"
          >
            完成引导
          </el-button>
        </div>
      </main>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import {
  ArrowRight,
  CircleCheck,
  DataAnalysis,
  MagicStick,
  Reading,
  Refresh
} from "@element-plus/icons-vue";
import { GetOnboardingOptions } from "@/api/onboarding";
import { useOnboardingStore } from "@/store/onboarding";
import { logger } from "@/utils/logger";

const router = useRouter();
const route = useRoute();
const onboardingStore = useOnboardingStore();

const activeStep = ref(0);
const maxStep = 2;
const loading = ref(false);
const submitting = ref(false);

const options = ref({
  tags: [],
  levels: [],
  learningGoals: [],
});

const form = reactive({
  currentLevel: null,
  learningGoal: "",
  tagIds: [],
});

const normalizeNumber = (value) => {
  const number = Number(value);
  return Number.isFinite(number) ? number : null;
};

const normalizeTags = (tags) => {
  if (!Array.isArray(tags)) return [];
  return tags
    .map((tag) => ({
      id: normalizeNumber(tag?.id),
      name: tag?.name || "",
      type: tag?.type || "OTHER",
    }))
    .filter((tag) => tag.id !== null && tag.name);
};

const normalizeOptions = (payload) => ({
  tags: normalizeTags(payload?.tags),
  levels: Array.isArray(payload?.levels) ? payload.levels : [],
  learningGoals: Array.isArray(payload?.learningGoals) ? payload.learningGoals : [],
});

const levelOptions = computed(() => options.value.levels);

const learningGoalOptions = computed(() => [
  ...options.value.learningGoals,
  { value: "", label: "暂不确定" },
]);

const tagGroups = computed(() => {
  const groups = new Map();
  options.value.tags.forEach((tag) => {
    const type = tag.type || "OTHER";
    if (!groups.has(type)) {
      groups.set(type, []);
    }
    groups.get(type).push(tag);
  });

  return Array.from(groups.entries()).map(([type, items]) => ({ type, items }));
});

const tagTypeLabel = (type) => {
  const labels = {
    TECH: "技术方向",
    FIELD: "应用领域",
    THEORY: "理论基础",
    OTHER: "其他方向",
  };
  return labels[type] || type;
};

const levelHint = (value) => {
  const hints = {
    1: "从概念和基础语法开始",
    2: "有入门经验，需要体系化提升",
    3: "已有项目或课程基础",
  };
  return hints[value] || "按当前学习体验选择";
};

const goalHint = (value) => {
  const hints = {
    JOB: "强化岗位技能与实践项目",
    PROJECT: "围绕完整项目补齐能力",
    FOUNDATION: "夯实长期学习底座",
    EXAM: "聚焦考点与阶段复习",
    "": "先根据兴趣探索课程",
  };
  return hints[value] || "保持灵活的学习路径";
};

const applyStatusToForm = (status) => {
  form.currentLevel = status.currentLevel ?? null;
  form.learningGoal = status.learningGoal ?? "";
  form.tagIds = Array.isArray(status.tagIds) ? [...status.tagIds] : [];
};

const fetchOptions = async () => {
  const res = await GetOnboardingOptions();
  if (res?.data?.code !== 200) {
    throw new Error(res?.data?.msg || "获取引导选项失败");
  }
  options.value = normalizeOptions(res.data.data);
};

const refreshData = async () => {
  loading.value = true;
  try {
    await Promise.all([
      fetchOptions(),
      onboardingStore.fetchStatus(true),
    ]);
    applyStatusToForm(onboardingStore.status);
  } catch (e) {
    logger.error("加载引导信息失败", e);
  } finally {
    loading.value = false;
  }
};

const isTagSelected = (tagId) => form.tagIds.includes(tagId);

const toggleTag = (tagId) => {
  if (isTagSelected(tagId)) {
    form.tagIds = form.tagIds.filter((id) => id !== tagId);
    return;
  }
  form.tagIds = [...form.tagIds, tagId];
};

const clearTags = () => {
  form.tagIds = [];
};

const validateStep = (step) => {
  if (step === 0 && !form.currentLevel) {
    logger.warn("请选择当前基础");
    return false;
  }

  if (step === 2 && !form.tagIds.length) {
    logger.warn("请至少选择一个兴趣方向");
    return false;
  }

  return true;
};

const nextStep = () => {
  if (!validateStep(activeStep.value)) return;
  activeStep.value = Math.min(maxStep, activeStep.value + 1);
};

const prevStep = () => {
  activeStep.value = Math.max(0, activeStep.value - 1);
};

const resolveRedirectPath = () => {
  const redirect = Array.isArray(route.query.redirect) ? route.query.redirect[0] : route.query.redirect;
  if (redirect && redirect.startsWith("/") && redirect !== "/onboarding") {
    return redirect;
  }
  return "/recommend";
};

const submitOnboarding = async () => {
  if (!validateStep(0) || !validateStep(2)) return;
  if (submitting.value) return;

  submitting.value = true;
  try {
    await onboardingStore.submit({
      currentLevel: form.currentLevel,
      learningGoal: form.learningGoal || null,
      tagIds: form.tagIds,
    });
    logger.success("引导信息已保存");
    router.replace(resolveRedirectPath());
  } catch (e) {
    logger.error(e.message || "提交引导信息失败", e);
  } finally {
    submitting.value = false;
  }
};

onMounted(() => {
  refreshData();
});
</script>

<style scoped>
.onboarding-page {
  min-height: 100%;
  padding: 16px 20px 24px;
  color: #1f2a44;
}

.page-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  margin-bottom: 18px;
}

.eyebrow {
  margin: 0 0 6px;
  color: #409eff;
  font-size: 13px;
  font-weight: 700;
}

.page-title {
  margin: 0;
  font-size: 26px;
  font-weight: 700;
}

.subtitle {
  margin: 6px 0 0;
  color: #6b778c;
  font-size: 14px;
}

.wizard-shell {
  display: grid;
  grid-template-columns: 220px minmax(0, 1fr);
  gap: 18px;
  align-items: stretch;
}

.step-rail,
.wizard-main {
  background: #fff;
  border: 1px solid #e5eaf3;
  border-radius: 8px;
}

.step-rail {
  padding: 28px 18px;
}

.wizard-main {
  min-height: 520px;
  padding: 28px;
  display: flex;
  flex-direction: column;
}

.step-panel {
  flex: 1;
}

.section-head {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 22px;
}

.section-head .el-icon {
  width: 38px;
  height: 38px;
  border-radius: 8px;
  background: #eef6ff;
  color: #409eff;
  font-size: 20px;
  flex: 0 0 auto;
}

.section-head h3 {
  margin: 0;
  font-size: 22px;
}

.section-head p {
  margin: 6px 0 0;
  color: #6b778c;
  font-size: 14px;
}

.option-grid {
  display: grid;
  gap: 14px;
}

.level-grid {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.goal-grid {
  grid-template-columns: repeat(auto-fit, minmax(190px, 1fr));
}

.choice-card {
  min-height: 116px;
  padding: 18px;
  border: 1px solid #dce4ef;
  border-radius: 8px;
  background: #fff;
  color: #1f2a44;
  text-align: left;
  cursor: pointer;
  transition: border-color 0.2s ease, box-shadow 0.2s ease, background-color 0.2s ease;
}

.choice-card:hover {
  border-color: #409eff;
  box-shadow: 0 8px 20px rgba(64, 158, 255, 0.12);
}

.choice-card.selected {
  border-color: #409eff;
  background: #f4f9ff;
  box-shadow: inset 0 0 0 1px #409eff;
}

.choice-label {
  display: block;
  font-size: 18px;
  font-weight: 700;
}

.choice-meta {
  display: block;
  margin-top: 10px;
  color: #6b778c;
  font-size: 13px;
  line-height: 1.5;
}

.tag-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
  padding: 10px 12px;
  border-radius: 8px;
  background: #f7f9fc;
  color: #4f5f7a;
}

.tag-groups {
  display: grid;
  gap: 20px;
}

.group-title {
  margin-bottom: 10px;
  color: #334155;
  font-size: 14px;
  font-weight: 700;
}

.tag-list {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.tag-chip {
  min-height: 34px;
  padding: 7px 14px;
  border: 1px solid #dce4ef;
  border-radius: 999px;
  background: #fff;
  color: #4f5f7a;
  cursor: pointer;
  transition: all 0.2s ease;
}

.tag-chip:hover {
  border-color: #409eff;
  color: #409eff;
}

.tag-chip.selected {
  border-color: #409eff;
  background: #409eff;
  color: #fff;
}

.wizard-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 28px;
  padding-top: 18px;
  border-top: 1px solid #eef2f7;
}

@media (max-width: 900px) {
  .wizard-shell {
    grid-template-columns: 1fr;
  }

  .step-rail {
    padding: 18px;
  }

  .wizard-main {
    min-height: auto;
    padding: 22px;
  }

  .level-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .onboarding-page {
    padding: 12px;
  }

  .page-head {
    flex-direction: column;
  }

  .page-head .el-button {
    width: 100%;
  }

  .goal-grid {
    grid-template-columns: 1fr;
  }

  .wizard-actions {
    flex-direction: column-reverse;
  }

  .wizard-actions .el-button {
    width: 100%;
    margin-left: 0;
  }
}
</style>
