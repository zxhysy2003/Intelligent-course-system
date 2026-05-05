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
import NotFound from "@/views/404.vue";
import CourseDetail from "@/views/user/CourseDetail.vue";
import CourseManage from "@/views/admin/CourseManage.vue";
import UserManage from "@/views/admin/UserManage.vue";
import UserEdit from "@/views/admin/UserEdit.vue";
import CourseEdit from "@/views/admin/CourseEdit.vue";
import CourseRegister from "@/views/admin/CourseRegister.vue";


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
    // 将匹配所有内容并将其放在 `route.params.pathMatch` 下
    { 
        path: '/:pathMatch(.*)*', 
        name: 'NotFound', 
        component: NotFound 
    },
    {
        path: "/",
        component: Layout,
        children: [
            { path: "", redirect: "/course" },
            { path: "course", component: Course },
            { path: "courseDetail/:id", name: "CourseDetail", component: CourseDetail },
            { path: "recommend", component: Recommend },
            { path: "dashboard", component: Dashboard },
            { path: "graph", component: KnowledgeGraph },
            { path: "profile", component: Profile },
            { path: "admin/course", component: CourseManage },
            { path: "admin/course/edit/:id", name: "CourseEdit", component: CourseEdit },
            { path: "admin/course/register", name: "CourseRegister", component: CourseRegister },
            { path: "admin/users", component: UserManage },
            { path: "admin/users/edit/:id", name: "UserEdit", component: UserEdit }
        ]
    },
];

const router = createRouter({
    history: createWebHashHistory(),
    routes,
});

router.beforeEach((to, from, next) => {
    const userStore = useUserStore();
    if (to.path !== "/login" && to.path !== "/register" && !userStore.isLoggedIn) {
        next({ name: "Login" });
    } else {
        if (to.path.startsWith("/admin") && userStore.userInfo?.role !== "ADMIN") {
            next({ path: "/course" });
        } else {
            next();
        }
    }
});

export default router;
