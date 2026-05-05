<template>
  <div class="user-manage">
    <h2>用户管理</h2>
    <div class="subtitle">支持搜索、筛选、角色调整、启用禁用与批量删除</div>

    <div class="toolbar">
      <el-input
        v-model.trim="query.keyword"
        class="toolbar-item input"
        clearable
        placeholder="搜索用户名/昵称/邮箱"
        @keyup.enter="searchUsers"
      />
      <el-select v-model="query.role" class="toolbar-item select" clearable placeholder="全部角色">
        <el-option label="管理员" value="ADMIN" />
        <el-option label="学生" value="STUDENT" />
      </el-select>
      <el-select v-model="query.status" class="toolbar-item select" clearable placeholder="全部状态">
        <el-option label="启用" :value="1" />
        <el-option label="禁用" :value="0" />
      </el-select>

      <el-button type="primary" :loading="loading" @click="searchUsers">搜索</el-button>
      <el-button @click="resetFilters">重置</el-button>
      <el-button type="warning" plain :disabled="!selectedIds.length" @click="batchSetStatus(0)">批量禁用</el-button>
      <el-button type="success" plain :disabled="!selectedIds.length" @click="batchSetStatus(1)">批量启用</el-button>
      <el-button type="danger" plain :disabled="!selectedIds.length" @click="batchDelete">
        删除用户<span v-if="selectedIds.length">（{{ selectedIds.length }}）</span>
      </el-button>
    </div>

    <el-table
      v-loading="loading"
      :data="users"
      border
      @selection-change="onSelectionChange"
      row-key="id"
      empty-text="暂无用户数据"
      class="table"
    >
      <el-table-column type="selection" width="50" />
      <el-table-column prop="id" label="用户ID" min-width="180" />
      <el-table-column prop="username" label="用户名" min-width="120" />
      <el-table-column prop="nickname" label="昵称" min-width="120" />
      <el-table-column prop="email" label="邮箱" min-width="180" show-overflow-tooltip />
      <el-table-column label="角色" width="160">
        <template #default="{ row }">
          <el-select
            :model-value="row.role"
            size="small"
            @change="value => updateRole(row, value)"
          >
            <el-option label="管理员" value="ADMIN" />
            <el-option label="学生" value="STUDENT" />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="110" align="center">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'">
            {{ row.status === 1 ? "启用" : "禁用" }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="280" fixed="right">
        <template #default="{ row }">
          <el-space>
            <el-button size="small" type="primary" @click="goToUserEdit(row)">
              修改
            </el-button>
            <el-button
              size="small"
              :type="row.status === 1 ? 'warning' : 'success'"
              @click="toggleStatus(row)"
            >
              {{ row.status === 1 ? "禁用" : "启用" }}
            </el-button>
            <el-button size="small" type="danger" plain @click="deleteOne(row)">
              删除
            </el-button>
          </el-space>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination">
      <el-pagination
        background
        layout="prev, pager, next, ->, sizes"
        :current-page="query.page"
        :page-size="query.pageSize"
        :page-sizes="[10, 20, 30, 50]"
        :total="total"
        @current-change="handlePageChange"
        @size-change="handleSizeChange"
      />
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from "vue";
import { ElMessageBox } from "element-plus";
import { useRouter } from "vue-router";
import { logger } from "../../utils/logger";
import {
  DeleteAdminUsers,
  GetAdminUsers,
  UpdateAdminUserRole,
  UpdateAdminUserStatus,
} from "../../api/user";

const router = useRouter();
const loading = ref(false);
const users = ref([]);
const total = ref(0);
const selectedIds = ref([]);

const query = reactive({
  page: 1,
  pageSize: 10,
  keyword: "",
  role: null,
  status: null,
});

const normalizeUser = (item) => {
  const id = item?.id ?? item?.userId ?? "";
  const rawStatus = Number(item?.status);
  return {
    id: String(id),
    username: item?.username || "-",
    nickname: item?.nickname || "-",
    email: item?.email || "-",
    role: item?.role || "STUDENT",
    status: Number.isFinite(rawStatus) ? rawStatus : 1,
  };
};

const useMockUsers = () => {
  const list = [
    { id: "10001", username: "admin", nickname: "系统管理员", email: "admin@test.com", role: "ADMIN", status: 1 },
    { id: "10002", username: "alice", nickname: "Alice", email: "alice@test.com", role: "STUDENT", status: 1 },
    { id: "10003", username: "bob", nickname: "Bob", email: "bob@test.com", role: "STUDENT", status: 0 },
  ];
  users.value = list.map(normalizeUser);
  total.value = list.length;
};

const searchUsers = async () => {
  loading.value = true;
  try {
    const res = await GetAdminUsers({
      page: query.page,
      pageSize: query.pageSize,
      keyword: query.keyword || null,
      role: query.role,
      status: query.status,
    });
    const payload = res?.data?.data;
    const records = payload?.records || payload || [];
    users.value = Array.isArray(records) ? records.map(normalizeUser) : [];
    total.value = Number(payload?.total ?? users.value.length);
  } catch (e) {
    logger.warn("用户接口暂不可用，已显示本地示例数据");
    useMockUsers();
  } finally {
    loading.value = false;
  }
};

const resetFilters = () => {
  query.page = 1;
  query.pageSize = 10;
  query.keyword = "";
  query.role = null;
  query.status = null;
  searchUsers();
};

const onSelectionChange = (rows) => {
  selectedIds.value = rows.map(row => row.id);
};

const handlePageChange = (p) => {
  query.page = p;
  searchUsers();
};

const handleSizeChange = (size) => {
  query.pageSize = size;
  query.page = 1;
  searchUsers();
};

const updateRole = async (row, role) => {
  if (row.role === role) return;
  const oldRole = row.role;
  row.role = role;
  try {
    const res = await UpdateAdminUserRole(row.id, role);
    if (res?.data?.code !== 200) {
      row.role = oldRole;
      logger.error(res?.data?.msg || "更新角色失败", res?.data);
      return;
    }
    logger.success("角色更新成功");
  } catch (e) {
    row.role = oldRole;
    logger.error("更新角色失败", e);
  }
};

const setUserStatus = async (id, status) => {
  const res = await UpdateAdminUserStatus(id, status);
  if (res?.data?.code !== 200) {
    throw new Error(res?.data?.msg || "更新状态失败");
  }
};

const toggleStatus = async (row) => {
  const target = row.status === 1 ? 0 : 1;
  try {
    await setUserStatus(row.id, target);
    row.status = target;
    logger.success(target === 1 ? "用户已启用" : "用户已禁用");
  } catch (e) {
    logger.error(e);
  }
};

const goToUserEdit = (row) => {
  router.push({
    name: "UserEdit",
    params: { id: row.id },
  });
};

const batchSetStatus = async (status) => {
  if (!selectedIds.value.length) return;
  const jobs = users.value
    .filter(item => selectedIds.value.includes(item.id) && item.status !== status)
    .map(item => setUserStatus(item.id, status));
  try {
    await Promise.all(jobs);
    users.value.forEach(item => {
      if (selectedIds.value.includes(item.id)) item.status = status;
    });
    logger.success(status === 1 ? "批量启用成功" : "批量禁用成功");
  } catch (e) {
    logger.error("批量更新状态失败", e);
  }
};

const doDelete = async (ids) => {
  const res = await DeleteAdminUsers(ids);
  if (res?.data?.code !== 200) {
    throw new Error(res?.data?.msg || "删除失败");
  }
};

const deleteOne = async (row) => {
  try {
    await ElMessageBox.confirm(`确认删除用户「${row.username}」吗？删除后不可恢复。`, "删除确认", {
      type: "warning",
      confirmButtonText: "确认删除",
      cancelButtonText: "取消",
      closeOnClickModal: false,
    });
    await doDelete([row.id]);
    users.value = users.value.filter(item => item.id !== row.id);
    selectedIds.value = selectedIds.value.filter(id => id !== row.id);
    total.value = Math.max(0, total.value - 1);
    logger.success("删除成功");
  } catch (e) {
    if (e !== "cancel" && e !== "close") {
      logger.error("删除失败", e);
    }
  }
};

const batchDelete = async () => {
  if (!selectedIds.value.length) return;
  try {
    await ElMessageBox.confirm(
      `确认删除已选中的 ${selectedIds.value.length} 个用户吗？删除后不可恢复。`,
      "再次确认删除",
      {
        type: "warning",
        confirmButtonText: "确认删除",
        cancelButtonText: "取消",
        closeOnClickModal: false,
      }
    );
    await doDelete(selectedIds.value);
    const selectedSet = new Set(selectedIds.value);
    users.value = users.value.filter(item => !selectedSet.has(item.id));
    total.value = Math.max(0, total.value - selectedIds.value.length);
    selectedIds.value = [];
    logger.success("批量删除成功");
  } catch (e) {
    if (e !== "cancel" && e !== "close") {
      logger.error("批量删除失败", e);
    }
  }
};

searchUsers();
</script>

<style scoped>
.user-manage {
  max-width: 1200px;
  margin: 0 auto;
  padding: 16px;
}

.subtitle {
  color: #666;
  margin-top: 4px;
  margin-bottom: 12px;
  font-size: 14px;
}

.toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  margin-bottom: 12px;
  position: sticky;
  top: 0;
  z-index: 10;
  background: #fff;
  padding: 8px 0;
  box-shadow: 0 8px 16px -12px rgba(0, 0, 0, 0.15);
}

.toolbar-item.input {
  flex: 1 1 280px;
}

.toolbar-item.select {
  width: 150px;
}

.table {
  width: 100%;
}

.pagination {
  display: flex;
  justify-content: center;
  margin-top: 16px;
}
</style>
