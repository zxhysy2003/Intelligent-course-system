import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import AgentComposer from '../components/AgentComposer.vue'
import AgentMessageList from '../components/AgentMessageList.vue'
import AgentSessionPanel from '../components/AgentSessionPanel.vue'

const global = {
  stubs: {
    'el-button': {
      props: ['disabled', 'loading'],
      emits: ['click'],
      template:
        '<button :disabled="disabled" :data-loading="loading" @click="$emit(\'click\')"><slot /></button>',
    },
    'el-empty': {
      props: ['description'],
      template: '<div>{{ description }}</div>',
    },
    'el-icon': {
      template: '<i><slot /></i>',
    },
    'el-input': {
      props: ['modelValue'],
      emits: ['update:modelValue', 'keydown'],
      template:
        '<textarea :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" @keydown="$emit(\'keydown\', $event)" />',
    },
  },
}

describe('agent presentation components', () => {
  it('reports session operation intents', async () => {
    const wrapper = mount(AgentSessionPanel, {
      props: {
        sessions: [
          { id: 1, title: '第一会话' },
          { id: 2, title: '第二会话' },
        ],
        currentSessionId: 1,
      },
      global,
    })

    await wrapper.findAll('button')[0].trigger('click')
    await wrapper.findAll('.session-item')[1].trigger('click')
    const actionButtons = wrapper.find('.session-actions').findAll('button')
    await actionButtons[0].trigger('click')
    await actionButtons[1].trigger('click')

    expect(wrapper.emitted('create')).toHaveLength(1)
    expect(wrapper.emitted('select')).toEqual([[2]])
    expect(wrapper.emitted('rename')).toHaveLength(1)
    expect(wrapper.emitted('remove')).toHaveLength(1)
  })

  it('reports quick prompt and retry intents', async () => {
    const failedMessage = {
      id: 1,
      role: 'USER',
      content: '失败消息',
      clientMessageId: 'client-1',
      status: 'failed',
    }
    const wrapper = mount(AgentMessageList, {
      props: {
        messages: [failedMessage],
        quickPrompts: ['快捷问题'],
      },
      global,
    })

    await wrapper.get('.message-actions button').trigger('click')
    expect(wrapper.emitted('retry')).toEqual([[failedMessage]])

    await wrapper.setProps({ messages: [] })
    await wrapper.get('.quick-prompts button').trigger('click')
    expect(wrapper.emitted('use-prompt')).toEqual([['快捷问题']])
  })

  it('implements v-model and reports send intent', async () => {
    const wrapper = mount(AgentComposer, {
      props: { modelValue: '' },
      global,
    })

    await wrapper.get('textarea').setValue('新的问题')
    await wrapper.get('button').trigger('click')

    expect(wrapper.emitted('update:modelValue')).toEqual([['新的问题']])
    expect(wrapper.emitted('send')).toHaveLength(1)
  })
})
