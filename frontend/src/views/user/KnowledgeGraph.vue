<template>
  <div class="knowledge-graph-page" v-loading="loading">
    <div class="header">
      <div class="title">知识图谱</div>
      <div class="subtitle">展示课程知识点关系与掌握度</div>
    </div>

    <el-card class="search-card" shadow="hover">
      <div class="search-row">
        <el-input
          v-model.trim="courseKeyword"
          placeholder="请输入课程ID"
          clearable
          class="search-input"
          @keyup.enter="handleSearch"
        />
        <el-button type="primary" @click="handleSearch">搜索</el-button>
      </div>
    </el-card>

    <el-card class="summary-card" shadow="hover">
      <div class="summary-row">
        <div class="summary-item">
          <div class="label">课程 ID</div>
          <div class="value">{{ graphData.courseId ?? "-" }}</div>
        </div>
        <div class="summary-item">
          <div class="label">课程名称</div>
          <div class="value">{{ graphData.title ?? "-" }}</div>
        </div>
        <div class="summary-item">
          <div class="label">用户 ID</div>
          <div class="value">{{ graphData.userId ?? "-" }}</div>
        </div>
        <div class="summary-item">
          <div class="label">节点数</div>
          <div class="value">{{ graphData.stats?.nodeCount ?? "-" }}</div>
        </div>
        <div class="summary-item">
          <div class="label">边数</div>
          <div class="value">{{ graphData.stats?.edgeCount ?? "-" }}</div>
        </div>
        <div class="summary-item">
          <div class="label">平均掌握度</div>
          <div class="value">{{ formatMastery(graphData.stats?.avgMastery) }}</div>
        </div>
      </div>
    </el-card>

    <el-card class="chart-card" shadow="hover">
      <el-empty v-if="chartHint" :description="chartHint" />
      <div v-show="!chartHint" ref="chartRef" class="chart"></div>
    </el-card>

    <el-dialog
      v-model="courseSelectorVisible"
      title="请选择课程"
      width="520px"
      destroy-on-close
    >
      <el-empty v-if="!relatedCourses.length" description="该知识点暂无关联课程" />
      <div v-else class="course-option-list">
        <el-button
          v-for="course in relatedCourses"
          :key="course.id"
          text
          class="course-option-btn"
          @click="goToCourseDetail(course.id)"
        >
          {{ course.title || `课程 #${course.id}` }}
        </el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, nextTick } from "vue";
import { useRoute, useRouter } from "vue-router";
import * as echarts from "echarts";
import { GetKnowledgeGraph } from "@/api/analysis";
import { GetCourseByKp } from "@/api/course";
import { logger } from "@/utils/logger";

const router = useRouter();
const route = useRoute();
const chartRef = ref(null);
const chartInstance = ref(null);
const loading = ref(false);
const jumping = ref(false);
const courseKeyword = ref("");
const chartHint = ref("请输入课程关键词");
const relatedCourses = ref([]);
const courseSelectorVisible = ref(false);

const graphData = ref({
  courseId: null,
  title: null,
  userId: null,
  nodes: [],
  links: [],
  stats: null,
});

const difficultyColor = {
  1: "#67c23a",
  2: "#409eff",
  3: "#e6a23c",
  4: "#f56c6c",
  5: "#ad3c3c",
};
const outOfCourseColor = "#bfc4cd";

// 将 0~1 掌握度转换为百分比字符串
const formatMastery = (value) => {
  if (value === null || value === undefined) return "-";
  return `${Math.round(Number(value) * 100)}%`;
};

const normalizeCourseListFromResponse = (res) => {
  const payload = res?.data?.data ?? res?.data;
  if (!Array.isArray(payload)) {
    return [];
  }
  return payload
    .map((item) => ({
      id: Number(item?.id),
      title: item?.title ?? "",
    }))
    .filter((item) => Number.isFinite(item.id) && item.id > 0);
};

const goToCourseDetail = (courseId) => {
  courseSelectorVisible.value = false;
  router.push({
    name: "CourseDetail",
    params: { id: courseId },
  });
};

const handleNodeClick = async (params) => {
  if (params?.dataType !== "node") return;
  const kpId = Number(params?.data?.kpId);
  if (!Number.isFinite(kpId)) {
    logger.warn("该节点缺少 kpId，无法跳转课程");
    return;
  }
  if (jumping.value) return;

  jumping.value = true;
  try {
    const res = await GetCourseByKp(kpId);
    const courses = normalizeCourseListFromResponse(res);
    if (!courses.length) {
      relatedCourses.value = [];
      courseSelectorVisible.value = true;
      return;
    }
    relatedCourses.value = courses;
    courseSelectorVisible.value = true;
  } catch (e) {
    logger.error("根据知识点获取课程失败", e);
  } finally {
    jumping.value = false;
  }
};

const clearChart = () => {
  chartInstance.value?.clear();
};

