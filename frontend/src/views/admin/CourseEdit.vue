<template>
  <div class="course-edit-page" v-loading="loading">
    <div class="header">
      <h2 class="title">修改课程</h2>
      <p class="subtitle">更新课程信息，可按需替换课程视频。</p>
    </div>

    <el-card shadow="hover" class="edit-card">
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        class="edit-form"
      >
        <el-row :gutter="14">
          <el-col :xs="24" :md="16">
            <el-form-item label="课程名称" prop="title">
              <el-input
                v-model.trim="form.title"
                placeholder="例如：Vue.js 前端开发基础"
                maxlength="60"
                show-word-limit
              />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="8">
            <el-form-item label="课程分类" prop="categoryId">
              <el-select v-model="form.categoryId" placeholder="请选择分类" class="full-width">
                <el-option
                  v-for="c in categories"
                  :key="c.id"
                  :label="c.name"
                  :value="c.id"
                />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="14">
          <el-col :xs="24" :md="8">
            <el-form-item label="课程难度" prop="difficulty">
              <el-select v-model="form.difficulty" placeholder="请选择难度" class="full-width">
                <el-option v-for="d in difficultyOptions" :key="d.value" :label="d.label" :value="d.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="8">
            <el-form-item label="课程时长(秒)" prop="duration">
              <el-input-number
                v-model="form.duration"
                :min="1"
                :max="864000"
                :step="1"
                controls-position="right"
                class="full-width"
              />
              <div class="field-tip">单位为秒，最终时长以后端视频分析结果为准</div>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="8">
            <el-form-item label="封面链接" prop="coverUrl">
              <el-input
                v-model.trim="form.coverUrl"
                placeholder="https://..."
                clearable
              />
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="课程标签" prop="tagIds">
          <el-select
            v-model="form.tagIds"
            multiple
            filterable
            collapse-tags
            collapse-tags-tooltip
            placeholder="请选择课程标签"
            class="full-width"
          >
            <el-option
              v-for="tag in tagOptions"
              :key="String(tag.id)"
              :label="`${tag.name}（${tag.type}）`"
              :value="String(tag.id)"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="课程知识点" prop="knowledgePointIds">
          <el-select
            v-model="form.knowledgePointIds"
            multiple
            filterable
            collapse-tags
            collapse-tags-tooltip
            placeholder="请选择知识点"
            class="full-width"
          >
            <el-option
              v-for="kp in knowledgePointOptions"
              :key="String(kp.id)"
              :label="`${kp.name}（${kp.dimensionName} / 难度${kp.difficulty}）`"
              :value="String(kp.id)"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="课程简介" prop="description">
          <el-input
            v-model.trim="form.description"
            type="textarea"
            :rows="4"
            placeholder="简要描述课程目标、适合人群、学习产出..."
            maxlength="300"
            show-word-limit
          />
        </el-form-item>

        <el-form-item label="替换课程视频（可选）">
          <el-upload
            class="video-uploader"
            :auto-upload="false"
            :show-file-list="true"
            :limit="1"
            :before-upload="beforeVideoUpload"
            :on-change="handleVideoChange"
            :on-remove="handleVideoRemove"
            accept="video/mp4,video/webm,video/ogg"
          >
            <el-button type="primary" plain>选择新视频文件</el-button>
            <template #tip>
              <div class="upload-tip">
                仅在选择视频时替换。支持 mp4/webm/ogg，建议小于 500MB。当前：{{ selectedVideoName || "未选择文件" }}
              </div>
            </template>
          </el-upload>
        </el-form-item>

        <el-form-item label="封面预览">
          <div class="cover-preview-wrap">
            <el-image
              v-if="form.coverUrl"
              :src="form.coverUrl"
              fit="cover"
              class="cover-preview"
            >
              <template #error>
                <div class="cover-fallback">封面加载失败</div>
              </template>
            </el-image>
            <div v-else class="cover-fallback">请输入封面链接以预览</div>
          </div>
        </el-form-item>

        <div class="actions">
          <el-button @click="goBack">返回</el-button>
          <el-button type="primary" :loading="submitting" @click="submitForm">保存更新</el-button>
        </div>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import {
  GetCategories,
  GetCourseRegisterOptions,
  GetAdminCourseDetail,
  UpdateCourse,
  UploadCourseVideo,
} from "@/api/course";
import { logger } from "@/utils/logger";

