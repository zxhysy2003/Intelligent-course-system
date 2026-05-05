<template>
  <div class="course-overview">
    <h2>课程总览</h2>
    <div class="subtitle">浏览、搜索并加入你感兴趣的课程</div>

    <!-- 工具栏 -->
    <div class="toolbar">
      <el-input v-model.trim="searchQuery" placeholder="搜索课程名称、讲师或标签…" clearable @keyup.enter="searchCourses"
        class="toolbar-item input">
        <template #prefix>
          <el-icon>
            <Search />
          </el-icon>
        </template>
      </el-input>

      <el-select v-model="selectedCategory" placeholder="全部分类" clearable class="toolbar-item select">
        <el-option label="全部分类" :value="null" />
        <el-option v-for="c in categories" :key="c.id" :label="c.name" :value="c.id" />
      </el-select>

      <el-select v-model="sortBy" placeholder="排序" class="toolbar-item select">
        <el-option label="按人数" :value="0" />
        <el-option label="按最新" :value="1" />
        <el-option label="按热度" :value="2" />
      </el-select>

      <el-button type="primary" @click="searchCourses" :loading="loading" class="toolbar-item">
        搜索
      </el-button>
      <el-button type="success" @click="goToCourseRegister" class="toolbar-item">
        注册课程
      </el-button>
      <el-button
        type="danger"
        plain
        :disabled="!selectedCount"
        @click="deleteSelected"
        class="toolbar-item"
      >
        删除课程<span v-if="selectedCount">（已选{{ selectedCount }}）</span>
      </el-button>
    </div>

    <!-- 课程列表 -->
    <el-row v-if="courses.length" :gutter="12">
      <el-col v-for="course in courses" :key="course.id" :xs="24" :sm="12" :md="8">
        <el-card
          shadow="hover"
          :class="['course-card', statusClass(course.status), { selected: isSelected(course.id) }]"
          @click="toggleSelect(course.id)"
        >
          <el-image :src="course.cover" :alt="course.title" fit="cover" class="cover" />

          <div class="card-body">
            <div class="card-header">
              <span class="title">
                {{ course.title }}
              </span>
              <el-tag size="small" :type="statusTagType(course.status)">
                {{ statusText(course.status) }}
              </el-tag>
            </div>

            <div class="meta">
              <span class="difficulty" :style="{ color: difficultyMap[getDifficultyLevel(course.difficulty)]?.color }">
                {{ difficultyMap[getDifficultyLevel(course.difficulty)]?.label || "未知" }}
              </span>
              <el-rate :model-value="getDifficultyLevel(course.difficulty)" :max="3" disabled class="difficulty-rate" />
              <span class="separator">|</span>
              <span class="category">{{ course.category }}</span>
            </div>

            <div class="tags">
              <el-tag v-for="tag in course.tagList" :key="tag" size="small" effect="light">
                #{{ tag }}
              </el-tag>
            </div>

            <div class="desc">{{ course.description }}</div>

            <div class="stats">
              <div class="learners">
                <el-icon>
                  <User />
                </el-icon>
                <span>{{ course.learners.toLocaleString() }}</span>
              </div>
            </div>

            <el-progress v-if="course.enrolled" :percentage="course.progress" :stroke-width="10" />

            <div class="actions">
              <el-button type="primary" @click.stop="editCourse(course)">
                修改课程
              </el-button>
              <el-button
                :type="isOnline(course.status) ? 'warning' : 'success'"
                plain
                @click.stop="toggleStatus(course)"
              >
                {{ isOnline(course.status) ? "下线" : "上线" }}
              </el-button>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-empty v-else description="未找到符合条件的课程">
      <el-button @click="resetFilters">重置筛选</el-button>
    </el-empty>

    <!-- 分页 -->
    <div class="pagination" v-if="courses.length">
      <el-pagination 
        background 
        layout="prev, pager, next, ->, sizes" 
        :current-page="page" 
        :page-size="pageSize"
        :page-sizes="[6, 9, 12, 18]"
        :size="default"
        :total="total" @current-change="p => { page = p; searchCourses(); }"
        @size-change="size => { pageSize = size; page = 1; searchCourses(); }" />
    </div>
  </div>
</template>


<script setup>

import { ref, watch, onMounted, computed } from "vue";
import { useRouter } from "vue-router";
import { Search, User } from "@element-plus/icons-vue";
import { ElMessageBox } from "element-plus";
import { logger } from "../../utils/logger";
import { GetCategories, GetCourses, DeleteCourses, UpdateCourseStatus } from "../../api/course";

const router = useRouter();

// 查询与筛选
const searchQuery = ref("");
const selectedCategory = ref(null);
const sortBy = ref(0);
// 分页
const page = ref(1);
const pageSize = ref(9);
const total = ref(0);

const courses = ref([]);
const selectedIds = ref([]);

const selectedCount = computed(() => selectedIds.value.length);

// 搜索加载状态
const loading = ref(false);
// 分类列表
const categories = ref([{
  id: 0,
  name: "默认分类"
}]);


// 难度等级映射
const difficultyMap = {
  1: { label: "初级", color: "#67c23a" },
  2: { label: "中级", color: "#e6a23c" },
  3: { label: "高级", color: "#f56c6c" },
};

const getDifficultyLevel = (value) => {
  const level = Number(value);
  if (!Number.isFinite(level) || level <= 1) return 1;
  if (level >= 3) return 3;
  return 2;
};

// 组件挂载时获取分类列表和初始课程列表
onMounted(async () => {
  try {
    const res = await GetCategories();
    categories.value = res.data?.data || [];
  } catch (e) {
    logger.error("获取分类列表失败", e);
  }
  // 初始搜索
  searchCourses();
});


