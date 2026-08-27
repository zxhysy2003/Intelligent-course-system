import http from 'k6/http'
import { check } from 'k6'
import { Counter, Gauge, Trend } from 'k6/metrics'

const recommendRequests = new Counter('con04_recommend_requests')
const upstreamRequests = new Counter('con04_upstream_requests')
const upstreamMaxActive = new Gauge('con04_upstream_max_active')
const unexpected = new Counter('con04_unexpected')
const recommendDuration = new Trend('con04_recommend_duration', true)

const mode = readMode(__ENV.CON04_MODE || 'COLD')
const baseUrls = readBaseUrls(__ENV.CON04_BASE_URLS || __ENV.CON04_BASE_URL || 'http://127.0.0.1:8080')
const stubUrl = readHttpUrl(__ENV.CON04_STUB_URL || 'http://127.0.0.1:18000', 'CON04_STUB_URL')
const virtualUsers = readPositiveInteger(__ENV.CON04_VUS || '100', 'CON04_VUS')
const username = (__ENV.CON04_USERNAME || 'con04_user_001').trim()
const password = __ENV.CON04_PASSWORD || '123456'
const providedToken = (__ENV.CON04_TOKEN || '').trim()
const maxDuration = (__ENV.CON04_MAX_DURATION || '30s').trim()
const expectedUpstreamMin = readNonNegativeInteger(
  __ENV.CON04_EXPECT_UPSTREAM_MIN || (mode === 'WARM' ? '0' : '1'),
  'CON04_EXPECT_UPSTREAM_MIN',
)
const expectedUpstreamMax = readNonNegativeInteger(
  __ENV.CON04_EXPECT_UPSTREAM_MAX || (mode === 'WARM' ? '0' : String(virtualUsers)),
  'CON04_EXPECT_UPSTREAM_MAX',
)
const expectedMaxActiveMin = readNonNegativeInteger(
  __ENV.CON04_EXPECT_MAX_ACTIVE_MIN || (mode === 'WARM' ? '0' : '1'),
  'CON04_EXPECT_MAX_ACTIVE_MIN',
)
const expectedMaxActiveMax = readNonNegativeInteger(
  __ENV.CON04_EXPECT_MAX_ACTIVE_MAX || (mode === 'WARM' ? '0' : String(virtualUsers)),
  'CON04_EXPECT_MAX_ACTIVE_MAX',
)
const p95LimitMs = readPositiveInteger(__ENV.CON04_P95_LIMIT_MS || '5000', 'CON04_P95_LIMIT_MS')

if (!providedToken && (!username || !password)) {
  throw new Error('请提供 CON04_TOKEN，或者提供 CON04_USERNAME 和 CON04_PASSWORD')
}
if (expectedUpstreamMin > expectedUpstreamMax) {
  throw new Error('CON04_EXPECT_UPSTREAM_MIN 不能大于 CON04_EXPECT_UPSTREAM_MAX')
}
if (expectedMaxActiveMin > expectedMaxActiveMax) {
  throw new Error('CON04_EXPECT_MAX_ACTIVE_MIN 不能大于 CON04_EXPECT_MAX_ACTIVE_MAX')
}

export const options = {
  scenarios: {
    recommend_single_key: {
      executor: 'per-vu-iterations',
      vus: virtualUsers,
      iterations: 1,
      maxDuration,
      gracefulStop: '0s',
      tags: { experiment: 'CON-04', cache_scope: 'single-key', cache_mode: mode },
    },
  },
  thresholds: {
    checks: ['rate==1'],
    http_req_failed: ['rate==0'],
    con04_recommend_requests: [`count==${virtualUsers}`],
    con04_upstream_requests: [`count>=${expectedUpstreamMin}`, `count<=${expectedUpstreamMax}`],
    con04_upstream_max_active: [
      `value>=${expectedMaxActiveMin}`,
      `value<=${expectedMaxActiveMax}`,
    ],
    con04_unexpected: ['count==0'],
    con04_recommend_duration: [`p(95)<${p95LimitMs}`],
    ...buildBackendDistributionThresholds(baseUrls, virtualUsers),
  },
}

export function setup() {
  const token = providedToken || login(baseUrls[0], username, password)

  if (mode === 'WARM') {
    const warmResponse = requestRecommendation(baseUrls[0], token, 'prewarm', '1')
    assertRecommendationResponse(warmResponse, '预热推荐')
  }
  resetStubStats()
  return { token }
}