const route = useRoute();
const router = useRouter();
const formRef = ref(null);
const loading = ref(false);
const submitting = ref(false);
const categories = ref([]);
const tagOptions = ref([]);
const knowledgePointOptions = ref([]);
const selectedVideoFile = ref(null);
const selectedVideoName = ref("");

const courseId = String(route.params.id || "");

const form = reactive({
  id: courseId,
  title: "",
  description: "",
  coverUrl: "",
  difficulty: null,
  duration: 60,
  categoryId: null,
  tagIds: [],
  knowledgePointIds: [],
});

const difficultyOptions = [
  { value: 1, label: "初级" },
  { value: 2, label: "中级" },
  { value: 3, label: "高级" },
];

const rules = {
  title: [{ required: true, message: "请输入课程名称", trigger: "blur" }],
  categoryId: [{ required: true, message: "请选择课程分类", trigger: "change" }],
  difficulty: [{ required: true, message: "请选择课程难度", trigger: "change" }],
  duration: [{ required: true, message: "请输入课程时长", trigger: "change" }],
  description: [{ required: true, message: "请输入课程简介", trigger: "blur" }],
  coverUrl: [
    { required: true, message: "请输入封面链接", trigger: "blur" },
    {
      validator: (_, value, callback) => {
        if (!value) return callback();
        const ok = /^https?:\/\/\S+$/i.test(value);
        callback(ok ? undefined : new Error("请输入合法的 URL"));
      },
      trigger: "blur",
    },
  ],
  tagIds: [
    {
      validator: (_, value, callback) => {
        if (Array.isArray(value) && value.length > 0) return callback();
        callback(new Error("请至少选择一个标签"));
      },
      trigger: "change",
    },
  ],
  knowledgePointIds: [
    {
      validator: (_, value, callback) => {
        if (Array.isArray(value) && value.length > 0) return callback();
        callback(new Error("请至少选择一个知识点"));
      },
      trigger: "change",
    },
  ],
};

const beforeVideoUpload = (file) => {
  const okType = ["video/mp4", "video/webm", "video/ogg"].includes(file.type);
  const okSize = file.size / 1024 / 1024 < 500;
  if (!okType) logger.warn("仅支持 mp4/webm/ogg 视频格式");
  if (!okSize) logger.warn("视频大小不能超过 500MB");
  return okType && okSize;
};

const handleVideoChange = (uploadFile) => {
  if (!uploadFile?.raw) return;
  if (!beforeVideoUpload(uploadFile.raw)) {
    selectedVideoFile.value = null;
    selectedVideoName.value = "";
    return;
  }
  selectedVideoFile.value = uploadFile.raw;
  selectedVideoName.value = uploadFile.name || uploadFile.raw.name || "";
};

const handleVideoRemove = () => {
  selectedVideoFile.value = null;
  selectedVideoName.value = "";
};

const normalizeIds = (list) => {
  if (!Array.isArray(list)) return [];
  return list.map((id) => String(id));
};

const mapTagIdsFromDetail = (detail) => {
  if (Array.isArray(detail?.tagIds)) return normalizeIds(detail.tagIds);
  if (Array.isArray(detail?.tags)) return normalizeIds(detail.tags.map((t) => t?.id));
  if (Array.isArray(detail?.options?.tags)) return normalizeIds(detail.options.tags.map((t) => t?.id));
  if (Array.isArray(detail?.tagList)) {
    const nameSet = new Set(detail.tagList);
    return tagOptions.value
      .filter((tag) => nameSet.has(tag.name))
      .map((tag) => String(tag.id));
  }
  return [];
};

const mapKnowledgePointIdsFromDetail = (detail) => {
  if (Array.isArray(detail?.knowledgePointIds)) return normalizeIds(detail.knowledgePointIds);
  if (Array.isArray(detail?.knowledgePoints)) return normalizeIds(detail.knowledgePoints.map((kp) => kp?.id));
  if (Array.isArray(detail?.options?.knowledgePoints)) {
    return normalizeIds(detail.options.knowledgePoints.map((kp) => kp?.id));
  }
  return [];
};

const fillDetail = (detail) => {
  form.id = String(detail?.id ?? courseId);
  form.title = detail?.title || "";
  form.description = detail?.description || "";
  form.coverUrl = detail?.coverUrl || detail?.cover || "";
  form.difficulty = Number(detail?.difficulty || 1);
  form.duration = Number(detail?.duration || 60);
  form.categoryId = Number(detail?.categoryId || null);
  form.tagIds = mapTagIdsFromDetail(detail);
  form.knowledgePointIds = mapKnowledgePointIdsFromDetail(detail);
};

