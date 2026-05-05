import request from "./request";

export function GetHybridRecommend() {
    return request.get("/recommend/hybrid");
}
