<template>
  <div class="course-overview">
    <section class="page-head">
      <div class="title-group">
        <h2 class="page-title">课程总览</h2>
        <p class="subtitle">发现适合当前阶段的课程内容</p>
      </div>

      <div class="summary-strip">
        <div class="summary-item">
          <el-icon><Collection /></el-icon>
          <div>
            <span class="summary-value">{{ total }}</span>
            <span class="summary-label">全部课程</span>
          </div>
        </div>
        <div class="summary-item">
          <el-icon><CircleCheck /></el-icon>
          <div>
            <span class="summary-value">{{ enrolledCount }}</span>
            <span class="summary-label">已加入</span>
          </div>
        </div>
        <div class="summary-item">
          <el-icon><Tickets /></el-icon>
          <div>
            <span class="summary-value">{{ filteredCourses.length }}</span>
            <span class="summary-label">当前显示</span>
          </div>
        </div>
      </div>
    </section>

    <section class="toolbar-panel">
      <el-input
        v-model.trim="searchQuery"
        placeholder="搜索课程名称、讲师或标签"
        clearable
        class="search-input"
        @keyup.enter="applyFilters"
      >
        <template #prefix>
          <el-icon><Search /></el-icon>
        </template>
      </el-input>

      <el-select v-model="selectedCategory" placeholder="全部分类" clearable class="filter-select" @change="applyFilters">
        <el-option label="全部分类" :value="null" />
        <el-option v-for="c in categories" :key="c.id" :label="c.name" :value="c.id" />
      </el-select>

      <el-select v-model="sortBy" placeholder="排序" class="filter-select" @change="applyFilters">
        <el-option label="按人数" :value="0" />
        <el-option label="按最新" :value="1" />
        <el-option label="按热度" :value="2" />
        <el-option label="按进度" :value="3" />
      </el-select>

      <div class="enrolled-switch">
        <span>已加入</span>
        <el-switch v-model="showEnrolledOnly" />
      </div>

      <div class="toolbar-actions">
        <el-button :icon="Refresh" @click="resetFilters">
          重置
        </el-button>
        <el-button type="primary" :icon="Search" @click="applyFilters" :loading="loading">
          搜索
        </el-button>
      </div>
    </section>

    <el-row v-if="filteredCourses.length" :gutter="20" class="course-grid">
      <el-col v-for="course in filteredCourses" :key="course.id" :xs="24" :sm="12" :lg="8">
        <el-card shadow="hover" class="course-card" :body-style="{ padding: '0' }">
          <div class="cover-wrap" @click="openCourse(course)">
            <el-image :src="course.cover" :alt="course.title" fit="cover" class="cover">
              <template #error>
                <div class="cover-fallback">
                  <el-icon><Picture /></el-icon>
                </div>
              </template>
            </el-image>
            <el-tag v-if="course.enrolled" class="enrolled-badge" type="success" effect="dark">
              已加入
            </el-tag>
          </div>

          <div class="card-body">
            <div class="card-header">
              <button class="title-button" type="button" @click="openCourse(course)">
                {{ course.title }}
              </button>
              <el-tag size="small" :type="difficultyTagType(course.difficulty)" effect="light">
                {{ difficultyText(course.difficulty) }}
              </el-tag>
            </div>

            <div class="meta">
              <span class="meta-item">
                <el-icon><Grid /></el-icon>
                {{ course.category }}
              </span>
              <span class="meta-dot" />
              <span class="meta-item">
                <el-icon><User /></el-icon>
                {{ formatLearners(course.learners) }} 人学习
              </span>
            </div>

            <div v-if="course.tagList.length" class="tags">
              <el-tag v-for="tag in course.tagList.slice(0, 4)" :key="tag" size="small" effect="plain">
                {{ tag }}
              </el-tag>
            </div>

            <p class="desc">{{ course.description || "暂无课程简介" }}</p>

            <div v-if="course.enrolled" class="progress-wrap">
              <div class="progress-label">
                <span>学习进度</span>
                <strong>{{ course.progress }}%</strong>
              </div>
              <el-progress :percentage="course.progress" :stroke-width="9" :show-text="false" />
            </div>

            <div class="actions">
              <el-button type="primary" :icon="VideoPlay" @click="openCourse(course)">
                {{ course.enrolled ? "继续学习" : "查看详情" }}
              </el-button>
              <el-button
                v-if="!course.enrolled"
                :icon="Plus"
                @click="enroll(course)"
                :loading="!!enrolling[course.id]"
              >
                {{ enrolling[course.id] ? "加入中" : "加入课程" }}
              </el-button>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-empty v-else description="未找到符合条件的课程" class="empty-state">
      <el-button :icon="Refresh" @click="resetFilters">重置筛选</el-button>
    </el-empty>

    <div class="pagination" v-if="filteredCourses.length">
      <el-pagination
        background
        layout="prev, pager, next, ->, sizes"
        :current-page="page"
        :page-size="pageSize"
        :page-sizes="[6, 9, 12, 18]"
        :total="total"
        @current-change="handlePageChange"
        @size-change="handleSizeChange"
      />
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from "vue";
import { useRouter } from "vue-router";
import {
  CircleCheck,
  Collection,
  Grid,
  Picture,
  Plus,
  Refresh,
  Search,
  Tickets,
  User,
  VideoPlay
} from "@element-plus/icons-vue";
import { logger } from "@/utils/logger";
import { GetCategories, GetCourses, UserAttendCourse } from "@/api/course";

