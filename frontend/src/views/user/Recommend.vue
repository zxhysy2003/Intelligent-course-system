<template>
  <div class="recommend-page" v-loading="loading">
    <div class="header-section">
      <div class="title-group">
        <h2 class="main-title">课程推荐</h2>
        <p class="sub-title">基于协同过滤算法与知识图谱的深度定制方案</p>
      </div>
      <el-button type="primary" :icon="Refresh" @click="fetchRecommendation" :loading="loading" plain>
        刷新推荐
      </el-button>
    </div>

    <div class="summary-grid">
      <div class="info-card">
        <span class="label">目标用户</span>
        <span class="value">{{ currentUserIdText }}</span>
      </div>
      <div class="info-card">
        <span class="label">为您精选</span>
        <span class="value">{{ items.length }} <small>门课程</small></span>
      </div>
      <div class="info-card">
        <span class="label">更新于</span>
        <span class="value">{{ lastUpdatedText || "-" }}</span>
      </div>
    </div>

    <el-row v-if="items.length" :gutter="20" class="cards-container">
      <el-col v-for="item in items" :key="item.courseId" :xs="24" :lg="12">
        <el-card shadow="always" class="course-card">
          <template #header>
            <div class="card-header">
              <div class="course-info">
                <el-icon class="course-icon"><Reading /></el-icon>
                <span class="course-title" @click="openCourse(item)">
                  {{ item.title || `课程 #${item.courseId}` }}
                </span>
                <el-tag v-if="item.difficulty" size="small" type="info" effect="plain">
                  {{ difficultyText(item.difficulty) }}
                </el-tag>
              </div>
              <el-tag :type="scoreTagType(item.recommendScore)" effect="dark">
                {{ formatRecommendScore(item.recommendScore) }}分 {{ recommendTagText(item) }}
              </el-tag>
            </div>
          </template>

          <div v-if="item.reason" class="reason-text">
            {{ item.reason }}
          </div>

          <div class="metric-section">
            <div class="metric-item">
              <span class="metric-label">学习准备度</span>
              <el-progress 
                :percentage="toPercent(item.readiness)" 
                :stroke-width="12" 
                :color="progressColors"
              />
            </div>
          </div>

          <div class="content-section" v-if="item.knowledgePoints.length">
            <h4 class="section-label">涵盖知识点</h4>
            <div class="tag-cloud">
              <el-tag 
                v-for="kp in item.knowledgePoints" 
                :key="kp.id" 
                class="kp-tag" 
                round
              >
                {{ kp.name }} 
                <span class="diff-badge">{{ difficultyText(kp.difficulty) }}</span>
              </el-tag>
            </div>
          </div>

          <div class="content-section" v-if="item.missingPrerequisitesMastery?.length">
            <h4 class="section-label">薄弱前置项 (需补齐)</h4>
            <el-table :data="item.missingPrerequisitesMastery" size="small" border class="mini-table">
              <el-table-column prop="name" label="知识点" />
              <el-table-column label="差距" align="center" width="120">
                <template #default="{ row }">
                  <span class="gap-text">{{ toPercent(row.have) }}% → {{ toPercent(row.need) }}%</span>
                </template>
              </el-table-column>
            </el-table>
          </div>

          <div class="path-section" v-if="item.learningPaths.length">
            <div class="path-header" @click="togglePath(item.courseId)">
              <span class="section-label">建议学习路径</span>
              <el-link :underline="false" type="primary">
                {{ isPathExpanded(item.courseId) ? '隐藏' : '查看详情' }}
                <el-icon><ArrowDown v-if="!isPathExpanded(item.courseId)" /><ArrowUp v-else /></el-icon>
              </el-link>
            </div>
            
            <el-collapse-transition>
              <div v-show="isPathExpanded(item.courseId)" class="path-body">
                <div v-for="(path, index) in item.learningPaths" :key="index" class="path-item">
                  <div class="path-tag">路径 {{ index + 1 }}</div>
                  <el-steps direction="vertical" :active="path.length" space="60px">
                    <el-step v-for="node in path" :key="node.id">
                      <template #title>
                        <span class="node-link" @click="openKnowledgePointCourses(node)">{{ node.name }}</span>
                      </template>
                      <template #description>
                        难度: {{ difficultyText(node.difficulty) }}
                      </template>
                    </el-step>
                  </el-steps>
                </div>
              </div>
            </el-collapse-transition>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-empty v-else description="正在分析您的学习画像..." />

    <el-dialog v-model="courseSelectorVisible" title="相关推荐课程" width="480px">
      <div v-loading="loadingCoursesByKp">
        <div v-if="relatedCourses.length" class="course-list">
          <div 
            v-for="course in relatedCourses" 
            :key="course.id" 
            class="course-option"
            @click="goToCourseDetail(course.id)"
          >
            <div class="option-main">
              <span class="option-title">{{ course.title }}</span>
              <el-tag size="small" type="info">{{ difficultyText(course.difficulty) }}</el-tag>
            </div>
            <el-icon><ArrowRight /></el-icon>
          </div>
        </div>
        <el-empty v-else size="small" description="暂无关联课程" />
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, ref, onMounted } from "vue";
import { useRouter } from "vue-router";
import { useUserStore } from "@/store/user";
import { GetHybridRecommend } from "@/api/recommend";
import { Reading, Refresh, ArrowDown, ArrowUp, ArrowRight } from '@element-plus/icons-vue';
import { GetCourseByKp } from "@/api/course";
import { logger } from "@/utils/logger";

