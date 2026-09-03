import http from 'k6/http'
import { check } from 'k6'
import { Counter, Gauge, Trend } from 'k6/metrics'
import {
  isValidBackendState,
  isValidStubStats,
  login,
  parseJson,
  readBaseUrls,
  readHttpUrl,
  readNonNegativeInteger,
  readPositiveInteger,
  requireSettledState,
  waitForSettledStubStats,
} from './con-04-common.js'

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
const adminUsername = (__ENV.CON04_ADMIN_USERNAME || 'con04_admin').trim()
const adminPassword = __ENV.CON04_ADMIN_PASSWORD || '123456'
const providedAdminToken = (__ENV.CON04_ADMIN_TOKEN || '').trim()
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
const statsSettleTimeoutMs = readPositiveInteger(
  __ENV.CON04_STATS_SETTLE_TIMEOUT_MS || '30000',
  'CON04_STATS_SETTLE_TIMEOUT_MS',
)
const statsPollIntervalMs = readPositiveInteger(
  __ENV.CON04_STATS_POLL_INTERVAL_MS || '200',
  'CON04_STATS_POLL_INTERVAL_MS',
)
const statsStablePolls = readPositiveInteger(
  __ENV.CON04_STATS_STABLE_POLLS || '5',
  'CON04_STATS_STABLE_POLLS',
)
const settleOptions = {
  baseUrls,
  stubUrl,
  timeoutMs: statsSettleTimeoutMs,
  pollIntervalMs: statsPollIntervalMs,
  requiredStablePolls: statsStablePolls,
}

if (!providedToken && (!username || !password)) {
  throw new Error('请提供 CON04_TOKEN，或者提供 CON04_USERNAME 和 CON04_PASSWORD')
}
if (!providedAdminToken && (!adminUsername || !adminPassword)) {
  throw new Error('请提供 CON04_ADMIN_TOKEN，或者提供 CON04_ADMIN_USERNAME 和 CON04_ADMIN_PASSWORD')
}
if (expectedUpstreamMin > expectedUpstreamMax) {
  throw new Error('CON04_EXPECT_UPSTREAM_MIN 不能大于 CON04_EXPECT_UPSTREAM_MAX')
}
if (expectedMaxActiveMin > expectedMaxActiveMax) {
  throw new Error('CON04_EXPECT_MAX_ACTIVE_MIN 不能大于 CON04_EXPECT_MAX_ACTIVE_MAX')
}

export const options = {
  setupTimeout: `${Math.ceil(statsSettleTimeoutMs / 1000) + 30}s`,
  teardownTimeout: `${Math.ceil(statsSettleTimeoutMs / 1000) + 5}s`,
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
  const token = providedToken || login(baseUrls[0], username, password, '实验用户')
  const adminToken =
    providedAdminToken || login(baseUrls[0], adminUsername, adminPassword, '指标观测管理员')

  requireSettledState(
    waitForSettledStubStats(settleOptions, adminToken, 'setup_settle'),
    '实验开始前',
    statsSettleTimeoutMs,
    unexpected,
  )

  if (mode === 'WARM') {
    const warmResponse = requestRecommendation(baseUrls[0], token, 'prewarm', '1')
    assertRecommendationResponse(warmResponse, '预热推荐')
    // 接口可以先返回降级结果，而构建仍在后台继续；排空后才能将后续请求视为真正热缓存。
    requireSettledState(
      waitForSettledStubStats(settleOptions, adminToken, 'prewarm_settle'),
      '预热完成后',
      statsSettleTimeoutMs,
      unexpected,
    )
  }
  resetStubStats()
  return { token, adminToken }
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

export function teardown(data) {
  const stats = waitForSettledStubStats(settleOptions, data.adminToken, 'teardown_poll')
  const valid = check(stats, {
    '后端构建指标可读取': (result) => result.backendStates.every(isValidBackendState),
    '所有后端构建任务已排空': (result) => result.backendsSettled,
    'Stub 统计 HTTP 状态为 200': (result) => result.response?.status === 200,
    'Stub 统计字段完整': (result) => isValidStubStats(result.body),
    'Stub 后台任务已稳定': (result) => result.settled,
  })
  if (!valid) {
    unexpected.add(1)
  }
  if (isValidStubStats(stats.body)) {
    upstreamRequests.add(stats.body.requestTotal)
    upstreamMaxActive.add(stats.body.maxActiveRequests)
    console.log(
      `[CON-04][single-key][${mode}] settled=${stats.settled} backendBuilds=${JSON.stringify(stats.backendStates)} stubStats=${JSON.stringify(stats.body)}`,
    )
  }
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

function readMode(value) {
  const parsed = String(value).trim().toUpperCase()
  if (!['WARM', 'COLD'].includes(parsed)) {
    throw new Error(`CON04_MODE 只支持 WARM 或 COLD，当前值：${value}`)
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