export default function (data) {
  const backendIndex = (__VU - 1) % baseUrls.length
  const response = requestRecommendation(
    baseUrls[backendIndex],
    data.token,
    'load',
    String(backendIndex + 1),
  )
  recommendRequests.add(1)
  recommendDuration.add(response.timings.duration)
  const valid = assertRecommendationResponse(response, '并发推荐')
  unexpected.add(valid ? 0 : 1)
}

export function teardown() {
  const response = http.get(`${stubUrl}/stats`, { tags: { name: 'stub_stats', phase: 'teardown' } })
  const body = parseJson(response)
  const valid = check(response, {
    'Stub 统计 HTTP 状态为 200': (res) => res.status === 200,
    'Stub 统计字段完整': () =>
      Number.isInteger(body?.requestTotal) && Number.isInteger(body?.maxActiveRequests),
  })
  if (!valid) {
    unexpected.add(1)
    return
  }
  upstreamRequests.add(body.requestTotal)
  upstreamMaxActive.add(body.maxActiveRequests)
  console.log(`[CON-04][single-key][${mode}] stubStats=${JSON.stringify(body)}`)
}

function login(baseUrl, loginUsername, loginPassword) {
  const response = http.post(
    `${baseUrl}/api/v1/auth/login`,
    JSON.stringify({ username: loginUsername, password: loginPassword }),
    {
      headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
      tags: { name: 'auth_login', phase: 'setup' },
    },
  )
  const body = parseJson(response)
  const valid = check(response, {
    '登录 HTTP 状态为 200': (res) => res.status === 200,
    '登录业务码为 200': () => body?.code === 200,
    '登录返回非空 Token': () => typeof body?.data === 'string' && body.data.length > 0,
  })
  if (!valid) {
    throw new Error(`CON-04 登录失败：username=${loginUsername}，HTTP=${response.status}`)
  }
  return body.data
}

function requestRecommendation(baseUrl, token, phase, backendIndex) {
  return http.get(`${baseUrl}/api/v1/recommendations`, {
    headers: { Authorization: `Bearer ${token}`, Accept: 'application/json' },
    tags: { name: 'recommendations', phase, backend_index: backendIndex },
  })
}

function assertRecommendationResponse(response, label) {
  const body = parseJson(response)
  return check(response, {
    [`${label} HTTP 状态为 200`]: (res) => res.status === 200,
    [`${label}业务码为 200`]: () => body?.code === 200,
    [`${label}返回 items 数组`]: () => Array.isArray(body?.data?.items),
  })
}

function resetStubStats() {
  const response = http.post(`${stubUrl}/stats/reset`, null, {
    tags: { name: 'stub_stats_reset', phase: 'setup' },
  })
  const valid = check(response, {
    'Stub 统计重置成功': (res) => res.status === 200,
  })
  if (!valid) {
    throw new Error(`Stub 统计重置失败：HTTP=${response.status}，stubUrl=${stubUrl}`)
  }
}

function parseJson(response) {
  try {
    return response.json()
  } catch (_) {
    return null
  }
}

function readMode(value) {
  const parsed = String(value).trim().toUpperCase()
  if (!['WARM', 'COLD'].includes(parsed)) {
    throw new Error(`CON04_MODE 只支持 WARM 或 COLD，当前值：${value}`)
  }
  return parsed
}

function readBaseUrls(value) {
  const urls = String(value)
    .split(',')
    .map((item) => item.trim().replace(/\/+$/, ''))
    .filter(Boolean)
  if (urls.length === 0 || urls.some((url) => !/^https?:\/\//.test(url))) {
    throw new Error('CON04_BASE_URLS 必须是逗号分隔的 HTTP(S) 地址')
  }
  return urls
}

function readHttpUrl(value, name) {
  const parsed = String(value).trim().replace(/\/+$/, '')
  if (!/^https?:\/\//.test(parsed)) {
    throw new Error(`${name} 必须是合法 HTTP(S) 地址，当前值：${value}`)
  }
  return parsed
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
    throw new Error(`${name} 必须是正整数，当前值：${value}`)
  }
  return parsed
}

function readNonNegativeInteger(value, name) {
  const parsed = Number(value)
  if (!Number.isInteger(parsed) || parsed < 0) {
    throw new Error(`${name} 必须是非负整数，当前值：${value}`)
  }
  return parsed
}
