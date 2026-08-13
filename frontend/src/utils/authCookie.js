const COOKIE_NAME = 'auth_token';
const VIDEO_COOKIE_PATH = '/videos';
const LEGACY_COOKIE_PATH = '/';
const EXPIRED_AT = 'Thu, 01 Jan 1970 00:00:00 GMT';

function secureAttribute() {
    return window.location.protocol === 'https:' ? '; Secure' : '';
}

function expireCookie(path) {
    document.cookie = `${COOKIE_NAME}=; expires=${EXPIRED_AT}; path=${path}; SameSite=Strict${secureAttribute()}`;
}

export function setAuthTokenToCookie(token, minutes) {
    expireCookie(LEGACY_COOKIE_PATH);
    const expires = new Date(Date.now() + minutes * 60 * 1000).toUTCString();
    document.cookie = `${COOKIE_NAME}=${token}; expires=${expires}; path=${VIDEO_COOKIE_PATH}; SameSite=Strict${secureAttribute()}`;
}

export function clearAuthTokenCookie() {
    expireCookie(VIDEO_COOKIE_PATH);
    expireCookie(LEGACY_COOKIE_PATH);
}
