import request from './request'

export function login(data) {
  return request.post('/auth/login', data)
}

export function getProfile() {
  return request.get('/users/me')
}

export function registerUser(data) {
  return request.post('/auth/register', data)
}

export function getAdminUsers(params) {
  return request.post('/admin/users/search', params)
}

export function updateAdminUserRole(userId, role) {
  return request.patch(`/admin/users/${userId}/role`, { role })
}

export function updateAdminUserStatus(userId, status) {
  return request.patch(`/admin/users/${userId}/status`, { status })
}

export function deleteAdminUsers(userIds) {
  return request.delete('/admin/users', {
    data: { userIds },
  })
}

export function getAdminUserDetail(userId) {
  return request.get(`/admin/users/${userId}`)
}

export function updateAdminUser(userId, data) {
  return request.put(`/admin/users/${userId}`, data)
}
