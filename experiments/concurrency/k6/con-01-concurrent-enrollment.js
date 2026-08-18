import http from 'k6/http'
import { check } from 'k6'
import { Counter } from 'k6/metrics'

const enrollmentSuccess = new Counter('enrollment_success')
const enrollmentDuplicate = new Counter('enrollment_duplicate')
const enrollmentUnexpected = new Counter('enrollment_unexpected')

const baseUrls = readBaseUrls(
  __ENV.CON01_BASE_URLS || __ENV.CON01_BASE_URL || 'http://127.0.0.1:8080',
)
const loginBaseUrl = baseUrls[0]
const courseId = readPositiveInteger(__ENV.CON01_COURSE_ID, 'CON01_COURSE_ID')
const virtualUsers = readPositiveInteger(__ENV.CON01_VUS || '50', 'CON01_VUS')
const maxDuration = (__ENV.CON01_MAX_DURATION || '30s').trim()
const providedToken = (__ENV.CON01_TOKEN || '').trim()
const username = (__ENV.CON01_USERNAME || '').trim()
const password = __ENV.CON01_PASSWORD || ''
const backendDistributionThresholds = buildBackendDistributionThresholds(baseUrls, virtualUsers)

if (!providedToken && (!username || !password)) {
  throw new Error('请提供 CON01_TOKEN，或者同时提供 CON01_USERNAME 和 CON01_PASSWORD')
}

export const options = {
  scenarios: {
    concurrent_enrollment: {
      executor: 'per-vu-iterations',
      vus: virtualUsers,
      iterations: 1,
      maxDuration,
      gracefulStop: '0s',
      tags: {
        experiment: 'CON-01',
      },
    },
  },
  thresholds: {
    checks: ['rate==1'],
    enrollment_success: ['count==1'],
    enrollment_duplicate: [`count==${virtualUsers - 1}`],
    enrollment_unexpected: ['count==0'],
    ...backendDistributionThresholds,
  },
}

/**
 * 登录只在 setup 阶段执行一次，不计入选课业务计数。
 * 也可以通过 CON01_TOKEN 直接传入已有 JWT，从而跳过登录。
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
 * 所有 VU 使用同一用户和同一课程，每个 VU 只执行一次选课。
 * 脚本要求运行前不存在该用户课程关系：预期一个成功，其余均为重复选课。
 */
export default function (data) {
  const backendIndex = (__VU - 1) % baseUrls.length
  const targetBaseUrl = baseUrls[backendIndex]
  const response = http.post(
    `${targetBaseUrl}/api/v1/courses/${courseId}/enrollment`,
    null,
    {
      headers: {
        Authorization: `Bearer ${data.token}`,
        Accept: 'application/json',
      },
      tags: {
        name: 'concurrent_enrollment',
        phase: 'load',
        backend_index: String(backendIndex + 1),
      },
    },
  )
  const body = parseJson(response)

  const isSuccess = response.status === 200 && body?.code === 200 && body?.data === true
  const isDuplicate = response.status === 200 && body?.code === 400
  const isExpected = isSuccess || isDuplicate

  // 每次请求都写入 0 或 1，确保计数器即使为零也会出现在阈值和汇总中。
  enrollmentSuccess.add(isSuccess ? 1 : 0)
  enrollmentDuplicate.add(isDuplicate ? 1 : 0)
  enrollmentUnexpected.add(isExpected ? 0 : 1)

  check(
    response,
    {
      '选课 HTTP 状态为 200': (res) => res.status === 200,
      '选课响应是预期业务结果': () => isExpected,
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
    throw new Error('CON01_BASE_URLS 必须包含至少一个合法的 HTTP(S) 地址，多个地址使用逗号分隔')
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

function readPositiveInteger(value, name) {
  const parsed = Number(value)
  if (!Number.isInteger(parsed) || parsed <= 0) {
    throw new Error(`${name} 必须是正整数，当前值：${value || '<empty>'}`)
  }
  return parsed
}
