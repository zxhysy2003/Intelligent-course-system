import request from "./request";

export function login(data) {
    return request.post("/user/login", data);
}

export function getProfile() {
    return request.get("/user/profile");
}

export function registerUser(data) {
    return request.post("/user/register", data);
}

export function getAdminUsers(params) {
    return request.post("/admin/user/list", params);
}

export function updateAdminUserRole(userId, role) {
    return request.put(`/admin/user/role/${userId}`, null, {
        params: { role }
    });
}

export function updateAdminUserStatus(userId, status) {
    return request.put(`/admin/user/status/${userId}`, null, {
        params: { status }
    });
}

export function deleteAdminUsers(userIds) {
    return request.delete("/admin/user/delete", {
        data: { userIds }
    });
}

export function getAdminUserDetail(userId) {
    return request.get(`/admin/user/detail/${userId}`);
}

export function updateAdminUser(data) {
    return request.put("/admin/user/update", data);
}