const router = useRouter();

const searchQuery = ref("");
const selectedCategory = ref(null);
const sortBy = ref(0);
const showEnrolledOnly = ref(false);

const page = ref(1);
const pageSize = ref(9);
const total = ref(0);

const courses = ref([]);
const enrolling = ref({});
const loading = ref(false);
const categories = ref([{ id: 0, name: "默认分类" }]);

const difficultyMap = {
  1: { label: "初级", color: "#67c23a", type: "success" },
  2: { label: "中级", color: "#e6a23c", type: "warning" },
  3: { label: "高级", color: "#f56c6c", type: "danger" },
};

const getDifficultyLevel = (value) => {
  const level = Number(value);
  if (!Number.isFinite(level) || level <= 1) return 1;
  if (level >= 3) return 3;
  return 2;
};

const difficultyText = (value) => difficultyMap[getDifficultyLevel(value)]?.label || "未知";

const difficultyTagType = (value) => difficultyMap[getDifficultyLevel(value)]?.type || "info";

const clampPercent = (value) => {
  const number = Math.round(Number(value) || 0);
  return Math.min(100, Math.max(0, number));
};

const normalizeCourse = (course) => ({
  ...course,
  id: Number(course?.id),
  title: course?.title || `课程 #${course?.id ?? ""}`,
  cover: course?.cover || course?.coverUrl || "",
  category: course?.category || course?.categoryName || "未分类",
  description: course?.description || "",
  difficulty: getDifficultyLevel(course?.difficulty),
  learners: Number(course?.learners || 0),
  enrolled: Boolean(course?.enrolled),
  progress: clampPercent(course?.progress),
  tagList: Array.isArray(course?.tagList) ? course.tagList : [],
});

const filteredCourses = computed(() => {
  if (!showEnrolledOnly.value) {
    return courses.value;
  }
  return courses.value.filter((course) => course.enrolled);
});

const enrolledCount = computed(() => courses.value.filter((course) => course.enrolled).length);

const formatLearners = (value) => Number(value || 0).toLocaleString();

const fetchCategories = async () => {
  try {
    const res = await GetCategories();
    categories.value = Array.isArray(res.data?.data) ? res.data.data : [];
  } catch (e) {
    logger.error("获取分类列表失败", e);
  }
};

const searchCourses = async () => {
  loading.value = true;
  try {
    const res = await GetCourses({
      page: page.value,
      pageSize: pageSize.value,
      keyword: searchQuery.value,
      categoryId: selectedCategory.value,
      sortBy: sortBy.value,
      status: 1,
    });

    const payload = res.data?.data || {};
    courses.value = Array.isArray(payload.records)
      ? payload.records.map(normalizeCourse).filter((course) => Number.isFinite(course.id))
      : [];
    total.value = Number(payload.total || 0);
    logger.success("搜索成功");
  } catch (e) {
    logger.error("搜索失败", e);
  } finally {
    loading.value = false;
  }
};

const applyFilters = () => {
  page.value = 1;
  searchCourses();
};

const resetFilters = () => {
  searchQuery.value = "";
  selectedCategory.value = null;
  sortBy.value = 0;
  showEnrolledOnly.value = false;
  applyFilters();
};

const handlePageChange = (nextPage) => {
  page.value = nextPage;
  searchCourses();
};

const handleSizeChange = (size) => {
  pageSize.value = size;
  page.value = 1;
  searchCourses();
};

const openCourse = (course) => {
  router.push({
    name: "CourseDetail",
    params: { id: course.id },
  });
};

const enroll = async (course) => {
  if (enrolling.value[course.id]) return;
  enrolling.value[course.id] = true;
  try {
    logger.debug("加入课程", course.id);
    const res = await UserAttendCourse(course.id);
    if (res.data.code !== 200) {
      logger.error(res.data.msg || "加入课程失败", res.data);
      return;
    }
    logger.success(`已成功加入课程《${course.title}》`);
    course.enrolled = true;
    course.progress = 0;
  } finally {
    enrolling.value[course.id] = false;
  }
};

onMounted(async () => {
  await fetchCategories();
  searchCourses();
});

