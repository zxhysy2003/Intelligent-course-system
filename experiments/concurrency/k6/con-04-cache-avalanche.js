import http from 'k6/http'
import { check } from 'k6'
import exec from 'k6/execution'
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

const recommendRequests = new Counter('con04_avalanche_recommend_requests')
const upstreamRequests = new Counter('con04_avalanche_upstream_requests')
const upstreamMaxActive = new Gauge('con04_avalanche_upstream_max_active')
const unexpected = new Counter('con04_avalanche_unexpected')
const recommendDuration = new Trend('con04_avalanche_duration', true)

const mode = readMode(__ENV.CON04_AVALANCHE_MODE || 'COLD')
const baseUrls = readBaseUrls(__ENV.CON04_BASE_URLS || __ENV.CON04_BASE_URL || 'http://127.0.0.1:8080')
const stubUrl = readHttpUrl(__ENV.CON04_STUB_URL || 'http://127.0.0.1:18000', 'CON04_STUB_URL')
const userCount = readRangeInteger(__ENV.CON04_USERS || '50', 'CON04_USERS', 1, 100)
const usernamePrefix = (__ENV.CON04_USERNAME_PREFIX || 'con04_user_').trim()
const password = __ENV.CON04_PASSWORD || '123456'
const adminUsername = (__ENV.CON04_ADMIN_USERNAME || 'con04_admin').trim()
const adminPassword = __ENV.CON04_ADMIN_PASSWORD || '123456'
const providedAdminToken = (__ENV.CON04_ADMIN_TOKEN || '').trim()
const prewarmResetStartTime = '62s'
const prewarmResetStartMs = 62000
const avalancheStartTime = (__ENV.CON04_AVALANCHE_START_TIME || '100s').trim()
const expectedUpstreamMin = readNonNegativeInteger(
  __ENV.CON04_EXPECT_UPSTREAM_MIN || String(userCount),
  'CON04_EXPECT_UPSTREAM_MIN',
)
const expectedUpstreamMax = readNonNegativeInteger(
  __ENV.CON04_EXPECT_UPSTREAM_MAX || String(userCount),
  'CON04_EXPECT_UPSTREAM_MAX',
)
const expectedMaxActiveMin = readNonNegativeInteger(
  __ENV.CON04_EXPECT_MAX_ACTIVE_MIN || '2',
  'CON04_EXPECT_MAX_ACTIVE_MIN',
)
const expectedMaxActiveMax = readNonNegativeInteger(
  __ENV.CON04_EXPECT_MAX_ACTIVE_MAX || String(userCount),
  'CON04_EXPECT_MAX_ACTIVE_MAX',
)
const p95LimitMs = readPositiveInteger(__ENV.CON04_P95_LIMIT_MS || '10000', 'CON04_P95_LIMIT_MS')
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