// 重置搜索选项
const resetFilters = () => {
  searchQuery.value = "";
  selectedCategory.value = null;
  sortBy.value = 0;
  searchCourses();
};


// 搜索课程
const searchCourses = async () => {
  loading.value = true;
  try {
    const res = await GetCourses({
      page: page.value,
      pageSize: pageSize.value,
      keyword: searchQuery.value,
      categoryId: selectedCategory.value,
      sortBy: sortBy.value,
    });

    logger.debug("搜索课程结果", res);

    courses.value = res.data.data?.records || [];
    total.value = res.data.data?.total || 0;
    logger.success("搜索成功");
  } catch (e) {
    logger.error("搜索失败", e);
  } finally {
    loading.value = false;
  }
};

const editCourse = (course) => {
  router.push({
    name: "CourseEdit",
    params: { id: course.id }
  });
};

const toggleSelect = (courseId) => {
  const current = new Set(selectedIds.value);
  if (current.has(courseId)) {
    current.delete(courseId);
  } else {
    current.add(courseId);
  }
  selectedIds.value = Array.from(current);
};

const isSelected = (courseId) => selectedIds.value.includes(courseId);

const deleteSelected = async () => {
  if (!selectedIds.value.length) return;
  try {
    await ElMessageBox.confirm(
      `确认删除已选中的 ${selectedIds.value.length} 门课程吗？删除后无法恢复。`,
      "再次确认删除",
      {
        confirmButtonText: "确认删除",
        cancelButtonText: "取消",
        type: "warning",
        distinguishCancelAndClose: true,
        closeOnClickModal: false,
      }
    );
  } catch {
    return;
  }

  try {
    const res = await DeleteCourses(selectedIds.value);
    if (res.data.code !== 200) {
      logger.error(res.data.msg || "删除课程失败", res.data);
      return;
    }
    const selectedSet = new Set(selectedIds.value);
    courses.value = courses.value.filter(course => !selectedSet.has(course.id));
    selectedIds.value = [];
    logger.success("删除课程成功");
  } catch (e) {
    logger.error("删除课程失败", e);
  }
};

const goToCourseRegister = () => {
  router.push({
    name: "CourseRegister"
  });
};

const statusText = (status) => {
  const value = Number(status);
  if (value === 1) return "已上线";
  if (value === 2) return "已下线";
  return "草稿";
};

const statusTagType = (status) => {
  const value = Number(status);
  if (value === 1) return "success";
  if (value === 2) return "warning";
  return "info";
};

const statusClass = (status) => {
  const value = Number(status);
  if (value === 1) return "status-online";
  if (value === 2) return "status-offline";
  return "status-draft";
};

const isOnline = (status) => Number(status) === 1;

const toggleStatus = async (course) => {
  const targetStatus = isOnline(course.status) ? 2 : 1;
  try {
    const res = await UpdateCourseStatus(course.id, targetStatus);
    if (res?.data?.code !== 200) {
      logger.error(res?.data?.msg || "更新课程状态失败", res?.data);
      return;
    }
    course.status = targetStatus;
    logger.success(targetStatus === 1 ? "课程已上线" : "课程已下线");
  } catch (e) {
    logger.error("更新课程状态失败", e);
  }
};

// 筛选项变化自动搜索（showEnrolledOnly不触发搜索，只做前端过滤）
watch(
  [selectedCategory, sortBy],
  searchCourses
);


</script>

<style scoped>
.course-overview {
  max-width: 1100px;
  margin: 0 auto;
  padding: 16px;
}

.subtitle {
  color: #666;
  margin-top: 4px;
  font-size: 14px;
  margin-bottom: 12px;
}

.toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  margin: 12px 0 16px;
  position: sticky;
  top: 0;
  z-index: 10;
  background: #fff;
  padding: 8px 0;
  box-shadow: 0 8px 16px -12px rgba(0, 0, 0, 0.15);
}

.toolbar .input {
  flex: 1 1 320px;
}

.toolbar .select {
  width: 160px;
}

.course-card {
  margin-bottom: 12px;
}

.course-card.selected {
  border-color: #409eff;
  box-shadow: 0 6px 16px rgba(64, 158, 255, 0.16);
}

.course-card.status-draft {
  border-left: 4px solid #909399;
  background: linear-gradient(180deg, #ffffff 0%, #fafafa 100%);
}

.course-card.status-online {
  border-left: 4px solid #67c23a;
  background: linear-gradient(180deg, #ffffff 0%, #f6fff6 100%);
}

.course-card.status-offline {
  border-left: 4px solid #e6a23c;
  background: linear-gradient(180deg, #ffffff 0%, #fffaf2 100%);
}

.cover {
  width: 100%;
  aspect-ratio: 16/9;
  object-fit: cover;
  background: #f6f6f6;
  border-radius: 6px;
}

.card-body {
  padding: 10px 0 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.title {
  font-size: 16px;
  cursor: pointer;
}

.meta {
  color: #666;
  font-size: 13px;
  display: flex;
  align-items: center;
  gap: 8px;
}

.difficulty-rate {
  font-size: 14px;
}

.difficulty {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-weight: 500;
}

.separator {
  color: #ddd;
}

.category {
  color: #666;
}

.tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.desc {
  color: #444;
  font-size: 14px;
  line-height: 1.6;
  display: -webkit-box;
  line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.stats {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.learners {
  display: flex;
  align-items: center;
  gap: 6px;
  color: #555;
  font-size: 13px;
}

.actions {
  display: flex;
  gap: 8px;
  margin-top: 4px;
}

.pagination {
  display: flex;
  justify-content: center;
  margin-top: 16px;
}
</style>
