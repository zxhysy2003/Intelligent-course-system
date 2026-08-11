import { createRouter, createWebHashHistory } from 'vue-router'
import { useOnboardingStore } from '@/store/onboarding'
import { useUserStore } from '@/store/user'

const Login = () => import('@/views/Login.vue')
const Register = () => import('@/views/Register.vue')
const MainLayout = () => import('@/layouts/MainLayout.vue')
const Profile = () => import('@/views/user/Profile.vue')
const CourseList = () => import('@/views/user/CourseList.vue')
const Recommend = () => import('@/views/user/Recommend.vue')
const Dashboard = () => import('@/views/user/Dashboard.vue')
const KnowledgeGraph = () => import('@/views/user/KnowledgeGraph.vue')
const Onboarding = () => import('@/views/user/Onboarding.vue')
const AgentAssistant = () => import('@/views/user/AgentAssistant.vue')
const NotFound = () => import('@/views/NotFound.vue')
const CourseDetail = () => import('@/views/user/CourseDetail.vue')
const CourseManage = () => import('@/views/admin/CourseManage.vue')
const UserManage = () => import('@/views/admin/UserManage.vue')
const UserEdit = () => import('@/views/admin/UserEdit.vue')
const CourseEdit = () => import('@/views/admin/CourseEdit.vue')
const CourseRegister = () => import('@/views/admin/CourseRegister.vue')

export const routes = [
  {
    path: '/login',
    name: 'Login',
    component: Login,
    meta: { public: true },
  },
  {
    path: '/register',
    name: 'Register',
    component: Register,
    meta: { public: true },
  },
  {
    path: '/',
    name: 'MainLayout',
    component: MainLayout,
    children: [
      { path: '', redirect: { name: 'CourseList' } },
      { path: 'course', name: 'CourseList', component: CourseList },
      { path: 'courseDetail/:id', name: 'CourseDetail', component: CourseDetail },
      {
        path: 'onboarding',
        name: 'Onboarding',
        component: Onboarding,
        meta: { skipOnboarding: true },
      },
      { path: 'recommend', name: 'Recommend', component: Recommend },
      { path: 'dashboard', name: 'Dashboard', component: Dashboard },
      { path: 'agent', name: 'AgentAssistant', component: AgentAssistant },
      { path: 'graph', name: 'KnowledgeGraph', component: KnowledgeGraph },
      { path: 'profile', name: 'Profile', component: Profile },
      {
        path: 'admin/course',
        name: 'AdminCourseList',
        component: CourseManage,
        meta: { roles: ['ADMIN'] },
      },
      {
        path: 'admin/course/edit/:id',
        name: 'CourseEdit',
        component: CourseEdit,
        meta: { roles: ['ADMIN'] },
      },
      {
        path: 'admin/course/register',
        name: 'CourseRegister',
        component: CourseRegister,
        meta: { roles: ['ADMIN'] },
      },
      {
        path: 'admin/users',
        name: 'AdminUserList',
        component: UserManage,
        meta: { roles: ['ADMIN'] },
      },
      {
        path: 'admin/users/edit/:id',
        name: 'UserEdit',
        component: UserEdit,
        meta: { roles: ['ADMIN'] },
      },
    ],
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: NotFound,
  },
]

export function createNavigationGuard() {
  return async (to) => {
    const userStore = useUserStore()

    if (!to.meta.public && !userStore.isLoggedIn) {
      useOnboardingStore().reset()
      return { name: 'Login' }
    }

    const allowedRoles = Array.isArray(to.meta.roles) ? to.meta.roles : []
    if (allowedRoles.length && !allowedRoles.includes(userStore.userInfo?.role)) {
      return { name: 'CourseList' }
    }

    const isAdmin = userStore.userInfo?.role === 'ADMIN'
    if (userStore.isLoggedIn && !isAdmin && !to.meta.skipOnboarding) {
      const onboardingStore = useOnboardingStore()
      try {
        const status = await onboardingStore.fetchStatus()
        if (!status.completed) {
          return {
            name: 'Onboarding',
            query: { redirect: to.fullPath },
          }
        }
      } catch (error) {
        // 状态接口异常时不阻断用户进入主流程，页面内会继续给出错误提示。
        console.error('获取引导状态失败', error)
      }
    }

    return true
  }
}

const router = createRouter({
  history: createWebHashHistory(),
  routes,
})

router.beforeEach(createNavigationGuard())

export default router
