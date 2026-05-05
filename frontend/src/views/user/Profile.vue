<template>
  <div class="profile-page" v-loading="loading">
    <div class="header">
      <h2 class="title">个人中心</h2>
      <div class="subtitle">能力雷达图</div>
    </div>

    <el-card shadow="hover" class="radar-card">
      <el-empty v-if="empty" description="暂无能力数据" />
      <template v-else>
        <div v-if="radarHint" class="radar-hint">{{ radarHint }}</div>
        <div ref="chartRef" class="radar-chart"></div>
      </template>
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, onBeforeUnmount, ref, nextTick } from "vue";
import * as echarts from "echarts";
import { GetAbilityRadar } from "@/api/analysis";
import { logger } from "@/utils/logger";

const loading = ref(false);
const empty = ref(false);
const chartRef = ref(null);
const chartInstance = ref(null);
const useMockData = ref(false);
const radarHint = ref("");

const mockRadarData = {
  indicator: [
    { name: "Python数据分析", max: 100 },
    { name: "推荐系统", max: 100 },
    { name: "机器学习", max: 100 },
    { name: "后端开发", max: 100 },
    { name: "前端开发", max: 100 },
    { name: "数据库", max: 100 },
  ],
  values: [92.26, 89.26, 78.4, 81.5, 74.2, 86.8],
};

const radarData = ref({
  indicator: [],
  values: [],
});

const enrichRadarData = (source) => {
  const indicators = [...source.indicator];
  const values = [...source.values];

  // 少于 3 个维度时雷达图会退化成线段，补充“综合能力”维度以形成可读面积。
  if (indicators.length > 0 && indicators.length < 3 && indicators.length === values.length) {
    const avg = Number((values.reduce((sum, cur) => sum + Number(cur || 0), 0) / values.length).toFixed(2));
    indicators.push({ name: "综合能力", max: 100 });
    values.push(avg);
    radarHint.value = "维度较少，已自动补充“综合能力”维度以增强可视化效果";
    return { indicator: indicators, values };
  }

  radarHint.value = "";
  return { indicator: indicators, values };
};

const normalizeRadarData = (payload) => {
  if (!payload || typeof payload !== "object") {
    return { indicator: [], values: [] };
  }
  return enrichRadarData({
    indicator: Array.isArray(payload.indicator) ? payload.indicator : [],
    values: Array.isArray(payload.values) ? payload.values : [],
  });
};

const renderRadar = async () => {
  if (!chartRef.value) return;
  if (!chartInstance.value) {
    chartInstance.value = echarts.init(chartRef.value);
  }

  await nextTick();

  chartInstance.value.setOption({
    tooltip: {
      trigger: "item",
    },
    legend: {
      top: 8,
      data: ["能力值"],
    },
    radar: {
      indicator: radarData.value.indicator,
      radius: "65%",
      splitNumber: 5,
      axisName: {
        color: "#24324b",
      },
      splitLine: {
        lineStyle: {
          color: "#dce4ef",
        },
      },
      splitArea: {
        areaStyle: {
          color: ["#f8fbff", "#f1f6fd"],
        },
      },
    },
    series: [
      {
        name: "能力值",
        type: "radar",
        data: [
          {
            value: radarData.value.values,
            name: "能力值",
            areaStyle: {
              color: "rgba(64, 158, 255, 0.2)",
            },
            lineStyle: {
              color: "#409eff",
              width: 2,
            },
            itemStyle: {
              color: "#409eff",
            },
          },
        ],
      },
    ],
  });
};

const fetchAbilityRadar = async () => {
  if (useMockData.value) {
    radarData.value = normalizeRadarData(mockRadarData);
    empty.value = false;
    await renderRadar();
    chartInstance.value?.resize();
    return;
  }

  loading.value = true;
  try {
    const res = await GetAbilityRadar();
    const payload = res?.data?.data ?? {};
    radarData.value = normalizeRadarData(payload);
    empty.value =
      !radarData.value.indicator.length ||
      !radarData.value.values.length ||
      radarData.value.indicator.length !== radarData.value.values.length;

    if (!empty.value) {
      await renderRadar();
      chartInstance.value?.resize();
    }
  } catch (e) {
    empty.value = true;
    logger.error("获取能力雷达图失败", e);
  } finally {
    loading.value = false;
  }
};

const handleResize = () => {
  chartInstance.value?.resize();
};

onMounted(() => {
  fetchAbilityRadar();
  window.addEventListener("resize", handleResize);
});

onBeforeUnmount(() => {
  window.removeEventListener("resize", handleResize);
  chartInstance.value?.dispose();
  chartInstance.value = null;
});
</script>

<style scoped>
.profile-page {
  padding: 16px 20px 24px;
}

.header {
  margin-bottom: 12px;
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

.radar-card {
  min-height: 420px;
}

.radar-chart {
  width: 100%;
  height: 420px;
}

.radar-hint {
  margin-bottom: 8px;
  padding: 8px 10px;
  border-radius: 8px;
  background: #f4f8ff;
  color: #4f5f7a;
  font-size: 12px;
}
</style>
