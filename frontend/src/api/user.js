import request from "./request";

export function login(data) {
    return request.post("/user/login", data);
}

export function getProfile() {
    return request.get("/user/profile");
}

export function Register(data) {
    return request.post("/user/register", data);
}

export function GetAdminUsers(params) {
    return request.post("/admin/user/list", params);
}

export function UpdateAdminUserRole(userId, role) {
    return request.put(`/admin/user/role/${userId}`, null, {
        params: { role }
    });
}

export function UpdateAdminUserStatus(userId, status) {
    return request.put(`/admin/user/status/${userId}`, null, {
        params: { status }
    });
}

export function DeleteAdminUsers(userIds) {
    return request.delete("/admin/user/delete", {
        data: { userIds }
    });
}

export function GetAdminUserDetail(userId) {
    return request.get(`/admin/user/detail/${userId}`);
}

export function UpdateAdminUser(data) {
    return request.put("/admin/user/update", data);
}