const router = useRouter();
const userStore = useUserStore();

const currentUserId = computed(() => userStore.userInfo?.userId);
const currentUserIdText = computed(() => {
  const userId = Number(currentUserId.value);
  return Number.isFinite(userId) && userId > 0 ? String(userId) : "未登录";
});

const recommendation = ref({
  items: [],
});

const items = computed(() => recommendation.value.items || []);

const lastUpdatedText = ref("");

const formatDate = (date) => {
  const yyyy = date.getFullYear();
  const mm = String(date.getMonth() + 1).padStart(2, "0");
  const dd = String(date.getDate()).padStart(2, "0");
  return `${yyyy}-${mm}-${dd}`;
};

const loading = ref(false);

const toFiniteNumber = (value, fallback = 0) => {
  const number = Number(value);
  return Number.isFinite(number) ? number : fallback;
};

const normalizeArray = (value) => Array.isArray(value) ? value : [];

const normalizeRecommendItem = (item) => ({
  courseId: toFiniteNumber(item?.courseId, 0),
  title: item?.title ?? "",
  difficulty: toFiniteNumber(item?.difficulty, 0),
  recommendScore: toFiniteNumber(item?.recommendScore ?? item?.finalScore, 0),
  reason: item?.reason ?? "",
  readiness: toFiniteNumber(item?.readiness, 0),
  isNewCourse: Boolean(item?.isNewCourse),
  knowledgePoints: normalizeArray(item?.knowledgePoints),
  missingPrerequisitesMastery: normalizeArray(item?.missingPrerequisitesMastery),
  learningPaths: normalizeArray(item?.learningPaths).filter(Array.isArray),
});

const normalizePayload = (payload) => {
  if (!payload || typeof payload !== "object") {
    return { items: [] };
  }

  return {
    items: normalizeArray(payload.items)
      .map(normalizeRecommendItem)
      .filter((item) => item.courseId > 0),
  };
};

const fetchRecommendation = async () => {
  loading.value = true;
  try {
    const res = await GetHybridRecommend();
    const payload = res?.data?.data ?? res?.data ?? {};
    recommendation.value = normalizePayload(payload);
    lastUpdatedText.value = formatDate(new Date());
  } catch (e) {
    logger.error("获取推荐失败", e);
  } finally {
    loading.value = false;
  }
};

onMounted(() => {
  fetchRecommendation();
});

const toPercent = (value) => Math.round(Number(value || 0) * 100);

const formatRecommendScore = (score) => Math.round(toFiniteNumber(score, 0));

const scoreTagType = (score) => formatRecommendScore(score) >= 85 ? "danger" : "success";

const recommendTagText = (item) => item.isNewCourse ? "新课推荐" : "强力推荐";

const difficultyMap = {
  1: "初级",
  2: "中级",
  3: "高级"
};

const difficultyText = (level) => difficultyMap[level] || "未知";

const expandedMap = ref({});

const isPathExpanded = (courseId) => !!expandedMap.value[courseId];

const togglePath = (courseId) => {
  expandedMap.value[courseId] = !expandedMap.value[courseId];
};

// 进度条颜色渐变
const progressColors = [
  { color: '#f56c6c', percentage: 20 },
  { color: '#e6a23c', percentage: 40 },
  { color: '#5cb87a', percentage: 60 },
  { color: '#1989fa', percentage: 80 },
  { color: '#6f7ad3', percentage: 100 },
];

const openCourse = (item) => {
  router.push({
    name: "CourseDetail",
    params: { id: item.courseId },
  });
};

