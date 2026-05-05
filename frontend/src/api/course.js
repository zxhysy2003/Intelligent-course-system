import request from "./request";

export function GetCategories() {
    return request.get("/course/categories");
}

export function GetCourses(params) {
    return request.post("/course/list", params);
}

export function UserAttendCourse(courseId) {
    return request.get(`/course/attend/${courseId}`);
}

export function GetCourseVideo(courseId) {
    return request.get(`/course/video/${courseId}`);
}

export function GetUserCourseRelation(courseId) {
    return request.get(`/course/relation/${courseId}`);
}

export function UpdateCourseVideoProgressSeconds(data) {
    return request.post("/course/relation/updateProgressSeconds", null, { params: data });
}

export function GetCourseById(courseId) {
    return request.get(`/course/${courseId}`);
}

export function GetAdminCourseDetail(courseId) {
    return request.get(`/admin/course/detail/${courseId}`);
}

export function GetCourseByKp(kpId) {
    return request.get("/course/by-kp", {
        params: { kpId }
    });
}

export function GetKnowledgePointsByCourse(courseId) {
    return request.get("/course/by-c", {
        params: { courseId }
    });
}

export function DeleteCourses(courseIds) {
    return request.delete("/admin/course/delete", {
        data: { courseIds }
    });
}

export function UpdateCourseStatus(courseId, status) {
    return request.put(`/admin/course/status/${courseId}`, null, {
        params: { status }
    });
}

export function GetCourseRegisterOptions() {
    return request.get("/admin/course/register-options");
}

export function RegisterCourse(data) {
    return request.post("/admin/course/register", data);
}

export function UpdateCourse(data) {
    return request.put("/admin/course/update", data);
}

export function UploadCourseVideo(courseId, file) {
    const formData = new FormData();
    formData.append("file", file);
    return request.post(`/admin/course/${courseId}/video`, formData, {
        headers: { "Content-Type": "multipart/form-data" }
    });
}
