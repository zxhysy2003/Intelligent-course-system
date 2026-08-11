import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import CourseMediaPlayer from '../components/CourseMediaPlayer.vue'

const setCurrentTime = (video, value) => {
  Object.defineProperty(video, 'currentTime', {
    configurable: true,
    writable: true,
    value,
  })
}

describe('CourseMediaPlayer', () => {
  it('应用起播时间并向父组件上报播放快照', async () => {
    const wrapper = mount(CourseMediaPlayer, {
      props: { videoUrl: '/videos/course.mp4', startTime: 12 },
    })
    const video = wrapper.get('video').element
    setCurrentTime(video, 0)

    await wrapper.get('video').trigger('loadedmetadata')
    expect(video.currentTime).toBe(12)
    expect(wrapper.emitted('ready')).toHaveLength(1)

    await wrapper.get('video').trigger('play')
    video.currentTime = 13.5
    await wrapper.get('video').trigger('timeupdate')

    expect(wrapper.emitted('progress').at(-1)[0]).toEqual({
      currentTime: 13.5,
      watchedSeconds: 1.5,
    })
  })

  it('不把拖拽造成的大跨度时间变化计入观看时长', async () => {
    const wrapper = mount(CourseMediaPlayer, {
      props: { videoUrl: '/videos/course.mp4' },
    })
    const video = wrapper.get('video').element
    setCurrentTime(video, 0)

    await wrapper.get('video').trigger('play')
    video.currentTime = 5
    await wrapper.get('video').trigger('timeupdate')

    expect(wrapper.emitted('progress').at(-1)[0]).toEqual({
      currentTime: 5,
      watchedSeconds: 0,
    })
  })

  it('用户开始播放后不再用延迟返回的断点覆盖当前位置', async () => {
    const wrapper = mount(CourseMediaPlayer, {
      props: { videoUrl: '/videos/course.mp4', startTime: 0 },
    })
    const video = wrapper.get('video').element
    setCurrentTime(video, 0)

    await wrapper.get('video').trigger('loadedmetadata')
    video.currentTime = 25
    await wrapper.get('video').trigger('play')
    await wrapper.setProps({ startTime: 10 })

    expect(video.currentTime).toBe(25)
  })

  it('首次播放前仍会应用延迟返回的断点', async () => {
    const wrapper = mount(CourseMediaPlayer, {
      props: { videoUrl: '/videos/course.mp4', startTime: 0 },
    })
    const video = wrapper.get('video').element
    setCurrentTime(video, 0)

    await wrapper.get('video').trigger('loadedmetadata')
    await wrapper.setProps({ startTime: 10 })

    expect(video.currentTime).toBe(10)
  })

  it('切换视频时重置快照，并透传加载错误', async () => {
    const wrapper = mount(CourseMediaPlayer, {
      props: { videoUrl: '/videos/first.mp4' },
    })

    await wrapper.setProps({ videoUrl: '/videos/second.mp4' })
    expect(wrapper.emitted('progress').at(-1)[0]).toEqual({
      currentTime: 0,
      watchedSeconds: 0,
    })

    await wrapper.get('video').trigger('error')
    expect(wrapper.emitted('error')).toHaveLength(1)
  })

  it('每个视频只在元数据首次就绪时上报 ready', async () => {
    const wrapper = mount(CourseMediaPlayer, {
      props: { videoUrl: '/videos/first.mp4' },
    })

    await wrapper.get('video').trigger('loadedmetadata')
    await wrapper.get('video').trigger('loadedmetadata')
    expect(wrapper.emitted('ready')).toHaveLength(1)

    await wrapper.setProps({ videoUrl: '/videos/second.mp4' })
    await wrapper.get('video').trigger('loadedmetadata')
    expect(wrapper.emitted('ready')).toHaveLength(2)
  })

  it('卸载前上报最终播放位置', async () => {
    const wrapper = mount(CourseMediaPlayer, {
      props: { videoUrl: '/videos/course.mp4' },
    })
    const video = wrapper.get('video').element
    setCurrentTime(video, 8)

    wrapper.unmount()

    expect(wrapper.emitted('progress').at(-1)[0].currentTime).toBe(8)
  })
})
