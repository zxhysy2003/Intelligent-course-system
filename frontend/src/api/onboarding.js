import request from "./request";

export function GetOnboardingOptions() {
    return request.get("/onboarding/options");
}

export function GetOnboardingStatus() {
    return request.get("/onboarding/status");
}

export function SubmitOnboarding(data) {
    return request.post("/onboarding/submit", data);
}
