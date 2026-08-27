import http from 'k6/http'
import { check } from 'k6'
import exec from 'k6/execution'
import { Counter, Gauge, Trend } from 'k6/metrics'

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
const avalancheStartTime = (__ENV.CON04_AVALANCHE_START_TIME || '70s').trim()
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
const p95LimitMs = readPositiveInteger(__ENV.CON04_P95_LIMIT_MS || '10000', 'CON04_P95_LIMIT_MS')

if (!usernamePrefix || !password) {
  throw new Error('CON04_USERNAME_PREFIX 和 CON04_PASSWORD 不能为空')
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
  scenarios:
    mode === 'EXPIRE'
      ? {
          prewarm: {
            executor: 'per-vu-iterations',
            vus: userCount,
            iterations: 1,
            maxDuration: '30s',
            gracefulStop: '0s',
            exec: 'prewarm',
            tags: { experiment: 'CON-04', phase: 'prewarm' },
          },
          reset_stub_stats: {
            executor: 'shared-iterations',
            vus: 1,
            iterations: 1,
            startTime: '10s',
            maxDuration: '10s',
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
    con04_avalanche_upstream_max_active: [`value>=${expectedMaxActiveMin}`],
    con04_avalanche_unexpected: ['count==0'],
    con04_avalanche_duration: [`p(95)<${p95LimitMs}`],
    'http_reqs{phase:load}': [`count==${userCount}`],
  },
}

export function setup() {
  const tokens = []
  for (let index = 1; index <= userCount; index += 1) {
    const username = `${usernamePrefix}${String(index).padStart(3, '0')}`
    tokens.push(login(baseUrls[0], username, password))
  }
  resetStubStats('setup')
  return { tokens }
}

export function prewarm(data) {
  const index = scenarioIterationIndex()
  const response = requestRecommendation(index, data.tokens[index], 'prewarm')
  assertRecommendationResponse(response, '多用户预热')
}

export function resetStatsAfterPrewarm() {
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
  console.log(`[CON-04][avalanche][${mode}] stubStats=${JSON.stringify(body)}`)
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

function login(baseUrl, username, loginPassword) {
  const response = http.post(
    `${baseUrl}/api/v1/auth/login`,
    JSON.stringify({ username, password: loginPassword }),
    {
      headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
      tags: { name: 'auth_login', phase: 'setup' },
    },
  )
  const body = parseJson(response)
  const valid = check(response, {
    '实验用户登录 HTTP 状态为 200': (res) => res.status === 200,
    '实验用户登录业务码为 200': () => body?.code === 200,
    '实验用户登录返回 Token': () => typeof body?.data === 'string' && body.data.length > 0,
  })
  if (!valid) {
    throw new Error(`CON-04 实验用户登录失败：username=${username}，HTTP=${response.status}`)
  }
  return body.data
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

function parseJson(response) {
  try {
    return response.json()
  } catch (_) {
    return null
  }
}

function readMode(value) {
  const parsed = String(value).trim().toUpperCase()
  if (!['COLD', 'EXPIRE'].includes(parsed)) {
    throw new Error(`CON04_AVALANCHE_MODE 只支持 COLD 或 EXPIRE，当前值：${value}`)
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

function readRangeInteger(value, name, minimum, maximum) {
  const parsed = Number(value)
  if (!Number.isInteger(parsed) || parsed < minimum || parsed > maximum) {
    throw new Error(`${name} 必须是 ${minimum}-${maximum} 的整数，当前值：${value}`)
  }
  return parsed
}
