/* =============================================
   Object service — /objects, /admin/objects, search,
   plus the object builders and boundary helpers.
   ============================================= */

Object.assign(api, {

    /**
     * Create object (BIN / TRUCK / ROUTE)
     * Required: type, alias, status (non-blank), active, createdBy
     * createdBy must be: { userId: { systemID: SYSTEM_ID, email: "..." } }
     * Metadata goes in: objectDetails
     * Server auto-generates: id (UUID), creationTimestamp, internal id
     */
    createObject: async (obj) => {
        const res = await fetch(`${API_BASE}/objects?${_pwd()}`, {
            method: 'POST',
            headers: _headers(),
            body: JSON.stringify(obj)
        });
        return _handleResponse(res);
    },

    /**
     * Update object — only type, alias, status, active, location, objectDetails can change
     * id in returned boundary: { systemID: "...", objectId: "uuid" }
     */
    updateObject: async (objectUUID, update) => {
        const res = await fetch(`${API_BASE}/objects/${SYSTEM_ID}/${objectUUID}?${_auth()}`, {
            method: 'PUT',
            headers: _headers(),
            body: JSON.stringify(update)
        });
        return _handleResponse(res);
    },

    /** Get single object by its UUID (the value inside id.objectId) */
    getObject: async (objectUUID) => {
        const res = await fetch(`${API_BASE}/objects/${SYSTEM_ID}/${objectUUID}?${_auth()}`, {
            headers: _headers()
        });
        return _handleResponse(res);
    },

    /** Get ALL objects — returns ObjectBoundary[] */
    getAllObjects: async () => {
        const res = await fetch(`${API_BASE}/objects?${_auth()}&${_page()}`, { headers: _headers() });
        return _handleResponse(res);
    },

    /** Bind a child object to a parent object */
    bindChildToObject: async (parentUUID, childSystemID, childUUID) => {
        const res = await fetch(`${API_BASE}/objects/${SYSTEM_ID}/${parentUUID}/children?${_auth()}`, {
            method: 'PUT',
            headers: _headers(),
            body: JSON.stringify({
                childId: {
                    systemID: childSystemID,
                    objectId: childUUID
                }
            })
        });
        return _handleResponse(res);
    },

    /** Get children of a parent object — returns ObjectBoundary[] */
    getChildren: async (parentUUID) => {
        const res = await fetch(`${API_BASE}/objects/${SYSTEM_ID}/${parentUUID}/children?${_auth()}&${_page()}`, {
            headers: _headers()
        });
        return _handleResponse(res);
    },

    /** Get parents of a child object — returns ObjectBoundary[] */
    getParents: async (childUUID) => {
        const res = await fetch(`${API_BASE}/objects/${SYSTEM_ID}/${childUUID}/parents?${_auth()}&${_page()}`, {
            headers: _headers()
        });
        return _handleResponse(res);
    },

    deleteAllObjects: async () => {
        const res = await fetch(`${API_BASE}/admin/objects?${_auth()}`, {
            method: 'DELETE',
            headers: _headers()
        });
        return _handleResponse(res);
    },

    /**
     * v1.4 server-side object search, one page at a time.
     * mode: 'aliasPattern' | 'alias' | 'type' | 'status'
     * Returns ObjectBoundary[] for the requested page.
     * ponytail: location search (byLocation) skipped — add when a map-radius UI needs it.
     */
    searchObjects: async (mode, query, { size = 5, page = 0 } = {}) => {
        const seg = {
            aliasPattern: 'byAliasPattern',
            alias: 'byAlias',
            type: 'byType',
            status: 'byStatus'
        }[mode];
        if (!seg) throw new Error('Unknown search mode: ' + mode);
        const url = `${API_BASE}/objects/search/${seg}/${encodeURIComponent(query)}?${_auth()}&size=${size}&page=${page}`;
        const res = await fetch(url, { headers: _headers() });
        return _handleResponse(res);
    },

    // ─── Client-side helpers (filter getAllObjects) ──────────────────────────

    getBins: async () => {
        const all = await api.getAllObjects();
        return all.filter(o => o.type === 'BIN' && o.active !== false);
    },

    getTrucks: async () => {
        const all = await api.getAllObjects();
        return all.filter(o => o.type === 'TRUCK' && o.active !== false);
    },

    getRoutes: async () => {
        const all = await api.getAllObjects();
        return all.filter(o => o.type === 'ROUTE' && o.active !== false);
    },

    getDriverRoute: async (driverEmail) => {
        const routes = await api.getRoutes();
        return routes.find(r =>
            r.objectDetails && r.objectDetails.assignedDriverEmail === driverEmail
        ) || null;
    },

    // ─── Object Builders ─────────────────────────────────────────────────────

    /** Build a valid BIN object ready to POST */
    buildBin: (alias, lat, lng, binType, capacity, address, creatorEmail) => ({
        type: 'BIN',
        alias,
        status: 'active',
        active: true,
        location: { lat, lng },
        createdBy: {
            userId: { systemID: SYSTEM_ID, email: creatorEmail }
        },
        objectDetails: {
            fillLevel: 0,
            binType: binType || 'general',
            capacity: capacity || 1100,
            address: address || '',
            lastCollected: null,
            status: 'active',
            errorStatus: null
        }
    }),

    /** Build a valid TRUCK object ready to POST */
    buildTruck: (alias, plateNumber, totalCapacity, creatorEmail) => ({
        type: 'TRUCK',
        alias,
        status: 'available',
        active: true,
        location: { lat: 32.114, lng: 34.796 },
        createdBy: {
            userId: { systemID: SYSTEM_ID, email: creatorEmail }
        },
        objectDetails: {
            plateNumber: plateNumber || '',
            driverEmail: null,
            driverName: null,
            status: 'available',
            currentRouteId: null,
            collectedCount: 0,
            totalCapacity: totalCapacity || 8000,
            currentLoad: 0
        }
    }),

    /** Build a valid ROUTE object ready to POST */
    buildRoute: (alias, binIds, truckId, driverEmail, driverName, creatorEmail) => ({
        type: 'ROUTE',
        alias,
        status: 'planned',
        active: true,
        location: { lat: 32.114, lng: 34.796 },
        createdBy: {
            userId: { systemID: SYSTEM_ID, email: creatorEmail }
        },
        objectDetails: {
            assignedTruckId: truckId || null,
            assignedDriverEmail: driverEmail || null,
            assignedDriverName: driverName || null,
            status: 'planned',
            binIds: binIds || [],
            completedBinIds: [],
            startedAt: null,
            completedAt: null
        }
    }),

    // ─── Helpers to extract UUID / classify bins ─────────────────────────────

    /** Extract the UUID of an object from its id map (v1.4: { id: { systemID, objectId } }) */
    getUUID: (obj) => {
        if (!obj || !obj.id) return null;
        return obj.id.objectId || null;
    },

    /** Fill level color classification */
    fillColor: (level) => {
        if (level >= 80) return 'red';
        if (level >= 50) return 'yellow';
        return 'green';
    },

    /**
     * Canonical bin-type → marker color map.
     * Allowed binTypes: general | plastic | paper | glass | organic
     * Single source of truth shared by operator + driver maps.
     */
    binTypeColor: (binType) => {
        switch (binType) {
            case 'plastic': return '#3B82F6'; // blue
            case 'paper':   return '#F59E0B'; // amber
            case 'glass':   return '#06B6D4'; // cyan
            case 'organic': return '#16A34A'; // green
            default:        return '#6B7280'; // general: gray
        }
    },

    /** Short " [type]" suffix for non-general bins (empty string for general) */
    binTypeLabel: (binType) =>
        (binType && binType !== 'general') ? ` [${binType}]` : ''
});
