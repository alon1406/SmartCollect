/* =============================================
   Core HTTP — fetch helpers, v1.4 auth params, and the shared `api` object.
   Service files (services/*.js) attach their methods onto `api`.
   ============================================= */

async function _handleResponse(res) {
    if (!res.ok) {
        let msg = `HTTP ${res.status}`;
        try {
            const err = await res.json();
            if (err.message) msg = err.message;
        } catch (_) { }
        throw new Error(msg);
    }
    const text = await res.text();
    return text ? JSON.parse(text) : null;
}

function _headers(extra = {}) {
    return { 'API-Version': API_VER, 'Content-Type': 'application/json', ...extra };
}

// The API requires the caller's credentials as query params on every
// object/command/admin call (the password is stashed at login).
function _auth() {
    return `userSystemID=${encodeURIComponent(SYSTEM_ID)}`
        + `&userEmail=${encodeURIComponent(session.getEmail() || '')}`
        + `&userPassword=${encodeURIComponent(session.getPassword() || '')}`;
}
function _pwd() { return `userPassword=${encodeURIComponent(session.getPassword() || '')}`; }
function _page() { return `size=${PAGE_SIZE}&page=0`; }

// The single API surface. Service files do `Object.assign(api, { ... })`.
const api = {};
