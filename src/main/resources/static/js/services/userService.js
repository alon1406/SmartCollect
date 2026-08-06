/* =============================================
   User & auth service — /users and /admin/users
   ============================================= */

Object.assign(api, {

    /**
     * Login — returns UserBoundary:
     * { userId: { email, systemID }, role, username, avatar }
     */
    login: async (email, password) => {
        const res = await fetch(
            `${API_BASE}/users/login/${SYSTEM_ID}/${encodeURIComponent(email)}?password=${encodeURIComponent(password)}`,
            { headers: _headers() }
        );
        const user = await _handleResponse(res);
        // v1.4 needs the caller's password on later calls — keep it for this session only.
        if (user) sessionStorage.setItem('sc_pwd', password);
        return user;
    },

    /**
     * Create user — body: UserWithPasswordBoundary
     * { email, password, role, username, avatar }
     * role must be: ADMIN | OPERATOR | END_USER
     * password: min 5 chars, 1 digit, 1 special char
     */
    createUser: async (payload) => {
        const res = await fetch(`${API_BASE}/users`, {
            method: 'POST',
            headers: _headers(),
            body: JSON.stringify(payload)
        });
        return _handleResponse(res);
    },

    /**
     * Update user — requires currentPassword for auth
     * Updatable: role, username, avatar, password
     */
    updateUser: async (email, currentPassword, update) => {
        const res = await fetch(
            `${API_BASE}/users/${SYSTEM_ID}/${encodeURIComponent(email)}?password=${encodeURIComponent(currentPassword)}`,
            {
                method: 'PUT',
                headers: _headers(),
                body: JSON.stringify(update)
            }
        );
        return _handleResponse(res);
    },

    /** Get all users — returns UserBoundary[] (admin endpoint) */
    getAllUsers: async () => {
        const res = await fetch(`${API_BASE}/admin/users?${_auth()}&${_page()}`, { headers: _headers() });
        return _handleResponse(res);
    },

    deleteAllUsers: async () => {
        const res = await fetch(`${API_BASE}/admin/users?${_auth()}`, {
            method: 'DELETE',
            headers: _headers()
        });
        return _handleResponse(res);
    }
});
