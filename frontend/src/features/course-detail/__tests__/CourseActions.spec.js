import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import CourseActions from '../components/CourseActions.vue'
import { ENROLLMENT_STATUS } from '../enrollmentStatus'

const global = {
  stubs: {
    'el-button': {
      props: ['disabled', 'loading'],
      emits: ['click'],
      template: '<button :disabled="disabled" :data-loading="loading" @click="$emit(\'click\')"><slot /></button>',
    },
  },
}

describe('CourseActions', () => {
  it('未选课时上报选课和图谱操作', async () => {
    const wrapper = mount(CourseActions, {
      props: {
        enrollmentStatus: ENROLLMENT_STATUS.NOT_ENROLLED,
        isFavorite: false,
      },
      global,
    })
    const buttons = wrapper.findAll('button')

    expect(wrapper.text()).toContain('加入课程')
    await buttons[0].trigger('click')
    await buttons[1].trigger('click')

    expect(wrapper.emitted('enroll')).toHaveLength(1)
    expect(wrapper.emitted('open-graph')).toHaveLength(1)
  })

  it('已选课时上报收藏操作，并在请求中禁用按钮', async () => {
    const wrapper = mount(CourseActions, {
      props: {
        enrollmentStatus: ENROLLMENT_STATUS.ENROLLED,
        isFavorite: true,
        favoriteLoading: true,
      },
      global,
    })
    const favoriteButton = wrapper.findAll('button')[0]

    expect(favoriteButton.attributes('disabled')).toBeDefined()
    expect(favoriteButton.attributes('data-loading')).toBe('true')

    await wrapper.setProps({ favoriteLoading: false })
    await favoriteButton.trigger('click')
    expect(wrapper.emitted('toggle-favorite')).toHaveLength(1)
  })

  it('加载中禁用操作，失败时上报重试事件', async () => {
    const wrapper = mount(CourseActions, {
      props: {
        enrollmentStatus: ENROLLMENT_STATUS.LOADING,
        isFavorite: false,
      },
      global,
    })

    expect(wrapper.text()).toContain('加载选课状态')
    expect(wrapper.findAll('button')[0].attributes('disabled')).toBeDefined()

    await wrapper.setProps({ enrollmentStatus: ENROLLMENT_STATUS.ERROR })
    expect(wrapper.text()).toContain('重试选课状态')
    await wrapper.findAll('button')[0].trigger('click')
    expect(wrapper.emitted('retry-relation')).toHaveLength(1)
  })
})