</script>

<style scoped>
.course-overview {
  min-height: 100vh;
  padding: 24px;
  background: #f8fafc;
}

.page-head {
  max-width: 1180px;
  margin: 0 auto 18px;
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 18px;
}

.page-title {
  margin: 0;
  color: #111827;
  font-size: 26px;
  font-weight: 700;
}

.subtitle {
  margin: 6px 0 0;
  color: #64748b;
  font-size: 14px;
}

.summary-strip {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  justify-content: flex-end;
}

.summary-item {
  min-width: 116px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #ffffff;
  color: #409eff;
}

.summary-value,
.summary-label {
  display: block;
}

.summary-value {
  color: #111827;
  font-size: 18px;
  font-weight: 700;
  line-height: 1;
}

.summary-label {
  margin-top: 3px;
  color: #64748b;
  font-size: 12px;
}

.toolbar-panel {
  max-width: 1180px;
  margin: 0 auto 20px;
  padding: 14px;
  display: grid;
  grid-template-columns: minmax(260px, 1fr) 160px 160px auto auto;
  gap: 10px;
  align-items: center;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #ffffff;
}

.search-input,
.filter-select {
  width: 100%;
}

.enrolled-switch {
  height: 32px;
  padding: 0 10px;
  display: inline-flex;
  align-items: center;
  gap: 10px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  color: #475569;
  font-size: 13px;
  white-space: nowrap;
}

.toolbar-actions {
  display: flex;
  gap: 8px;
  justify-content: flex-end;
}

.course-grid {
  max-width: 1180px;
  margin: 0 auto;
}

.course-card {
  height: 100%;
  margin-bottom: 20px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  overflow: hidden;
  transition: transform 0.2s ease, box-shadow 0.2s ease, border-color 0.2s ease;
}

.course-card:hover {
  transform: translateY(-3px);
  border-color: #bfdbfe;
  box-shadow: 0 14px 30px rgba(15, 23, 42, 0.08);
}

.cover-wrap {
  position: relative;
  cursor: pointer;
}

.cover {
  display: block;
  width: 100%;
  aspect-ratio: 16 / 9;
  background: #eef2f7;
}

.cover-fallback {
  width: 100%;
  aspect-ratio: 16 / 9;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #eef2f7;
  color: #94a3b8;
  font-size: 26px;
}

.enrolled-badge {
  position: absolute;
  right: 10px;
  top: 10px;
  border: none;
}

.card-body {
  min-height: 238px;
  padding: 14px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.card-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
}

.title-button {
  min-width: 0;
  padding: 0;
  border: 0;
  background: transparent;
  color: #111827;
  font-size: 16px;
  font-weight: 700;
  line-height: 1.45;
  text-align: left;
  cursor: pointer;
  overflow-wrap: anywhere;
}

.title-button:hover {
  color: #2563eb;
}

.meta {
  min-height: 20px;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 7px;
  color: #64748b;
  font-size: 13px;
}

.meta-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.meta-dot {
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background: #cbd5e1;
}

.tags {
  min-height: 24px;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.desc {
  min-height: 44px;
  margin: 0;
  color: #475569;
  font-size: 14px;
  line-height: 1.6;
  display: -webkit-box;
  line-clamp: 2;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.progress-wrap {
  margin-top: auto;
}

.progress-label {
  margin-bottom: 6px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: #64748b;
  font-size: 12px;
}

.progress-label strong {
  color: #111827;
}

.actions {
  margin-top: auto;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.actions .el-button {
  width: 100%;
  margin-left: 0;
}

.actions .el-button:only-child {
  grid-column: 1 / -1;
}

.empty-state {
  max-width: 1180px;
  margin: 40px auto 0;
  padding: 44px 0;
  border: 1px dashed #cbd5e1;
  border-radius: 8px;
  background: #ffffff;
}

.pagination {
  max-width: 1180px;
  margin: 6px auto 0;
  padding: 14px 0 4px;
  display: flex;
  justify-content: center;
}

@media (max-width: 900px) {
  .page-head {
    align-items: flex-start;
    flex-direction: column;
  }

  .summary-strip {
    width: 100%;
    justify-content: flex-start;
  }

  .toolbar-panel {
    grid-template-columns: 1fr 1fr;
  }

  .search-input,
  .toolbar-actions {
    grid-column: 1 / -1;
  }

  .toolbar-actions {
    justify-content: stretch;
  }

  .toolbar-actions .el-button {
    flex: 1;
  }
}

@media (max-width: 560px) {
  .course-overview {
    padding: 16px;
  }

  .toolbar-panel {
    grid-template-columns: 1fr;
  }

  .summary-item {
    flex: 1 1 100%;
  }

  .enrolled-switch,
  .toolbar-actions {
    width: 100%;
  }
}
</style>
