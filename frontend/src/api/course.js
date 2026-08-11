import request from "./request";

export function getCategories() {
    return request.get("/course/categories");
}

export function getCourses(params) {
    return request.post("/course/list", params);
}

export function enrollCourse(courseId) {
    return request.get(`/course/attend/${courseId}`);
}

export function getCourseVideo(courseId) {
    return request.get(`/course/video/${courseId}`);
}

export function getUserCourseRelation(courseId) {
    return request.get(`/course/relation/${courseId}`);
}

export function updateCourseVideoProgressSeconds(data) {
    return request.post("/course/relation/updateProgressSeconds", null, { params: data });
}

export function getCourseById(courseId) {
    return request.get(`/course/${courseId}`);
}

export function getAdminCourseDetail(courseId) {
    return request.get(`/admin/course/detail/${courseId}`);
}

export function getCoursesByKnowledgePoint(kpId) {
    return request.get("/course/by-kp", {
        params: { kpId }
    });
}

export function getCourseKnowledgePoints(courseId) {
    return request.get("/course/by-c", {
        params: { courseId }
    });
}

export function deleteCourses(courseIds) {
    return request.delete("/admin/course/delete", {
        data: { courseIds }
    });
}

export function updateCourseStatus(courseId, status) {
    return request.put(`/admin/course/status/${courseId}`, null, {
        params: { status }
    });
}

export function getCourseRegisterOptions() {
    return request.get("/admin/course/register-options");
}

export function registerCourse(data) {
    return request.post("/admin/course/register", data);
}

export function updateCourse(data) {
    return request.put("/admin/course/update", data);
}

export function uploadCourseVideo(courseId, file) {
    const formData = new FormData();
    formData.append("file", file);
    return request.post(`/admin/course/${courseId}/video`, formData, {
        headers: { "Content-Type": "multipart/form-data" }
    });
}
