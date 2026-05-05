<template>
    <el-container class="layout-container">
        <!-- 左侧菜单 -->
        <el-aside class="layout-aside" :class="{ collapsed: isCollapsed }">
            <div class="logo">
                <span class="logo-icon">📚</span>
                <span v-if="!isCollapsed" class="logo-text">课程系统</span>
            </div>
            <div class="menu-container">
                <button class="toggle-btn" @click="isCollapsed = !isCollapsed" :title="isCollapsed ? '展开' : '收缩'">
                    <el-icon>
                        <component :is="isCollapsed ? ArrowRight : ArrowLeft" />
                    </el-icon>
                </button>
                <el-menu router class="side-menu" :collapse="isCollapsed">
                <template v-if="isAdmin">
                    <el-menu-item index="/admin/course">
                        <el-icon><Document /></el-icon>
                        <span>课程管理</span>
                    </el-menu-item>
                    <el-menu-item index="/admin/users">
                        <el-icon><User /></el-icon>
                        <span>用户管理</span>
                    </el-menu-item>
                    <el-menu-item index="/course">
                        <el-icon><Document /></el-icon>
                        <span>课程学习</span>
                    </el-menu-item>
                    <el-menu-item index="/graph">
                        <el-icon><MagicStick /></el-icon>
                        <span>知识图谱</span>
                    </el-menu-item>
                </template>
                <template v-else>
                    <el-menu-item index="/course">
                        <el-icon><Document /></el-icon>
                        <span>课程学习</span>
                    </el-menu-item>
                    <el-menu-item index="/recommend">
                        <el-icon><Star /></el-icon>
                        <span>推荐</span>
                    </el-menu-item>
                    <el-menu-item index="/dashboard">
                        <el-icon><DataAnalysis /></el-icon>
                        <span>学习进度</span>
                    </el-menu-item>
                    <el-menu-item index="/graph">
                        <el-icon><MagicStick /></el-icon>
                        <span>知识图谱</span>
                    </el-menu-item>
                    <el-menu-item index="/profile">
                        <el-icon><User /></el-icon>
                        <span>个人中心</span>
                    </el-menu-item>
                </template>
            </el-menu>
            </div>
        </el-aside>

        <!-- 主区域 -->
        <el-container class="main-container">
            <el-header class="layout-header">
                <div class="header-left">
                    <span class="header-title">智能课程推荐系统</span>
                </div>
                <div class="header-right">
                    <el-dropdown @command="handleCommand">
                        <span class="user-info">
                            <el-icon><UserFilled /></el-icon>
                            {{ userStore.userInfo?.username || '用户' }}
                        </span>
                        <template #dropdown>
                            <el-dropdown-menu>
                                <el-dropdown-item command="profile">个人中心</el-dropdown-item>
                                <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
                            </el-dropdown-menu>
                        </template>
                    </el-dropdown>
                </div>
            </el-header>
            <el-main class="layout-main">
                <router-view />
            </el-main>
        </el-container>
    </el-container>
</template>

<script setup>
import { ref, computed } from "vue"
import { useUserStore } from "../store/user"
import { useRouter } from "vue-router"
import { Document, Star, DataAnalysis, MagicStick, User, UserFilled, ArrowLeft, ArrowRight } from '@element-plus/icons-vue'


const userStore = useUserStore();
const router = useRouter();

// 菜单收缩状态
const isCollapsed = ref(false);
const isAdmin = computed(() => userStore.userInfo?.role === "ADMIN");

const handleCommand = (command) => {
    if (command === 'logout') {
        userStore.clearToken();
        window.location.reload();
    } else if (command === 'profile') {
        router.push('/profile');
    }
}
</script>

<style scoped>
.layout-container {
    height: 100vh;
    display: flex;
}

.layout-aside {
    width: 220px;
    background: linear-gradient(180deg, #ffffff 0%, #fafafa 100%);
    border-right: 1px solid #f0f0f0;
    overflow-y: auto;
    padding: 16px 0;
    transition: width 0.3s ease;
    position: relative;
}

.layout-aside.collapsed {
    width: 60px;
}

.logo {
    display: flex;
    align-items: center;
    justify-content: center;
    padding: 0 16px 24px;
    border-bottom: 1px solid #f0f0f0;
    margin-bottom: 8px;
    position: relative;
    height: 50px;
}

.layout-aside.collapsed .logo {
    padding: 0;
    justify-content: center;
}

.logo-icon {
    font-size: 24px;
    margin-right: 8px;
    flex-shrink: 0;
}

.layout-aside.collapsed .logo-icon {
    margin-right: 0;
}

.logo-text {
    font-size: 16px;
    font-weight: 600;
    color: #333;
    white-space: nowrap;
}

.menu-container {
    display: flex;
    flex-direction: column;
    flex: 1;
    position: relative;
}

.toggle-btn {
    align-self: center;
    width: 32px;
    height: 32px;
    border: 1px solid #e0e0e0;
    background: #fff;
    border-radius: 4px;
    cursor: pointer;
    display: flex;
    align-items: center;
    justify-content: center;
    transition: all 0.3s ease;
    font-size: 14px;
    margin: 8px 0;
    flex-shrink: 0;
}

.toggle-btn:hover {
    background: #f5f5f5;
    border-color: #d0d0d0;
}

.side-menu {
    border: none;
    flex: 1;
    overflow-y: auto;
}

:deep(.side-menu .el-menu-item) {
    padding: 0 16px;
    margin: 4px 8px;
    border-radius: 6px;
    transition: all 0.3s ease;
    height: 40px;
    line-height: 40px;
}

.layout-aside.collapsed :deep(.side-menu .el-menu-item) {
    padding: 0;
    margin: 4px;
    justify-content: center;
}

:deep(.side-menu .el-menu-item:hover) {
    background-color: #f0f0f0;
}

:deep(.side-menu .el-menu-item.is-active) {
    background-color: #e6f7ff;
    color: #1890ff;
}

:deep(.side-menu .el-icon) {
    margin-right: 8px;
}

.layout-aside.collapsed :deep(.side-menu .el-icon) {
    margin-right: 0;
}

.main-container {
    flex: 1;
    display: flex;
    flex-direction: column;
}

.layout-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 0 24px;
    height: 64px;
    background-color: #ffffff;
    border-bottom: 1px solid #f0f0f0;
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
}

.header-left {
    display: flex;
    align-items: center;
}

.header-title {
    font-size: 18px;
    font-weight: 600;
    color: #333;
}

.header-right {
    display: flex;
    align-items: center;
}

.user-info {
    display: flex;
    align-items: center;
    padding: 0 12px;
    border-radius: 6px;
    color: #666;
    cursor: pointer;
    transition: all 0.3s ease;
}

.user-info:hover {
    background-color: #f0f0f0;
    color: #333;
}

:deep(.user-info .el-icon) {
    margin-right: 8px;
}

.layout-main {
    flex: 1;
    background-color: #f5f7fa;
    padding: 24px;
    overflow-y: auto;
}

/* 滚动条美化 */
.layout-aside::-webkit-scrollbar,
.layout-main::-webkit-scrollbar {
    width: 6px;
}

.layout-aside::-webkit-scrollbar-track,
.layout-main::-webkit-scrollbar-track {
    background: transparent;
}

.layout-aside::-webkit-scrollbar-thumb,
.layout-main::-webkit-scrollbar-thumb {
    background: #d9d9d9;
    border-radius: 3px;
}

.layout-aside::-webkit-scrollbar-thumb:hover,
.layout-main::-webkit-scrollbar-thumb:hover {
    background: #bfbfbf;
}
</style>
