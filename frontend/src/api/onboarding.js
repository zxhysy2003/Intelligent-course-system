import request from './request'

export function getOnboardingOptions() {
  return request.get('/onboarding/options')
}

export function getOnboardingStatus() {
  return request.get('/onboarding/status')
}

export function submitOnboarding(data) {
  return request.put('/onboarding/profile', data)
}
