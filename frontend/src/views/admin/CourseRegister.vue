<template>
  <div class="course-register-page">
    <div class="header">
      <h2 class="title">注册课程</h2>
      <p class="subtitle">填写课程信息并上传课程视频。</p>
    </div>

    <el-card shadow="hover" class="register-card">
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        class="register-form"
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

        <el-form-item label="课程视频">
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
            <el-button type="primary" plain>选择视频文件</el-button>
            <template #tip>
              <div class="upload-tip">
                支持 mp4/webm/ogg，建议小于 500MB。当前：{{ selectedVideoName || "未选择文件" }}
              </div>
              <div class="upload-tip">选择视频后会自动填充预估时长（秒）</div>
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
          <el-button @click="resetForm">重置</el-button>
          <el-button type="primary" :loading="submitting" @click="submitForm">提交注册</el-button>
        </div>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from "vue";
import {
  GetCategories,
  GetCourseRegisterOptions,
  RegisterCourse,
  UploadCourseVideo,
} from "@/api/course";
import { logger } from "@/utils/logger";

const formRef = ref(null);
const submitting = ref(false);
const categories = ref([]);
const tagOptions = ref([]);
const knowledgePointOptions = ref([]);
const selectedVideoFile = ref(null);
const selectedVideoName = ref("");

const form = reactive({
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
  fillEstimatedDurationSeconds(uploadFile.raw);
};

const handleVideoRemove = () => {
  selectedVideoFile.value = null;
  selectedVideoName.value = "";
};

const fillEstimatedDurationSeconds = (file) => {
  try {
    const objectUrl = URL.createObjectURL(file);
    const video = document.createElement("video");
    video.preload = "metadata";
    video.onloadedmetadata = () => {
      const seconds = Number(video.duration || 0);
      if (Number.isFinite(seconds) && seconds > 0) {
        form.duration = Math.max(1, Math.floor(seconds));
      }
      URL.revokeObjectURL(objectUrl);
    };
    video.onerror = () => {
      URL.revokeObjectURL(objectUrl);
    };
    video.src = objectUrl;
  } catch (e) {
    logger.warn("无法读取视频时长，请手动填写时长");
  }
};

const resetForm = () => {
  formRef.value?.resetFields();
  form.tagIds = [];
  form.knowledgePointIds = [];
  selectedVideoFile.value = null;
  selectedVideoName.value = "";
};

const submitForm = async () => {
  if (!formRef.value || submitting.value) return;
  try {
    submitting.value = true;
    await formRef.value.validate();

    if (!selectedVideoFile.value) {
      logger.warn("请先选择课程视频");
      return;
    }

    const payload = {
      title: form.title,
      description: form.description,
      coverUrl: form.coverUrl,
      difficulty: form.difficulty,
      duration: form.duration,
      categoryId: form.categoryId,
      tagIds: form.tagIds,
      knowledgePointIds: form.knowledgePointIds,
    };

    const registerRes = await RegisterCourse(payload);
    if (registerRes?.data?.code !== 200) {
      logger.error(registerRes?.data?.msg || "课程注册失败", registerRes?.data);
      return;
    }

    const courseId = registerRes?.data?.data;
    if (!courseId) {
      logger.error("课程注册成功但未返回 courseId");
      return;
    }

    const uploadRes = await UploadCourseVideo(courseId, selectedVideoFile.value);
    if (uploadRes?.data?.code !== 200) {
      logger.error(uploadRes?.data?.msg || "视频上传失败", uploadRes?.data);
      return;
    }

    logger.success("课程注册并上传视频成功");
    resetForm();
  } catch (e) {
    logger.error("请先完善课程信息", e);
  } finally {
    submitting.value = false;
  }
};

onMounted(async () => {
  try {
    const [categoryRes, optionRes] = await Promise.all([
      GetCategories(),
      GetCourseRegisterOptions(),
    ]);
    categories.value = Array.isArray(categoryRes?.data?.data) ? categoryRes.data.data : [];

    const optionsPayload = optionRes?.data?.data || {};
    tagOptions.value = Array.isArray(optionsPayload.tags) ? optionsPayload.tags : [];
    knowledgePointOptions.value = Array.isArray(optionsPayload.knowledgePoints)
      ? optionsPayload.knowledgePoints
      : [];
  } catch (e) {
    logger.error("获取课程注册选项失败", e);
  }
});
</script>

<style scoped>
.course-register-page {
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

.register-card {
  border-radius: 12px;
}

.register-form {
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
  .course-register-page {
    padding: 12px;
  }

  .cover-preview-wrap {
    width: 100%;
    max-width: 320px;
  }
}
</style>