if (!usernamePrefix || !password) {
  throw new Error('CON04_USERNAME_PREFIX 和 CON04_PASSWORD 不能为空')
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
if (
  mode === 'EXPIRE' &&
  readDurationMilliseconds(avalancheStartTime, 'CON04_AVALANCHE_START_TIME') <=
    prewarmResetStartMs + statsSettleTimeoutMs + 5000
) {
  throw new Error(
    `CON04_AVALANCHE_START_TIME 必须晚于预热收尾的最长等待窗口，当前需要大于 ${prewarmResetStartMs + statsSettleTimeoutMs + 5000}ms`,
  )
}

const avalancheScenario = {
  executor: 'per-vu-iterations',
  vus: userCount,
  iterations: 1,
  maxDuration: '30s',
  gracefulStop: '0s',
  exec: 'avalanche',
  tags: { experiment: 'CON-04', cache_scope: 'multi-key', avalanche_mode: mode },
}

export const options = {
  setupTimeout: `${Math.ceil(statsSettleTimeoutMs / 1000) + 30}s`,
  teardownTimeout: `${Math.ceil(statsSettleTimeoutMs / 1000) + 5}s`,
  scenarios:
    mode === 'EXPIRE'
      ? {
          prewarm: {
            // 预热并发不超过默认 Java 构建上限，避免预热阶段自身触发线程池拒绝，
            // 确保正式阶段观察到的是 TTL 分散效果而不是首次 miss。
            executor: 'shared-iterations',
            vus: Math.min(4, userCount),
            iterations: userCount,
            maxDuration: '60s',
            gracefulStop: '0s',
            exec: 'prewarm',
            tags: { experiment: 'CON-04', phase: 'prewarm' },
          },
          reset_stub_stats: {
            executor: 'shared-iterations',
            vus: 1,
            iterations: 1,
            startTime: prewarmResetStartTime,
            maxDuration: `${Math.ceil(statsSettleTimeoutMs / 1000) + 5}s`,
            gracefulStop: '0s',
            exec: 'resetStatsAfterPrewarm',
            tags: { experiment: 'CON-04', phase: 'reset' },
          },
          avalanche: { ...avalancheScenario, startTime: avalancheStartTime },
        }
      : { avalanche: avalancheScenario },
  thresholds: {
    checks: ['rate==1'],
    http_req_failed: ['rate==0'],
    con04_avalanche_recommend_requests: [`count==${userCount}`],
    con04_avalanche_upstream_requests: [
      `count>=${expectedUpstreamMin}`,
      `count<=${expectedUpstreamMax}`,
    ],
    con04_avalanche_upstream_max_active: [
      `value>=${expectedMaxActiveMin}`,
      `value<=${expectedMaxActiveMax}`,
    ],
    con04_avalanche_unexpected: ['count==0'],
    con04_avalanche_duration: [`p(95)<${p95LimitMs}`],
    'http_reqs{phase:load}': [`count==${userCount}`],
  },
}

export function setup() {
  const tokens = []
  for (let index = 1; index <= userCount; index += 1) {
    const username = `${usernamePrefix}${String(index).padStart(3, '0')}`
    tokens.push(login(baseUrls[0], username, password, '实验用户'))
  }
  const adminToken =
    providedAdminToken || login(baseUrls[0], adminUsername, adminPassword, '指标观测管理员')
  requireSettledState(
    waitForSettledStubStats(settleOptions, adminToken, 'setup_settle'),
    '实验开始前',
    statsSettleTimeoutMs,
    unexpected,
  )
  resetStubStats('setup')
  return { tokens, adminToken }
}

export function prewarm(data) {
  const index = scenarioIterationIndex()
  const response = requestRecommendation(index, data.tokens[index], 'prewarm')
  assertRecommendationResponse(response, '多用户预热')
}

export function resetStatsAfterPrewarm(data) {
  // 预热请求返回后，Java 线程池中仍可能有排队或执行中的构建。
  // 只有后端和 Stub 都排空后才能清零，否则预热回源会污染正式负载统计。
  requireSettledState(
    waitForSettledStubStats(settleOptions, data.adminToken, 'prewarm_settle'),
    '预热完成后',
    statsSettleTimeoutMs,
    unexpected,
  )
  resetStubStats('reset')
}

export function avalanche(data) {
  const index = scenarioIterationIndex()
  const response = requestRecommendation(index, data.tokens[index], 'load')
  recommendRequests.add(1)
  recommendDuration.add(response.timings.duration)
  const valid = assertRecommendationResponse(response, '雪崩并发推荐')
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
      `[CON-04][avalanche][${mode}] settled=${stats.settled} backendBuilds=${JSON.stringify(stats.backendStates)} stubStats=${JSON.stringify(stats.body)}`,
    )
  }
}

function scenarioIterationIndex() {
  const index = Number(exec.scenario.iterationInTest)
  if (!Number.isInteger(index) || index < 0 || index >= userCount) {
    throw new Error(`无法映射实验用户：iterationInTest=${exec.scenario.iterationInTest}`)
  }
  return index
}

function requestRecommendation(index, token, phase) {
  const backendIndex = index % baseUrls.length
  return http.get(`${baseUrls[backendIndex]}/api/v1/recommendations`, {
    headers: { Authorization: `Bearer ${token}`, Accept: 'application/json' },
    tags: {
      name: 'recommendations',
      phase,
      backend_index: String(backendIndex + 1),
      experiment_user: String(index + 1),
    },
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

function resetStubStats(phase) {
  const response = http.post(`${stubUrl}/stats/reset`, null, {
    tags: { name: 'stub_stats_reset', phase },
  })
  const valid = check(response, {
    'Stub 统计重置成功': (res) => res.status === 200,
  })
  if (!valid) {
    throw new Error(`Stub 统计重置失败：HTTP=${response.status}，phase=${phase}`)
  }
}

function readMode(value) {
  const parsed = String(value).trim().toUpperCase()
  if (!['COLD', 'EXPIRE'].includes(parsed)) {
    throw new Error(`CON04_AVALANCHE_MODE 只支持 COLD 或 EXPIRE，当前值：${value}`)
  }
  return parsed
}

function readRangeInteger(value, name, minimum, maximum) {
  const parsed = Number(value)
  if (!Number.isInteger(parsed) || parsed < minimum || parsed > maximum) {
    throw new Error(`${name} 必须是 ${minimum}-${maximum} 的整数，当前值：${value}`)
  }
  return parsed
}

function readDurationMilliseconds(value, name) {
  const text = String(value).trim()
  const pattern = /(\d+(?:\.\d+)?)(ms|s|m|h)/g
  const multipliers = { ms: 1, s: 1000, m: 60000, h: 3600000 }
  let total = 0
  let consumed = 0
  let match
  while ((match = pattern.exec(text)) !== null) {
    if (match.index !== consumed) {
      throw new Error(`${name} 必须是合法时长，当前值：${value}`)
    }
    total += Number(match[1]) * multipliers[match[2]]
    consumed = pattern.lastIndex
  }
  if (consumed !== text.length || total <= 0) {
    throw new Error(`${name} 必须是合法正时长，当前值：${value}`)
  }
  return total
}
