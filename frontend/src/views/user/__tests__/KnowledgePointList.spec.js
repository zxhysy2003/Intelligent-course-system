import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import KnowledgePointList from '../KnowledgePointList.vue'

const global = {
  stubs: {
    'el-tag': {
      template: '<span class="tag" :style="$attrs.style"><slot /></span>',
    },
  },
}

describe('KnowledgePointList', () => {
  it('渲染空状态', () => {
    const wrapper = mount(KnowledgePointList, { global })
    expect(wrapper.text()).toContain('暂无知识点数据')
  })

  it('渲染知识点并为未知难度使用默认样式', () => {
    const wrapper = mount(KnowledgePointList, {
      props: {
        knowledgePoints: [{ id: 1, name: 'Vue 响应式', difficulty: 99 }],
      },
      global,
    })
    const tag = wrapper.get('.tag')

    expect(tag.text()).toContain('Vue 响应式 · 难度 99')
    expect(tag.element.style.backgroundColor).toBe('rgb(241, 248, 233)')
  })
})
