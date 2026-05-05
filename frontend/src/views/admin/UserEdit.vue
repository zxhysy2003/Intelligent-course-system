<template>
  <div class="user-edit-page" v-loading="loading">
    <div class="header">
      <h2 class="title">修改用户</h2>
      <p class="subtitle">修改用户基础信息、角色与状态。</p>
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
          <el-col :xs="24" :md="12">
            <el-form-item label="用户ID">
              <el-input :model-value="form.id" disabled />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="12">
            <el-form-item label="用户名" prop="username">
              <el-input v-model.trim="form.username" maxlength="30" show-word-limit />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="14">
          <el-col :xs="24" :md="12">
            <el-form-item label="昵称" prop="nickname">
              <el-input v-model.trim="form.nickname" maxlength="30" show-word-limit />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="12">
            <el-form-item label="邮箱" prop="email">
              <el-input v-model.trim="form.email" maxlength="80" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="14">
          <el-col :xs="24" :md="12">
            <el-form-item label="手机号" prop="phone">
              <el-input v-model.trim="form.phone" maxlength="20" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="12">
            <el-form-item label="角色" prop="role">
              <el-select v-model="form.role" class="full-width">
                <el-option label="管理员" value="ADMIN" />
                <el-option label="学生" value="STUDENT" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="12">
            <el-form-item label="状态" prop="status">
              <el-select v-model="form.status" class="full-width">
                <el-option label="启用" :value="1" />
                <el-option label="禁用" :value="0" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <div class="actions">
          <el-button @click="goBack">返回</el-button>
          <el-button type="primary" :loading="submitting" @click="submitForm">保存修改</el-button>
        </div>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { logger } from "@/utils/logger";
import { GetAdminUserDetail, UpdateAdminUser } from "@/api/user";

const route = useRoute();
const router = useRouter();
const formRef = ref(null);
const loading = ref(false);
const submitting = ref(false);

const userId = String(route.params.id || "");

const form = reactive({
  id: userId,
  username: "",
  nickname: "",
  email: "",
  phone: "",
  role: "STUDENT",
  status: 1,
});

const rules = {
  username: [{ required: true, message: "请输入用户名", trigger: "blur" }],
  nickname: [{ required: true, message: "请输入昵称", trigger: "blur" }],
  email: [
    { required: true, message: "请输入邮箱", trigger: "blur" },
    { type: "email", message: "请输入合法邮箱", trigger: ["blur", "change"] },
  ],
  phone: [{ required: true, message: "请输入手机号", trigger: "blur" }],
  role: [{ required: true, message: "请选择角色", trigger: "change" }],
  status: [{ required: true, message: "请选择状态", trigger: "change" }],
};

const fillForm = (raw) => {
  if (!raw) return;
  form.id = String(raw.id ?? userId);
  form.username = raw.username || "";
  form.nickname = raw.nickname || "";
  form.email = raw.email || "";
  form.phone = raw.phone || "";
  form.role = raw.role || "STUDENT";
  form.status = Number(raw.status) === 0 ? 0 : 1;
};

const loadDetail = async () => {
  if (!userId) {
    logger.error("无效的用户ID");
    goBack();
    return;
  }
  loading.value = true;
  try {
    const res = await GetAdminUserDetail(userId);
    if (res?.data?.code !== 200) {
      logger.error(res?.data?.msg || "获取用户详情失败", res?.data);
      return;
    }
    fillForm(res?.data?.data);
  } catch (e) {
    logger.error("获取用户详情失败", e);
  } finally {
    loading.value = false;
  }
};

const submitForm = async () => {
  if (!formRef.value) return;
  const valid = await formRef.value.validate().catch(() => false);
  if (!valid) return;

  submitting.value = true;
  try {
    const payload = {
      id: form.id,
      username: form.username,
      nickname: form.nickname,
      email: form.email,
      phone: form.phone,
      role: form.role,
      status: form.status,
    };
    const res = await UpdateAdminUser(payload);
    if (res?.data?.code !== 200) {
      logger.error(res?.data?.msg || "更新用户失败", res?.data);
      return;
    }
    logger.success("用户信息更新成功");
    goBack();
  } catch (e) {
    logger.error("更新用户失败", e);
  } finally {
    submitting.value = false;
  }
};

const goBack = () => {
  router.push("/admin/users");
};

onMounted(loadDetail);
</script>

<style scoped>
.user-edit-page {
  max-width: 980px;
  margin: 0 auto;
  padding: 16px;
}

.header {
  margin-bottom: 12px;
}

.title {
  margin: 0;
  font-size: 24px;
}

.subtitle {
  margin: 6px 0 0;
  color: #666;
  font-size: 14px;
}

.edit-card {
  border-radius: 12px;
}

.edit-form {
  width: 100%;
}

.full-width {
  width: 100%;
}

.actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 8px;
}
</style>
