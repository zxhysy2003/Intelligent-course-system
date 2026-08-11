import request from "./request";

export function getHybridRecommend() {
    return request.get("/recommend/hybrid");
}
