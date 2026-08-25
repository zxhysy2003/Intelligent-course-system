import http from 'k6/http'
import { check } from 'k6'
import { Counter } from 'k6/metrics'

const expectedFailure = new Counter('rollback_expected_failure')
const retryProcessed = new Counter('rollback_retry_processed')
const retryReplayed = new Counter('rollback_retry_replayed')
const unexpectedResult = new Counter('rollback_unexpected')

const phase = readPhase(__ENV.CON03_ROLLBACK_PHASE || 'FAIL')
const baseUrl = readBaseUrl(__ENV.CON03_ROLLBACK_BASE_URL || 'http://127.0.0.1:8080')
const courseId = readPositiveInteger(__ENV.CON03_ROLLBACK_COURSE_ID || '14', 'CON03_ROLLBACK_COURSE_ID')
const duration = readPositiveInteger(__ENV.CON03_ROLLBACK_DURATION || '5', 'CON03_ROLLBACK_DURATION')
const eventId = readEventId(__ENV.CON03_ROLLBACK_EVENT_ID || 'con03-rollback-event')
const username = (__ENV.CON03_ROLLBACK_USERNAME || '').trim()
const password = __ENV.CON03_ROLLBACK_PASSWORD || ''
const providedToken = (__ENV.CON03_ROLLBACK_TOKEN || '').trim()

if (duration > 21600) {
  throw new Error(`CON03_ROLLBACK_DURATION 不能超过 21600 秒，当前值：${duration}`)
}
if (!providedToken && (!username || !password)) {
  throw new Error(
    '请提供 CON03_ROLLBACK_TOKEN，或者同时提供 CON03_ROLLBACK_USERNAME 和 CON03_ROLLBACK_PASSWORD',
  )
}

const failPhase = phase === 'FAIL'
const attemptThresholds = failPhase
  ? { 'http_reqs{phase:load,attempt:failure}': ['count==1'] }
  : {
      'http_reqs{phase:load,attempt:retry}': ['count==1'],
      'http_reqs{phase:load,attempt:replay}': ['count==1'],
    }

export const options = {
  scenarios: {
    study_event_rollback_retry: {
      executor: 'per-vu-iterations',
      vus: 1,
      iterations: 1,
      maxDuration: '30s',
      gracefulStop: '0s',
      tags: {
        experiment: 'CON-03-ROLLBACK',
        rollback_phase: phase,
      },
    },
  },
  thresholds: {
    checks: ['rate==1'],
    http_req_failed: ['rate==0'],
    rollback_expected_failure: [`count==${failPhase ? 1 : 0}`],
    rollback_retry_processed: [`count==${failPhase ? 0 : 1}`],
    rollback_retry_replayed: [`count==${failPhase ? 0 : 1}`],
    rollback_unexpected: ['count==0'],
    ...attemptThresholds,
  },
}

export function setup() {
  if (providedToken) {
    return { token: providedToken }
  }

  const response = http.post(
    `${baseUrl}/api/v1/auth/login`,
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
  const loginSucceeded = check(response, {
    '登录 HTTP 状态为 200': (res) => res.status === 200,
    '登录业务码为 200': () => body?.code === 200,
    '登录返回非空 Token': () => typeof body?.data === 'string' && body.data.length > 0,
  })

  if (!loginSucceeded) {
    throw new Error(`登录失败：HTTP ${response.status}，业务码 ${body?.code ?? '无法解析'}`)
  }

  return { token: body.data }
}

export default function (data) {
  if (failPhase) {
    assertInjectedFailure(data.token)
    return
  }

  assertFirstSuccessfulRetry(data.token)
  assertIdempotentReplay(data.token)
}

function assertInjectedFailure(token) {
  const response = postStudy(token, 'failure', 500)
  const passed = check(response, {
    '故障注入请求返回 HTTP 500': (res) => res.status === 500,
  })

  expectedFailure.add(passed ? 1 : 0)
  retryProcessed.add(0)
  retryReplayed.add(0)
  unexpectedResult.add(passed ? 0 : 1)
}

function assertFirstSuccessfulRetry(token) {
  const response = postStudy(token, 'retry', 200)
  const body = parseJson(response)
  const passed = check(response, {
    '事务回滚后重试返回 HTTP 200': (res) => res.status === 200,
    '事务回滚后重试业务码为 200': () => body?.code === 200,
    '事务回滚后重试是首次处理': () => body?.data?.replayed === false,
  })

  expectedFailure.add(0)
  retryProcessed.add(passed ? 1 : 0)
  retryReplayed.add(0)
  unexpectedResult.add(passed ? 0 : 1)
}

function assertIdempotentReplay(token) {
  const response = postStudy(token, 'replay', 200)
  const body = parseJson(response)
  const passed = check(response, {
    '已提交事件再次请求返回 HTTP 200': (res) => res.status === 200,
    '已提交事件再次请求业务码为 200': () => body?.code === 200,
    '已提交事件再次请求是幂等回放': () => body?.data?.replayed === true,
  })

  expectedFailure.add(0)
  retryProcessed.add(0)
  retryReplayed.add(passed ? 1 : 0)
  unexpectedResult.add(passed ? 0 : 1)
}

function postStudy(token, attempt, expectedStatus) {
  return http.post(
    `${baseUrl}/api/v1/learning-behaviors`,
    JSON.stringify({
      eventId,
      courseId,
      behaviorType: 'STUDY',
      duration,
    }),
    {
      headers: {
        Authorization: `Bearer ${token}`,
        'Content-Type': 'application/json',
        Accept: 'application/json',
      },
      responseCallback: http.expectedStatuses(expectedStatus),
      tags: {
        name: 'study_event_rollback_retry',
        phase: 'load',
        attempt,
      },
    },
  )
}

function parseJson(response) {
  try {
    return response.json()
  } catch (_) {
    return null
  }
}

function readPhase(value) {
  const normalized = String(value).trim().toUpperCase()
  if (!['FAIL', 'RECOVER'].includes(normalized)) {
    throw new Error(`CON03_ROLLBACK_PHASE 只支持 FAIL 或 RECOVER，当前值：${value}`)
  }
  return normalized
}

function readBaseUrl(value) {
  const url = String(value).trim().replace(/\/+$/, '')
  if (!/^https?:\/\//.test(url)) {
    throw new Error(`CON03_ROLLBACK_BASE_URL 必须是 HTTP(S) 地址，当前值：${value}`)
  }
  return url
}

function readEventId(value) {
  const normalized = String(value).trim()
  if (!/^[A-Za-z0-9][A-Za-z0-9._:-]{0,63}$/.test(normalized)) {
    throw new Error(`CON03_ROLLBACK_EVENT_ID 必须是 1-64 位合法字符串，当前值：${value}`)
  }
  return normalized
}

function readPositiveInteger(value, name) {
  const parsed = Number(value)
  if (!Number.isInteger(parsed) || parsed <= 0) {
    throw new Error(`${name} 必须是正整数，当前值：${value}`)
  }
  return parsed
}
