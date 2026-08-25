import http from 'k6/http'
import { check } from 'k6'
import { Counter } from 'k6/metrics'

const studySuccess = new Counter('study_success')
const studyUnexpected = new Counter('study_unexpected')

const baseUrls = readBaseUrls(
  __ENV.CON02_BASE_URLS || __ENV.CON02_BASE_URL || 'http://127.0.0.1:8080',
)
const loginBaseUrl = baseUrls[0]
const courseId = readPositiveInteger(__ENV.CON02_COURSE_ID, 'CON02_COURSE_ID')
const durationSeconds = readDuration(__ENV.CON02_DURATION || '5')
const virtualUsers = readPositiveInteger(__ENV.CON02_VUS || '100', 'CON02_VUS')
const maxDuration = (__ENV.CON02_MAX_DURATION || '30s').trim()
const providedToken = (__ENV.CON02_TOKEN || '').trim()
const username = (__ENV.CON02_USERNAME || '').trim()
const password = __ENV.CON02_PASSWORD || ''
const backendDistributionThresholds = buildBackendDistributionThresholds(baseUrls, virtualUsers)

if (!providedToken && (!username || !password)) {
  throw new Error('请提供 CON02_TOKEN，或者同时提供 CON02_USERNAME 和 CON02_PASSWORD')
}

export const options = {
  scenarios: {
    concurrent_study_progress: {
      executor: 'per-vu-iterations',
      vus: virtualUsers,
      iterations: 1,
      maxDuration,
      gracefulStop: '0s',
      tags: {
        experiment: 'CON-02',
      },
    },
  },
  thresholds: {
    checks: ['rate==1'],
    study_success: [`count==${virtualUsers}`],
    study_unexpected: ['count==0'],
    ...backendDistributionThresholds,
  },
}

/**
 * 登录只在 setup 阶段执行一次，不计入学习进度请求数。
 * 也可以通过 CON02_TOKEN 直接传入已有 JWT。
 */
export function setup() {
  if (providedToken) {
    return { token: providedToken }
  }

  const response = http.post(
    `${loginBaseUrl}/api/v1/auth/login`,
    JSON.stringify({ username, password }),
    {
      headers: {
        'Content-Type': 'application/json',
        Accept: 'application/json',
      },
      tags: {
        name: 'auth_login',
        phase: 'setup',
      },
    },
  )
  const body = parseJson(response)
  const loginSucceeded = check(
    response,
    {
      '登录 HTTP 状态为 200': (res) => res.status === 200,
      '登录业务码为 200': () => body?.code === 200,
      '登录返回非空 Token': () => typeof body?.data === 'string' && body.data.length > 0,
    },
    { phase: 'setup' },
  )

  if (!loginSucceeded) {
    throw new Error(
      `登录失败：HTTP ${response.status}，业务码 ${body?.code ?? '无法解析'}，` +
        `username=${username}，baseUrl=${loginBaseUrl}`,
    )
  }

  return { token: body.data }
}

/**
 * 每个 VU 上报一段独立的 STUDY 行为，并使用不同 eventId。
 * CON-02 验证数据库原子累加与首次完课门闩；相同 eventId 的并发回放由 CON-03 验证。
 */
export default function (data) {
  const backendIndex = (__VU - 1) % baseUrls.length
  const targetBaseUrl = baseUrls[backendIndex]
  const response = http.post(
    `${targetBaseUrl}/api/v1/learning-behaviors`,
    JSON.stringify({
      eventId: `con02-${courseId}-${__VU}-${__ITER}`,
      courseId,
      behaviorType: 'STUDY',
      duration: durationSeconds,
    }),
    {
      headers: {
        Authorization: `Bearer ${data.token}`,
        'Content-Type': 'application/json',
        Accept: 'application/json',
      },
      tags: {
        name: 'concurrent_study_progress',
        phase: 'load',
        backend_index: String(backendIndex + 1),
      },
    },
  )
  const body = parseJson(response)
  const isSuccess =
    response.status === 200 && body?.code === 200 && body?.data?.replayed === false

  // 每次请求都写入 0 或 1，让零值指标也稳定出现在阈值和结果汇总中。
  studySuccess.add(isSuccess ? 1 : 0)
  studyUnexpected.add(isSuccess ? 0 : 1)

  check(
    response,
    {
      '学习行为 HTTP 状态为 200': (res) => res.status === 200,
      '学习行为业务码为 200': () => body?.code === 200,
      '学习行为为首次处理': () => body?.data?.replayed === false,
    },
    { phase: 'load' },
  )
}

function parseJson(response) {
  try {
    return response.json()
  } catch (_) {
    return null
  }
}

function readBaseUrls(value) {
  const urls = value
    .split(',')
    .map((item) => item.trim().replace(/\/+$/, ''))
    .filter(Boolean)

  if (urls.length === 0 || urls.some((url) => !/^https?:\/\//.test(url))) {
    throw new Error('CON02_BASE_URLS 必须包含至少一个合法的 HTTP(S) 地址，多个地址使用逗号分隔')
  }
  return urls
}

function buildBackendDistributionThresholds(urls, vus) {
  return Object.fromEntries(
    urls.map((_, index) => {
      const expectedCount = index >= vus ? 0 : Math.floor((vus - 1 - index) / urls.length) + 1
      return [`http_reqs{phase:load,backend_index:${index + 1}}`, [`count==${expectedCount}`]]
    }),
  )
}

function readDuration(value) {
  const duration = readPositiveInteger(value, 'CON02_DURATION')
  if (duration > 6 * 60 * 60) {
    throw new Error(`CON02_DURATION 不能超过后端单次上报上限 21600 秒，当前值：${value}`)
  }
  return duration
}

function readPositiveInteger(value, name) {
  const parsed = Number(value)
  if (!Number.isInteger(parsed) || parsed <= 0) {
    throw new Error(`${name} 必须是正整数，当前值：${value || '<empty>'}`)
  }
  return parsed
}
