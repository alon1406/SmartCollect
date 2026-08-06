/* =============================================
   Command service — /commands and /admin/commands
   ============================================= */

Object.assign(api, {

    /**
     * Invoke a command (END_USER only). The server applies the side effects.
     * { command, targetObject, invokedBy, commandAttributes }
     */
    invokeCommand: async (cmd) => {
        const res = await fetch(`${API_BASE}/commands?${_pwd()}`, {
            method: 'POST',
            headers: _headers(),
            body: JSON.stringify(cmd)
        });
        return _handleResponse(res);
    },

    deleteAllCommands: async () => {
        const res = await fetch(`${API_BASE}/admin/commands?${_auth()}`, {
            method: 'DELETE',
            headers: _headers()
        });
        return _handleResponse(res);
    },

    getAllCommands: async () => {
        const res = await fetch(`${API_BASE}/admin/commands?${_auth()}&${_page()}`, { headers: _headers() });
        return _handleResponse(res);
    }
});
