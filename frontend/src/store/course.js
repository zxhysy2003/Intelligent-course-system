import { ref } from 'vue'
import { defineStore } from 'pinia'

export const useCourseStore = defineStore('course', () => {
  // 临时存储当前选中的课程信息（用于页面跳转）
  const currentCourse = ref(null)

  const setCurrentCourse = (course) => {
    currentCourse.value = course
  }

  const clearCurrentCourse = () => {
    currentCourse.value = null
  }

  return {
    currentCourse,
    setCurrentCourse,
    clearCurrentCourse
  }
})
