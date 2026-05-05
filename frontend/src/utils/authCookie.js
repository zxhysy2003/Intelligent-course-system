const COOKIE_NAME = 'auth_token';

export function setAuthTokenToCookie(token, minutes) {
    const expires = new Date(Date.now() + minutes * 60 * 1000).toUTCString();
    document.cookie = `${COOKIE_NAME}=${token}; expires=${expires}; path=/`;
}

export function clearAuthTokenCookie() {
    document.cookie = `${COOKIE_NAME}=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/`;
}