const relatedCourses = ref([]);
const courseSelectorVisible = ref(false);
const loadingCoursesByKp = ref(false);

const normalizeCourseListFromResponse = (res) => {
  const payload = res?.data?.data ?? res?.data;
  if (!Array.isArray(payload)) return [];
  return payload
    .map((item) => ({
      id: Number(item?.id),
      title: item?.title ?? "",
      difficulty: Number(item?.difficulty ?? 0),
    }))
    .filter((item) => Number.isFinite(item.id) && item.id > 0);
};

const openKnowledgePointCourses = async (node) => {
  const kpId = Number(node?.id);
  if (!Number.isFinite(kpId)) {
    logger.warn("该知识点缺少 id，无法查询关联课程");
    return;
  }
  if (loadingCoursesByKp.value) return;

  loadingCoursesByKp.value = true;
  try {
    const res = await GetCourseByKp(kpId);
    relatedCourses.value = normalizeCourseListFromResponse(res);
    courseSelectorVisible.value = true;
  } catch (e) {
    logger.error("获取知识点关联课程失败", e);
  } finally {
    loadingCoursesByKp.value = false;
  }
};

const goToCourseDetail = (courseId) => {
  courseSelectorVisible.value = false;
  router.push({
    name: "CourseDetail",
    params: { id: courseId },
  });
};
</script>

<style scoped>
.recommend-page {
  padding: 24px;
  background-color: #f8fafc;
  min-height: 100vh;
}

.header-section {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
}

.main-title {
  margin: 0;
  font-size: 26px;
  color: #1e293b;
  font-weight: 700;
}

.sub-title {
  margin: 4px 0 0;
  color: #64748b;
  font-size: 14px;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 16px;
  margin-bottom: 24px;
}

.info-card {
  background: white;
  padding: 16px;
  border-radius: 12px;
  border: 1px solid #e2e8f0;
  display: flex;
  flex-direction: column;
}

.info-card .label {
  font-size: 12px;
  color: #94a3b8;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.info-card .value {
  font-size: 20px;
  font-weight: 600;
  color: #0f172a;
  margin-top: 4px;
}

.course-card {
  border-radius: 16px;
  border: none;
  transition: transform 0.3s ease;
}

.course-card:hover {
  transform: translateY(-4px);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
}

.course-info {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  min-width: 0;
}

.course-icon {
  font-size: 20px;
  color: #409eff;
}

.course-title {
  font-weight: 600;
  font-size: 17px;
  cursor: pointer;
  color: #1e293b;
  overflow-wrap: anywhere;
}

.course-title:hover {
  color: #409eff;
}

.reason-text {
  margin-bottom: 18px;
  padding: 10px 12px;
  border-radius: 8px;
  background: #f8fafc;
  color: #475569;
  font-size: 13px;
  line-height: 1.6;
}

.metric-section {
  margin-bottom: 20px;
}

.metric-label {
  display: block;
  font-size: 13px;
  color: #64748b;
  margin-bottom: 8px;
}

.content-section {
  margin-top: 20px;
}

.section-label {
  font-size: 14px;
  font-weight: 600;
  color: #334155;
  margin-bottom: 12px;
}

.tag-cloud {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.kp-tag {
  background: #f1f5f9;
  border: none;
  color: #475569;
}

.diff-badge {
  opacity: 0.6;
  font-size: 11px;
  margin-left: 4px;
}

.mini-table {
  border-radius: 8px;
  overflow: hidden;
}

.path-section {
  margin-top: 24px;
  background: #fcfcfd;
  border-radius: 12px;
  padding: 12px;
}

.path-header {
  display: flex;
  justify-content: space-between;
  cursor: pointer;
}

.path-body {
  padding-top: 16px;
}

.path-tag {
  font-size: 12px;
  font-weight: 600;
  color: #409eff;
  background: #ecf5ff;
  display: inline-block;
  padding: 2px 8px;
  border-radius: 4px;
  margin-bottom: 12px;
}

.node-link {
  color: #1e293b;
  font-weight: 500;
  cursor: pointer;
}

.node-link:hover {
  color: #409eff;
  text-decoration: underline;
}

/* 弹窗课程项美化 */
.course-option {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  border-radius: 8px;
  cursor: pointer;
  margin-bottom: 8px;
  border: 1px solid #f1f5f9;
  transition: all 0.2s;
}

.course-option:hover {
  background: #f0f7ff;
  border-color: #d1e9ff;
}

.option-main {
  display: flex;
  align-items: center;
  gap: 12px;
}

.option-title {
  font-weight: 500;
  color: #334155;
}
</style>
