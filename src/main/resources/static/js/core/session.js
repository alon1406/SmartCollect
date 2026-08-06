/* =============================================
   Core session — logged-in user + credentials in sessionStorage.
   ============================================= */

const session = {
    getUser: () => { const u = sessionStorage.getItem('sc_user'); return u ? JSON.parse(u) : null; },
    setUser: (user) => sessionStorage.setItem('sc_user', JSON.stringify(user)),

    /** Logged-in user's password (stashed at login) — required by the v1.4 API */
    getPassword: () => sessionStorage.getItem('sc_pwd') || '',

    logout: () => { sessionStorage.removeItem('sc_user'); sessionStorage.removeItem('sc_pwd'); window.location.href = 'index.html'; },

    /** Returns the logged-in user's email from userId map */
    getEmail: () => { const u = session.getUser(); return u?.userId?.email || null; },

    /** Returns role string: ADMIN | OPERATOR | END_USER */
    getRole: () => { const u = session.getUser(); return u?.role || null; },

    /** Guards a page — redirects to login if not logged in or wrong role */
    require: (role) => {
        const u = session.getUser();
        if (!u) { window.location.href = 'index.html'; return null; }
        if (role && u.role !== role) { window.location.href = 'index.html'; return null; }
        return u;
    }
};