const fetchPageData = async () => {
  if (!courseId) {
    logger.error("课程ID缺失，无法编辑");
    return;
  }

  loading.value = true;
  try {
    const [categoryRes, optionRes, detailRes] = await Promise.all([
      GetCategories(),
      GetCourseRegisterOptions(),
      GetAdminCourseDetail(courseId),
    ]);

    categories.value = Array.isArray(categoryRes?.data?.data) ? categoryRes.data.data : [];

    const optionsPayload = optionRes?.data?.data || {};
    const detail = detailRes?.data?.data || {};
    const detailOptions = detail?.options || {};

    // 优先使用 register-options，接口异常时回退到 detail.options
    tagOptions.value = Array.isArray(optionsPayload.tags) ? optionsPayload.tags : [];
    knowledgePointOptions.value = Array.isArray(optionsPayload.knowledgePoints)
      ? optionsPayload.knowledgePoints
      : [];

    if (!tagOptions.value.length && Array.isArray(detailOptions.tags)) {
      tagOptions.value = detailOptions.tags;
    }
    if (!knowledgePointOptions.value.length && Array.isArray(detailOptions.knowledgePoints)) {
      knowledgePointOptions.value = detailOptions.knowledgePoints;
    }

    fillDetail(detail);
  } catch (e) {
    logger.error("加载课程编辑数据失败", e);
  } finally {
    loading.value = false;
  }
};

const submitForm = async () => {
  if (!formRef.value || submitting.value) return;
  try {
    submitting.value = true;
    await formRef.value.validate();

    const payload = {
      id: form.id,
      title: form.title,
      description: form.description,
      coverUrl: form.coverUrl,
      difficulty: form.difficulty,
      duration: form.duration,
      categoryId: form.categoryId,
      tagIds: form.tagIds,
      knowledgePointIds: form.knowledgePointIds,
    };

    const updateRes = await UpdateCourse(payload);
    if (updateRes?.data?.code !== 200) {
      logger.error(updateRes?.data?.msg || "课程更新失败", updateRes?.data);
      return;
    }

    if (selectedVideoFile.value) {
      const uploadRes = await UploadCourseVideo(form.id, selectedVideoFile.value);
      if (uploadRes?.data?.code !== 200) {
        logger.error(uploadRes?.data?.msg || "视频上传失败", uploadRes?.data);
        return;
      }
    }

    logger.success(selectedVideoFile.value ? "课程更新并上传视频成功" : "课程更新成功");
    selectedVideoFile.value = null;
    selectedVideoName.value = "";
  } catch (e) {
    logger.error("请先完善课程信息", e);
  } finally {
    submitting.value = false;
  }
};

const goBack = () => {
  router.push("/admin/course");
};

onMounted(() => {
  fetchPageData();
});
</script>

<style scoped>
.course-edit-page {
  max-width: 980px;
  margin: 0 auto;
  padding: 16px 18px 24px;
}

.header {
  margin-bottom: 12px;
}

.title {
  margin: 0;
  font-size: 24px;
  color: #1f2a44;
}

.subtitle {
  margin: 6px 0 0;
  color: #6b778c;
  font-size: 14px;
}

.edit-card {
  border-radius: 12px;
}

.edit-form {
  padding-top: 6px;
}

.full-width {
  width: 100%;
}

.video-uploader {
  width: 100%;
}

.upload-tip {
  margin-top: 8px;
  color: #7e889d;
  font-size: 12px;
}

.field-tip {
  margin-top: 6px;
  color: #7e889d;
  font-size: 12px;
}

.cover-preview-wrap {
  width: 240px;
  height: 140px;
  border: 1px dashed #d8dce5;
  border-radius: 8px;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #fafbfe;
}

.cover-preview {
  width: 100%;
  height: 100%;
}

.cover-fallback {
  color: #8b96aa;
  font-size: 13px;
  padding: 0 10px;
  text-align: center;
}

.actions {
  margin-top: 18px;
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

@media (max-width: 768px) {
  .course-edit-page {
    padding: 12px;
  }

  .cover-preview-wrap {
    width: 100%;
    max-width: 320px;
  }
}
</style>
