<template>
  <div class="dashboard-page" v-loading="loading">
    <div class="header">
      <div>
        <h2 class="title">学习进度</h2>
        <div class="subtitle">近 {{ selectedDays }} 天学习行为统计</div>
      </div>
      <el-select
        v-model="selectedDays"
        class="days-select"
        placeholder="选择天数"
        @change="fetchProgress"
      >
        <el-option :value="7" label="近 7 天" />
        <el-option :value="15" label="近 15 天" />
        <el-option :value="30" label="近 30 天" />
      </el-select>
    </div>

    <el-row :gutter="12" class="metric-row">
      <el-col :xs="12" :sm="12" :md="6">
        <el-card shadow="hover" class="metric-card">
          <div class="metric-label">总课程数</div>
          <div class="metric-value">{{ progressData.totalCourses }}</div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="12" :md="6">
        <el-card shadow="hover" class="metric-card">
          <div class="metric-label">已完成课程</div>
          <div class="metric-value">{{ progressData.finishedCourses }}</div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="12" :md="6">
        <el-card shadow="hover" class="metric-card">
          <div class="metric-label">平均进度</div>
          <div class="metric-value">{{ progressData.avgProgress }}%</div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="12" :md="6">
        <el-card shadow="hover" class="metric-card">
          <div class="metric-label">累计学习时长</div>
          <div class="metric-value">{{ formatDuration(progressData.totalLearnedSeconds) }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="hover" class="chart-card">
      <div ref="chartRef" class="chart"></div>
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, onBeforeUnmount, ref } from "vue";
import * as echarts from "echarts";
import { GetLearningProgress } from "@/api/analysis";
import { logger } from "@/utils/logger";

const chartRef = ref(null);
const chartInstance = ref(null);
const loading = ref(false);
const selectedDays = ref(7);

const progressData = ref({
  dates: [],
  studySeconds: [],
  activeCourses: [],
  totalCourses: 0,
  finishedCourses: 0,
  avgProgress: 0,
  totalLearnedSeconds: 0,
});

const formatDuration = (seconds) => {
  const total = Number(seconds || 0);
  const h = Math.floor(total / 3600);
  const m = Math.floor((total % 3600) / 60);
  const s = total % 60;
  if (h > 0) return `${h}h ${m}m`;
  if (m > 0) return `${m}m ${s}s`;
  return `${s}s`;
};

const renderChart = () => {
  if (!chartRef.value) return;
  if (!chartInstance.value) {
    chartInstance.value = echarts.init(chartRef.value);
  }

  chartInstance.value.setOption({
    tooltip: {
      trigger: "axis",
    },
    legend: {
      data: ["学习时长(秒)", "活跃课程数"],
      top: 0,
    },
    grid: {
      left: 40,
      right: 40,
      top: 48,
      bottom: 40,
    },
    xAxis: {
      type: "category",
      data: progressData.value.dates,
      axisLabel: {
        color: "#6b778c",
      },
    },
    yAxis: [
      {
        type: "value",
        name: "学习时长(秒)",
        axisLabel: { color: "#6b778c" },
      },
      {
        type: "value",
        name: "活跃课程数",
        minInterval: 1,
        axisLabel: { color: "#6b778c" },
      },
    ],
    series: [
      {
        name: "学习时长(秒)",
        type: "bar",
        barMaxWidth: 20,
        data: progressData.value.studySeconds,
        itemStyle: {
          color: "#409eff",
          borderRadius: [4, 4, 0, 0],
        },
      },
      {
        name: "活跃课程数",
        type: "line",
        yAxisIndex: 1,
        smooth: true,
        symbol: "circle",
        symbolSize: 7,
        data: progressData.value.activeCourses,
        itemStyle: {
          color: "#67c23a",
        },
        lineStyle: {
          width: 2,
        },
      },
    ],
  });
};

const normalizeProgressData = (payload) => {
  if (!payload || typeof payload !== "object") {
    return {
      dates: [],
      studySeconds: [],
      activeCourses: [],
      totalCourses: 0,
      finishedCourses: 0,
      avgProgress: 0,
      totalLearnedSeconds: 0,
    };
  }
  return {
    dates: Array.isArray(payload.dates) ? payload.dates : [],
    studySeconds: Array.isArray(payload.studySeconds) ? payload.studySeconds : [],
    activeCourses: Array.isArray(payload.activeCourses) ? payload.activeCourses : [],
    totalCourses: Number(payload.totalCourses || 0),
    finishedCourses: Number(payload.finishedCourses || 0),
    avgProgress: Number(payload.avgProgress || 0),
    totalLearnedSeconds: Number(payload.totalLearnedSeconds || 0),
  };
};

const fetchProgress = async () => {
  loading.value = true;
  try {
    const res = await GetLearningProgress(selectedDays.value);
    const payload = res?.data?.data ?? {};
    progressData.value = normalizeProgressData(payload);
    renderChart();
  } catch (e) {
    logger.error("获取学习进度失败", e);
    progressData.value = normalizeProgressData(null);
    renderChart();
  } finally {
    loading.value = false;
  }
};

const handleResize = () => {
  chartInstance.value?.resize();
};

onMounted(() => {
  fetchProgress();
  window.addEventListener("resize", handleResize);
});

onBeforeUnmount(() => {
  window.removeEventListener("resize", handleResize);
  chartInstance.value?.dispose();
  chartInstance.value = null;
});
</script>

<style scoped>
.dashboard-page {
  padding: 16px 20px 24px;
}

.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.days-select {
  width: 140px;
}

.title {
  margin: 0;
  font-size: 22px;
  color: #1f2a44;
}

.subtitle {
  margin-top: 4px;
  color: #6b778c;
  font-size: 14px;
}

.metric-row {
  margin-bottom: 12px;
}

.metric-card {
  min-height: 100px;
}

.metric-label {
  color: #8a97ad;
  font-size: 12px;
}

.metric-value {
  margin-top: 10px;
  color: #24324b;
  font-size: 24px;
  font-weight: 700;
}

.chart-card {
  min-height: 420px;
}

.chart {
  width: 100%;
  height: 380px;
}

@media (max-width: 768px) {
  .header {
    align-items: flex-start;
    flex-direction: column;
  }

  .days-select {
    width: 100%;
    max-width: 220px;
  }
}
</style>
