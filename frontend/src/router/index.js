import {
    createRouter, 
    createWebHashHistory 
} from "vue-router";
import { useUserStore } from "../store/user";

import Login from "@/views/Login.vue";
import Register from "@/views/Register.vue";
import Layout from "@/views/Layout.vue";
import Profile from "@/views/user/Profile.vue";
import Course from "@/views/user/Course.vue";
import Recommend from "@/views/user/Recommend.vue";
import Dashboard from "@/views/user/Dashboard.vue";
import KnowledgeGraph from "@/views/user/KnowledgeGraph.vue";
import Onboarding from "@/views/user/Onboarding.vue";
import AgentAssistant from "@/views/user/AgentAssistant.vue";
import NotFound from "@/views/404.vue";
import CourseDetail from "@/views/user/CourseDetail.vue";
import CourseManage from "@/views/admin/CourseManage.vue";
import UserManage from "@/views/admin/UserManage.vue";
import UserEdit from "@/views/admin/UserEdit.vue";
import CourseEdit from "@/views/admin/CourseEdit.vue";
import CourseRegister from "@/views/admin/CourseRegister.vue";
import { useOnboardingStore } from "@/store/onboarding";


const routes = [
    {
        path: "/login",
        name: "Login",
        component: Login
    },
    {
        path: "/register",
        name: "Register",
        component: Register
    },
    {
        path: "/",
        component: Layout,
        children: [
            { path: "", redirect: "/course" },
            { path: "course", component: Course },
            { path: "courseDetail/:id", name: "CourseDetail", component: CourseDetail },
            { path: "onboarding", name: "Onboarding", component: Onboarding },
            { path: "recommend", component: Recommend },
            { path: "dashboard", component: Dashboard },
            { path: "agent", component: AgentAssistant },
            { path: "graph", component: KnowledgeGraph },
            { path: "profile", component: Profile },
            { path: "admin/course", component: CourseManage },
            { path: "admin/course/edit/:id", name: "CourseEdit", component: CourseEdit },
            { path: "admin/course/register", name: "CourseRegister", component: CourseRegister },
            { path: "admin/users", component: UserManage },
            { path: "admin/users/edit/:id", name: "UserEdit", component: UserEdit }
        ]
    },
    // 将匹配所有内容并将其放在 `route.params.pathMatch` 下
    { 
        path: '/:pathMatch(.*)*', 
        name: 'NotFound', 
        component: NotFound 
    },
];

const router = createRouter({
    history: createWebHashHistory(),
    routes,
});

router.beforeEach(async (to, from, next) => {
    const userStore = useUserStore();
    if (to.path !== "/login" && to.path !== "/register" && !userStore.isLoggedIn) {
        useOnboardingStore().reset();
        next({ name: "Login" });
    } else {
        if (to.path.startsWith("/admin") && userStore.userInfo?.role !== "ADMIN") {
            next({ path: "/course" });
        } else {
            const isAdmin = userStore.userInfo?.role === "ADMIN";
            const isOnboardingPage = to.path === "/onboarding";

            if (userStore.isLoggedIn && !isAdmin && !isOnboardingPage) {
                const onboardingStore = useOnboardingStore();
                try {
                    const status = await onboardingStore.fetchStatus();
                    if (!status.completed) {
                        next({
                            path: "/onboarding",
                            query: { redirect: to.fullPath }
                        });
                        return;
                    }
                } catch (e) {
                    // 状态接口异常时不阻断用户进入主流程，页面内会继续给出错误提示。
                    console.error("获取引导状态失败", e);
                }
            }
            next();
        }
    }
});

export default router;
