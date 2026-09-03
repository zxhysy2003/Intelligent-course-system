import http from 'k6/http'
import { check, sleep } from 'k6'

export function waitForSettledStubStats(options, adminToken, phase) {
  const { baseUrls, stubUrl, timeoutMs, pollIntervalMs, requiredStablePolls } = options
  const deadline = Date.now() + timeoutMs
  let lastResponse = null
  let lastValidBody = null
  let backendStates = []
  let stableRequestTotal = null
  let stablePollCount = 0

  while (Date.now() <= deadline) {
    backendStates = readBackendBuildStates(baseUrls, adminToken, phase, deadline, pollIntervalMs)
    lastResponse = http.get(`${stubUrl}/stats`, {
      tags: { name: 'stub_stats', phase },
      timeout: remainingPollTimeout(deadline, pollIntervalMs),
    })
    const currentBody = parseJson(lastResponse)
    if (lastResponse.status === 200 && isValidStubStats(currentBody)) {
      lastValidBody = currentBody
    }

    const backendsSettled =
      backendStates.length === baseUrls.length &&
      backendStates.every((state) => isValidBackendState(state) && state.inFlight === 0)
    const idle =
      backendsSettled &&
      lastResponse.status === 200 &&
      isValidStubStats(currentBody) &&
      currentBody.activeRequests === 0
    if (idle) {
      if (currentBody.requestTotal === stableRequestTotal) {
        stablePollCount += 1
      } else {
        stableRequestTotal = currentBody.requestTotal
        stablePollCount = 1
      }
      if (stablePollCount >= requiredStablePolls) {
        return { response: lastResponse, body: currentBody, backendStates, backendsSettled, settled: true }
      }
    } else {
      stableRequestTotal = null
      stablePollCount = 0
    }
    sleep(pollIntervalMs / 1000)
  }

  const backendsSettled =
    backendStates.length === baseUrls.length &&
    backendStates.every((state) => isValidBackendState(state) && state.inFlight === 0)
  return { response: lastResponse, body: lastValidBody, backendStates, backendsSettled, settled: false }
}

export function requireSettledState(stats, label, timeoutMs, unexpected) {
  const valid = check(stats, {
    [`${label}后端构建指标可读取`]: (result) =>
      result.backendStates.every(isValidBackendState),
    [`${label}所有后端构建任务已排空`]: (result) => result.backendsSettled,
    [`${label} Stub 请求已排空`]: (result) => result.settled,
  })
  if (!valid) {
    unexpected.add(1)
    throw new Error(
      `${label}后台任务未在 ${timeoutMs}ms 内排空：backendBuilds=${JSON.stringify(stats.backendStates)} stubStats=${JSON.stringify(stats.body)}`,
    )
  }
}

function readBackendBuildStates(baseUrls, adminToken, phase, deadline, pollIntervalMs) {
  return baseUrls.map((baseUrl, index) => {
    const response = http.get(`${baseUrl}/actuator/metrics/recommend.cache.build.inflight`, {
      headers: { Authorization: `Bearer ${adminToken}`, Accept: 'application/json' },
      tags: {
        name: 'recommend_build_inflight',
        phase,
        backend_index: String(index + 1),
      },
      timeout: remainingPollTimeout(deadline, pollIntervalMs),
    })
    const body = parseJson(response)
    const measurement = Array.isArray(body?.measurements)
      ? body.measurements.find((item) => item?.statistic === 'VALUE')
      : null
    return { baseUrl, status: response.status, inFlight: measurement?.value }
  })
}

function remainingPollTimeout(deadline, pollIntervalMs) {
  const remainingMs = Math.max(1, deadline - Date.now())
  const perRequestCapMs = Math.max(1000, pollIntervalMs)
  return `${Math.min(remainingMs, perRequestCapMs)}ms`
}

export function isValidBackendState(state) {
  return state?.status === 200 && Number.isFinite(state?.inFlight) && state.inFlight >= 0
}

export function isValidStubStats(body) {
  return (
    Number.isInteger(body?.requestTotal) &&
    Number.isInteger(body?.activeRequests) &&
    Number.isInteger(body?.maxActiveRequests)
  )
}

export function login(baseUrl, username, password, label) {
  const response = http.post(
    `${baseUrl}/api/v1/auth/login`,
    JSON.stringify({ username, password }),
    {
      headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
      tags: { name: 'auth_login', phase: 'setup' },
    },
  )
  const body = parseJson(response)
  const valid = check(response, {
    [`${label}登录 HTTP 状态为 200`]: (res) => res.status === 200,
    [`${label}登录业务码为 200`]: () => body?.code === 200,
    [`${label}登录返回 Token`]: () => typeof body?.data === 'string' && body.data.length > 0,
  })
  if (!valid) {
    throw new Error(`CON-04 ${label}登录失败：username=${username}，HTTP=${response.status}`)
  }
  return body.data
}

export function parseJson(response) {
  try {
    return response.json()
  } catch (_) {
    return null
  }
}

export function readBaseUrls(value) {
  const urls = String(value)
    .split(',')
    .map((item) => item.trim().replace(/\/+$/, ''))
    .filter(Boolean)
  if (urls.length === 0 || urls.some((url) => !/^https?:\/\//.test(url))) {
    throw new Error('CON04_BASE_URLS 必须是逗号分隔的 HTTP(S) 地址')
  }
  return urls
}

export function readHttpUrl(value, name) {
  const parsed = String(value).trim().replace(/\/+$/, '')
  if (!/^https?:\/\//.test(parsed)) {
    throw new Error(`${name} 必须是合法 HTTP(S) 地址，当前值：${value}`)
  }
  return parsed
}

export function readPositiveInteger(value, name) {
  const parsed = Number(value)
  if (!Number.isInteger(parsed) || parsed <= 0) {
    throw new Error(`${name} 必须是正整数，当前值：${value}`)
  }
  return parsed
}

export function readNonNegativeInteger(value, name) {
  const parsed = Number(value)
  if (!Number.isInteger(parsed) || parsed < 0) {
    throw new Error(`${name} 必须是非负整数，当前值：${value}`)
  }
  return parsed
}