// 将后端图结构映射为 ECharts graph 配置并渲染
const buildChart = (data) => {
  if (!chartRef.value) return;
  if (!chartInstance.value) {
    chartInstance.value = echarts.init(chartRef.value);
  }

  // 节点：课程内按难度高亮，课程外统一灰色
  const nodes = (data.nodes || []).map((node) => ({
    kpId: node.kpId,
    id: node.id,
    name: node.name,
    value: node.mastery,
    symbolSize: 18 + Number(node.mastery || 0) * 18,
    itemStyle: {
      color: node.inCourse ? (difficultyColor[node.difficulty] || "#409eff") : outOfCourseColor,
      opacity: node.inCourse ? 1 : 0.75,
      borderColor: node.inCourse ? "#1f2a44" : "#d5d9e0",
      borderWidth: node.inCourse ? 1.5 : 1,
    },
    tooltip: {
      formatter: () =>
        `${node.name}<br/>难度：${node.difficulty}<br/>掌握度：${formatMastery(node.mastery)}<br/>归属：${
          node.inCourse ? "课程内" : "课程外"
        }`,
    },
  }));

  // 边：目前按依赖关系统一样式，可按 type 继续细分
  const links = (data.links || []).map((link) => ({
    source: link.source,
    target: link.target,
    lineStyle: {
      color: link.type === "PRE_REQUIRES" ? "#909399" : "#c0c4cc",
      width: 1.2,
      curveness: 0.2,
    },
    label: {
      show: false,
    },
  }));

  chartInstance.value.setOption({
    tooltip: { trigger: "item" },
    series: [
      {
        type: "graph",
        layout: "force",
        roam: true,
        draggable: true,
        data: nodes,
        links,
        edgeSymbol: ["arrow", "none"],
        edgeSymbolSize: 10,
        label: {
          show: true,
          position: "right",
          formatter: "{b}",
          color: "#24324b",
        },
        force: {
          repulsion: 120,
          edgeLength: [80, 160],
        },
        emphasis: {
          focus: "adjacency",
        },
      },
    ],
  });

  chartInstance.value.off("click");
  chartInstance.value.on("click", handleNodeClick);
};

// 根据 courseId 拉取图数据，并做兜底结构化处理
const fetchGraph = async (courseId) => {
  loading.value = true;
  try {
    const res = await GetKnowledgeGraph(courseId);
    const payload = res?.data?.data ?? res?.data ?? {};
    graphData.value = {
      courseId: payload.courseId ?? courseId,
      title: payload.title ?? null,
      userId: payload.userId ?? null,
      nodes: Array.isArray(payload.nodes) ? payload.nodes : [],
      links: Array.isArray(payload.links) ? payload.links : [],
      stats: payload.stats ?? null,
    };
    if (!graphData.value.nodes.length) {
      clearChart();
      chartHint.value = "未查询到图谱数据";
      return;
    }
    chartHint.value = "";
    // 等待容器从隐藏状态切换完成，再初始化/重绘图表，避免出现空白遮挡
    await nextTick();
    buildChart(graphData.value);
    chartInstance.value?.resize();
  } catch (e) {
    clearChart();
    chartHint.value = "图谱加载失败";
    logger.error("获取知识图谱失败", e);
  } finally {
    loading.value = false;
  }
};

const resetGraphData = () => {
  graphData.value = {
    courseId: null,
    title: null,
    userId: null,
    nodes: [],
    links: [],
    stats: null,
  };
};

const handleSearch = async () => {
  const keyword = courseKeyword.value?.trim();
  if (!keyword) {
    chartHint.value = "请输入课程关键词";
    resetGraphData();
    clearChart();
    return;
  }
  const courseId = Number(keyword);
  if (!Number.isFinite(courseId) || courseId <= 0) {
    chartHint.value = "课程ID格式不正确";
    resetGraphData();
    clearChart();
    return;
  }
  await fetchGraph(courseId);
};

// 窗口变化时同步图表尺寸，避免容器变更后显示错位
const handleResize = () => {
  chartInstance.value?.resize();
};

onMounted(() => {
  window.addEventListener("resize", handleResize);
  const initialCourseId = Number(route.query.courseId);
  if (Number.isFinite(initialCourseId) && initialCourseId > 0) {
    courseKeyword.value = String(initialCourseId);
    handleSearch();
  }
});

onBeforeUnmount(() => {
  window.removeEventListener("resize", handleResize);
  chartInstance.value?.dispose();
  chartInstance.value = null;
});
</script>

<style scoped>
.knowledge-graph-page {
  padding: 16px 20px 40px;
}

.search-card {
  margin-bottom: 16px;
}

.search-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.search-input {
  max-width: 320px;
}

.header {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-bottom: 16px;
}

.title {
  font-size: 22px;
  font-weight: 600;
  color: #1f2a44;
}

.subtitle {
  color: #6b778c;
  font-size: 14px;
}

.summary-card {
  margin-bottom: 16px;
}

.summary-row {
  display: flex;
  flex-wrap: wrap;
  gap: 24px;
}

.summary-item .label {
  color: #97a3b6;
  font-size: 12px;
}

.summary-item .value {
  font-size: 18px;
  font-weight: 600;
  color: #24324b;
}

.chart-card {
  min-height: 460px;
}

.chart {
  width: 100%;
  height: 440px;
}

.course-option-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.course-option-btn {
  width: 100%;
  justify-content: flex-start;
  padding: 6px 0;
  height: auto;
}

.course-option-btn :deep(.el-button__text) {
  width: 100%;
  text-align: left;
}

.course-option-list :deep(.el-button + .el-button) {
  margin-left: 0;
  margin-top: 6px;
}

@media (max-width: 960px) {
  .search-row {
    flex-direction: column;
    align-items: stretch;
  }

  .search-input {
    max-width: none;
  }

  .chart {
    height: 360px;
  }
}
</style>
