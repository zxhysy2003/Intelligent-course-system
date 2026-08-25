import http from 'k6/http'
import { check } from 'k6'
import { Counter } from 'k6/metrics'

const studyProcessed = new Counter('study_processed')
const studyReplayed = new Counter('study_replayed')
const studyConflict = new Counter('study_conflict')
const studyUnexpected = new Counter('study_unexpected')

const eventMode = readEventMode(__ENV.CON03_EVENT_MODE || 'SAME')
const baseUrls = readBaseUrls(
  __ENV.CON03_BASE_URLS || __ENV.CON03_BASE_URL || 'http://127.0.0.1:8080',
)
const loginBaseUrl = baseUrls[0]
const courseId = readPositiveInteger(__ENV.CON03_COURSE_ID, 'CON03_COURSE_ID')
const durationSeconds = readDuration(__ENV.CON03_DURATION || '5')
const virtualUsers = readPositiveInteger(__ENV.CON03_VUS || '100', 'CON03_VUS')
const eventIdBase = readEventIdBase(
  __ENV.CON03_EVENT_ID || `con03-${courseId}-${eventMode.toLowerCase()}`,
  eventMode,
  virtualUsers,
)
const maxDuration = (__ENV.CON03_MAX_DURATION || '30s').trim()
const providedToken = (__ENV.CON03_TOKEN || '').trim()
const username = (__ENV.CON03_USERNAME || '').trim()
const password = __ENV.CON03_PASSWORD || ''
const backendDistributionThresholds = buildBackendDistributionThresholds(baseUrls, virtualUsers)

if (!providedToken && (!username || !password)) {
  throw new Error('请提供 CON03_TOKEN，或者同时提供 CON03_USERNAME 和 CON03_PASSWORD')
}

const expectedProcessed = eventMode === 'SAME' ? 1 : eventMode === 'UNIQUE' ? virtualUsers : 0
const expectedReplayed = eventMode === 'SAME' ? virtualUsers - 1 : 0
const expectedConflict = eventMode === 'CONFLICT' ? virtualUsers : 0

export const options = {
  scenarios: {
    study_event_idempotency: {
      executor: 'per-vu-iterations',
      vus: virtualUsers,
      iterations: 1,
      maxDuration,
      gracefulStop: '0s',
      tags: {
        experiment: 'CON-03',
        event_mode: eventMode,
      },
    },
  },
  thresholds: {
    checks: ['rate==1'],
    study_processed: [`count==${expectedProcessed}`],
    study_replayed: [`count==${expectedReplayed}`],
    study_conflict: [`count==${expectedConflict}`],
    study_unexpected: ['count==0'],
    ...backendDistributionThresholds,
  },
}

/**
 * 登录只在 setup 阶段执行一次，不计入 STUDY 业务计数。
 * 也可以通过 CON03_TOKEN 直接传入已有 JWT。
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
 * SAME：所有 VU 复用同一 eventId，预期一次首次处理，其余均为回放。
 * UNIQUE：每个 VU 使用不同 eventId，预期全部首次处理。
 * CONFLICT：复用数据库中已有 eventId，但携带不同 duration，预期全部返回业务码 409。
 */
export default function (data) {
  const backendIndex = (__VU - 1) % baseUrls.length
  const targetBaseUrl = baseUrls[backendIndex]
  const eventId = buildEventId(eventIdBase, eventMode, __VU, __ITER)
  const response = http.post(
    `${targetBaseUrl}/api/v1/learning-behaviors`,
    JSON.stringify({
      eventId,
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
        name: 'study_event_idempotency',
        phase: 'load',
        event_mode: eventMode,
        backend_index: String(backendIndex + 1),
      },
    },
  )
  const body = parseJson(response)

  const isProcessed =
    response.status === 200 && body?.code === 200 && body?.data?.replayed === false
  const isReplayed =
    response.status === 200 && body?.code === 200 && body?.data?.replayed === true
  const isConflict = response.status === 200 && body?.code === 409
  const isExpected = expectedForMode(eventMode, isProcessed, isReplayed, isConflict)

  // 每次请求都为所有 Counter 写入 0 或 1，确保零值指标也会参与阈值判断。
  studyProcessed.add(isProcessed ? 1 : 0)
  studyReplayed.add(isReplayed ? 1 : 0)
  studyConflict.add(isConflict ? 1 : 0)
  studyUnexpected.add(isExpected ? 0 : 1)

  check(
    response,
    {
      '学习行为 HTTP 状态为 200': (res) => res.status === 200,
      '学习行为响应符合当前模式': () => isExpected,
    },
    { phase: 'load', event_mode: eventMode },
  )
}

function expectedForMode(mode, isProcessed, isReplayed, isConflict) {
  if (mode === 'SAME') return isProcessed || isReplayed
  if (mode === 'UNIQUE') return isProcessed
  return isConflict
}

function buildEventId(base, mode, vu, iteration) {
  return mode === 'UNIQUE' ? `${base}-${vu}-${iteration}` : base
}

function parseJson(response) {
  try {
    return response.json()
  } catch (_) {
    return null
  }
}

function readEventMode(value) {
  const mode = String(value).trim().toUpperCase()
  if (!['SAME', 'UNIQUE', 'CONFLICT'].includes(mode)) {
    throw new Error(`CON03_EVENT_MODE 只支持 SAME、UNIQUE、CONFLICT，当前值：${value}`)
  }
  return mode
}

function readEventIdBase(value, mode, vus) {
  const base = String(value).trim()
  const longestEventId = buildEventId(base, mode, vus, 0)
  const eventIdPattern = /^[A-Za-z0-9][A-Za-z0-9._:-]{0,63}$/
  if (!eventIdPattern.test(longestEventId)) {
    throw new Error(
      `CON03_EVENT_ID 生成的最长事件 ID 必须是 1-64 位合法字符串，当前值：${longestEventId}`,
    )
  }
  return base
}

function readBaseUrls(value) {
  const urls = value
    .split(',')
    .map((item) => item.trim().replace(/\/+$/, ''))
    .filter(Boolean)

  if (urls.length === 0 || urls.some((url) => !/^https?:\/\//.test(url))) {
    throw new Error('CON03_BASE_URLS 必须包含至少一个合法的 HTTP(S) 地址，多个地址使用逗号分隔')
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
  const duration = readPositiveInteger(value, 'CON03_DURATION')
  if (duration > 6 * 60 * 60) {
    throw new Error(`CON03_DURATION 不能超过后端单次上报上限 21600 秒，当前值：${value}`)
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
