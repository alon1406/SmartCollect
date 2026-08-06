/* =============================================
   driver.js — Driver Dashboard Logic
   Role: END_USER
   ============================================= */

let currentUser = null;
let driverMap = null;
let currentRoute = null;
let routeBins = [];
let binMarkers = {};
let selectedErrorType = null;
let errorTargetBinId = null;
let assignedTruck = null;

// ── Session-local progress overlay ──────────────────────────────────────────
// Under the v1.4 RBAC an END_USER (driver) may ONLY POST commands — it cannot
// PUT objects. The collection itself is the command; the server is expected to
// apply the side effects (empty the bin, advance the route, update the truck).
// Until the backend wires those effects up, we POST the command (the legal
// action) and keep the driver's progress in this local overlay so the route
// still advances on screen and survives the 10s silent refresh. Once the
// backend applies command effects, server state and this overlay agree.
let localRouteId = null;          // UUID the overlay belongs to
let localCompleted = new Set();   // binIds collected this session
let localErrors = {};             // binId -> errorType reported this session
let localRouteStatus = null;      // e.g. 'returning' after depot arrival

function _resetLocalProgress(routeId) {
    localRouteId = routeId;
    localCompleted = new Set();
    localErrors = {};
    localRouteStatus = null;
}

document.addEventListener('DOMContentLoaded', async () => {
    currentUser = session.require('END_USER');
    if (!currentUser) return;

    document.getElementById('user-name').textContent = currentUser.username || 'Driver';
    document.getElementById('btn-logout').addEventListener('click', () => session.logout());

    initDriverMap();
    await loadRoute();

    // Auto-refresh the route silently every 10 seconds
    setInterval(() => loadRoute(true), 10000);
});

// ─── Map ─────────────────────────────────────────────────────────────────────

function initDriverMap() {
    driverMap = L.map('driver-map').setView([32.114, 34.796], 13);
    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
        attribution: '© OpenStreetMap contributors'
    }).addTo(driverMap);
}

function binMarkerIcon(bin, isCollected) {
    const attr = bin.objectDetails || {};
    const fill = parseFloat(attr.fillLevel) || 0;
    const hasError = attr.errorStatus && attr.errorStatus !== null && attr.errorStatus !== 'null';
    const binType = attr.binType;

    let bg = '#9CA3AF'; // neutral gray for collected
    if (!isCollected) {
        bg = api.binTypeColor(binType); // color by waste type
    }

    let label = hasError ? '⚠' : `${fill}%`;
    if (isCollected) label = '✓';

    const borderStyle = hasError ? 'border: 3px solid #EF4444;'
        : fill >= 80 ? 'border: 3px dashed #EF4444;'
            : 'border: 2px solid #fff;';
    const animClass = (!isCollected && (hasError || fill >= 80)) ? 'pulse-red' : '';

    return L.divIcon({
        className: animClass,
        html: `<div style="
            background:${bg};color:#fff;
            width:36px;height:36px;border-radius:50%;
            display:flex;align-items:center;justify-content:center;
            font-size:9px;font-weight:700;
            box-shadow:0 2px 8px rgba(0,0,0,0.3);
            ${borderStyle}
            opacity:${isCollected ? 0.6 : 1};">${label}</div>`,
        iconSize: [36, 36],
        iconAnchor: [18, 18]
    });
}

let driverRoutePolyline = null;

function renderMapBins(completedIds, fit = true) {
    Object.values(binMarkers).forEach(m => driverMap.removeLayer(m));
    binMarkers = {};

    const completedSet = new Set(completedIds || []);
    const bounds = [];

    // Remove existing polyline if any to prevent memory leaks
    if (driverRoutePolyline) {
        driverMap.removeLayer(driverRoutePolyline);
        driverRoutePolyline = null;
    }

    routeBins.forEach((bin, idx) => {
        const loc = bin.location || {};
        const lat = parseFloat(loc.lat);
        const lng = parseFloat(loc.lng);
        if (isNaN(lat) || isNaN(lng)) return;

        const uuid = api.getUUID(bin);
        const collected = completedSet.has(uuid);
        const attr = bin.objectDetails || {};

        const marker = L.marker([lat, lng], { icon: binMarkerIcon(bin, collected) })
            .bindPopup(`
                <b>#${idx + 1} — ${esc(bin.alias) || '—'}</b><br>
                ${esc(attr.address) || ''}<br>
                Fill: <b>${attr.fillLevel || 0}%</b><br>
                ${collected ? '<span style="color:green;font-weight:600;">✓ Collected</span>' : ''}
                ${attr.errorStatus && attr.errorStatus !== 'null' ? `<br><span style="color:red;">⚠ ${esc(attr.errorStatus)}</span>` : ''}
            `)
            .addTo(driverMap);
        binMarkers[uuid] = marker;
        bounds.push([lat, lng]);
    });

    // Draw road-following route (OSRM) from the depot through the bins, with a
    // straight-line fallback if the routing service is unavailable.
    drawDriverRoute(bounds);

    if (fit && bounds.length > 0) {
        driverMap.fitBounds(bounds, { padding: [40, 40] });
    }
}

// Real driving geometry, not crow-flies. Public OSRM demo server (best-effort);
// falls back to a dashed straight line on any failure.
let _routeReqId = 0;
async function drawDriverRoute(binCoords) {
    const myReq = ++_routeReqId;
    if (driverRoutePolyline) { driverMap.removeLayer(driverRoutePolyline); driverRoutePolyline = null; }
    if (!binCoords || binCoords.length === 0) return;

    const depot = [32.114, 34.796]; // warehouse start
    const waypoints = [depot, ...binCoords];
    if (waypoints.length < 2) return;

    const drawLine = (latlngs, road) => {
        if (myReq !== _routeReqId) return; // a newer render superseded this request
        if (driverRoutePolyline) driverMap.removeLayer(driverRoutePolyline);
        driverRoutePolyline = L.polyline(latlngs, road
            ? { color: '#2563EB', weight: 4, opacity: 0.85, lineJoin: 'round' }
            : { color: '#3B82F6', weight: 3, dashArray: '8,6', opacity: 0.6 }
        ).addTo(driverMap);
    };

    try {
        const coordStr = waypoints.map(([la, ln]) => `${ln},${la}`).join(';');
        const url = `https://router.project-osrm.org/route/v1/driving/${coordStr}?overview=full&geometries=geojson`;
        const resp = await fetch(url);
        const data = await resp.json();
        if (data.code === 'Ok' && data.routes && data.routes[0] && data.routes[0].geometry) {
            drawLine(data.routes[0].geometry.coordinates.map(([ln, la]) => [la, ln]), true);
        } else {
            drawLine(waypoints, false);
        }
    } catch (_) {
        drawLine(waypoints, false); // routing service down → straight line
    }
}

// ─── Load Route ───────────────────────────────────────────────────────────────

async function loadRoute(isSilent = false) {
    const email = session.getEmail();
    if (!email) { session.logout(); return; }

    if (!isSilent) {
        document.getElementById('no-route').innerHTML = `
            <div class="spinner"></div><p style="margin-top:.75rem;">Loading your route...</p>`;
        document.getElementById('no-route').style.display = 'flex';
        document.getElementById('route-info').style.display = 'none';
    }

    try {
        const route = await api.getDriverRoute(email);

        if (!route) {
            // Route finished/unassigned (e.g. archived after the operator approves) —
            // wipe the previous route's markers + line so nothing lingers on the map.
            _routeReqId++; // cancel any in-flight OSRM draw
            Object.values(binMarkers).forEach(m => driverMap.removeLayer(m));
            binMarkers = {};
            if (driverRoutePolyline) { driverMap.removeLayer(driverRoutePolyline); driverRoutePolyline = null; }
            currentRoute = null;
            routeBins = [];

            document.getElementById('no-route').innerHTML = `
                <i class="fa-solid fa-route"></i>
                <p>No route assigned to you today.<br>
                <span style="font-size:.8rem;color:var(--text-muted);">Contact your operator if you think this is an error.</span></p>`;
            document.getElementById('no-route').style.display = 'flex';
            document.getElementById('route-info').style.display = 'none';
            return;
        }

        const oldAttr = currentRoute?.objectDetails || {};
        const oldBinState = routeBins.map(b => `${api.getUUID(b)}-${b.objectDetails?.fillLevel}-${b.objectDetails?.errorStatus}`).join(',');

        currentRoute = route;
        const routeId = api.getUUID(route);
        if (routeId !== localRouteId) _resetLocalProgress(routeId);
        const serverAttr = route.objectDetails || {};

        // Effective membership/order = server binIds minus locally-errored bins
        // (a reported bin is dropped from the route for this session).
        const effectiveBinIds = (serverAttr.binIds || []).filter(id => !localErrors[id]);

        // Load all bins in this route. binIds is the source of truth for current
        // membership AND order: a reported bin is removed from binIds but stays
        // bound as a child, so without this intersection it would reappear via
        // getChildren (at index -1, sorting to the front) and hijack the lock.
        const binIdSet = new Set(effectiveBinIds);
        let newRouteBins = (await api.getChildren(api.getUUID(route)) || [])
            .filter(b => binIdSet.has(api.getUUID(b)));
        if (newRouteBins.length === 0 && binIdSet.size > 0) {
            const allObjects = await api.getAllObjects();
            newRouteBins = allObjects.filter(o => o.type === 'BIN' && binIdSet.has(api.getUUID(o)));
        }

        // Sort by route order (binIds)
        newRouteBins.sort((a, b) => effectiveBinIds.indexOf(api.getUUID(a)) - effectiveBinIds.indexOf(api.getUUID(b)));

        // Apply the session-local overlay: locally-collected bins read as empty.
        newRouteBins = newRouteBins.map(b => localCompleted.has(api.getUUID(b))
            ? { ...b, objectDetails: { ...b.objectDetails, fillLevel: 0, errorStatus: null } }
            : b);

        // Merge completion: server's completedBinIds + locally collected, limited
        // to current members. Status falls back to any local depot-arrival state.
        const mergedCompleted = [...new Set([...(serverAttr.completedBinIds || []), ...localCompleted])]
            .filter(id => binIdSet.has(id));
        const attr = {
            ...serverAttr,
            binIds: effectiveBinIds,
            completedBinIds: mergedCompleted,
            status: localRouteStatus || serverAttr.status
        };
        currentRoute.objectDetails = attr;

        const newBinState = newRouteBins.map(b => `${api.getUUID(b)}-${b.objectDetails?.fillLevel}-${b.objectDetails?.errorStatus}`).join(',');

        const stateChanged =
            JSON.stringify(oldAttr.binIds) !== JSON.stringify(attr.binIds) ||
            JSON.stringify(oldAttr.completedBinIds) !== JSON.stringify(attr.completedBinIds) ||
            oldAttr.status !== attr.status ||
            oldAttr.assignedTruckId !== attr.assignedTruckId ||
            oldBinState !== newBinState ||
            !routeBins.length;

        routeBins = newRouteBins;

        // Load assigned truck
        assignedTruck = null;
        if (attr.assignedTruckId) {
            try {
                assignedTruck = await api.getObject(attr.assignedTruckId);
            } catch (_) {}
        }

        if (stateChanged || !isSilent) {
            renderRoutePanel(attr);
            renderMapBins(attr.completedBinIds || [], !isSilent);
        }

        document.getElementById('topbar-route-name').textContent = route.alias || 'My Route';
        document.getElementById('no-route').style.display = 'none';
        document.getElementById('route-info').style.display = '';

    } catch (err) {
        if (!isSilent) {
            document.getElementById('no-route').innerHTML = `
                <i class="fa-solid fa-circle-exclamation" style="color:#EF4444;"></i>
                <p>Error loading route: ${err.message}</p>`;
            document.getElementById('no-route').style.display = 'flex';
            document.getElementById('route-info').style.display = 'none';
        }
    }
}

// ─── Render Route Panel ───────────────────────────────────────────────────────

function renderRoutePanel(attr) {
    const total = (attr.binIds || []).length;
    const completed = (attr.completedBinIds || []).length;
    const pct = total > 0 ? Math.round((completed / total) * 100) : 0;
    const completedSet = new Set(attr.completedBinIds || []);

    // Sequential lock: the only actionable bin is the first one in route order
    // that hasn't been collected yet. Every uncollected bin after it stays locked.
    const activeBin = routeBins.find(b => !completedSet.has(api.getUUID(b)));
    const activeId = activeBin ? api.getUUID(activeBin) : null;

    // Header meta
    document.getElementById('route-display-name').textContent = currentRoute.alias || '—';
    document.getElementById('route-display-meta').textContent =
        `${(currentRoute.creationTimestamp || '').slice(0, 10) || '—'} · Status: ${attr.status || '—'}`;

    // Progress
    document.getElementById('prog-label').textContent = `${completed} / ${total} bins collected`;
    document.getElementById('prog-pct').textContent = pct + '%';
    document.getElementById('prog-bar').style.width = pct + '%';

    // Bin list
    const listEl = document.getElementById('bin-list');
    if (routeBins.length === 0) {
        listEl.innerHTML = '<p style="color:var(--text-muted);text-align:center;padding:1rem;">No bins assigned to this route.</p>';
    } else {
        listEl.innerHTML = routeBins.map((bin, idx) => {
            const a = bin.objectDetails || {};
            const uuid = api.getUUID(bin);
            const fill = parseFloat(a.fillLevel) || 0;
            const isCollected = completedSet.has(uuid);
            const isActive = !isCollected && uuid === activeId;
            const isLocked = !isCollected && !isActive;
            const hasError = a.errorStatus && a.errorStatus !== null && a.errorStatus !== 'null';
            const fillColor = api.fillColor(fill);

            let badge, actions;
            if (isCollected) {
                badge = `<span class="badge badge-green"><i class="fa-solid fa-check"></i> Done</span>`;
                actions = `<div class="collected-label"><i class="fa-solid fa-circle-check"></i> Collected</div>`;
            } else if (isActive) {
                badge = `<span class="badge badge-${fillColor === 'red' ? 'red' : fillColor === 'yellow' ? 'yellow' : 'green'}">${fill}%</span>`;
                actions = `<div class="bin-card-actions">
                        <button class="btn btn-sm btn-primary" onclick="markCollected('${uuid}')">
                            <i class="fa-solid fa-check"></i> Collected
                        </button>
                        <button class="btn btn-sm btn-ghost" style="color:#EF4444;" onclick="openErrorModal('${uuid}')" title="Report an issue">
                            <i class="fa-solid fa-triangle-exclamation"></i> Report
                        </button>
                        <button class="btn btn-sm btn-ghost" onclick="flyToBin('${uuid}')" title="Show on map">
                            <i class="fa-solid fa-location-dot"></i>
                        </button>
                        <button class="btn btn-sm btn-ghost" onclick="navigateToBin('${uuid}')" title="Navigate (open in Maps)">
                            <i class="fa-solid fa-diamond-turn-right"></i>
                        </button>
                    </div>`;
            } else {
                // Locked — the previous stop must be finished first
                badge = `<span class="badge badge-gray"><i class="fa-solid fa-lock"></i> Locked</span>`;
                actions = `<div class="bin-card-actions">
                        <button class="btn btn-sm btn-ghost" disabled title="Finish the previous stop first">
                            <i class="fa-solid fa-lock"></i> Locked
                        </button>
                        <button class="btn btn-sm btn-ghost" onclick="flyToBin('${uuid}')" title="Show on map">
                            <i class="fa-solid fa-location-dot"></i>
                        </button>
                    </div>
                    <div class="bin-lock-hint"><i class="fa-solid fa-lock"></i> Complete the previous bin to unlock</div>`;
            }

            return `
            <div class="bin-card ${isCollected ? 'collected' : ''} ${isLocked ? 'locked' : ''} ${hasError && !isCollected ? 'error' : ''}" id="card-${uuid}">
                <div class="bin-card-header">
                    <span class="bin-card-name">#${idx + 1} — ${esc(bin.alias) || '—'}</span>
                    ${badge}
                </div>
                <div class="bin-card-addr">${esc(a.address || a.binType) || '—'}</div>
                ${hasError ? `<div style="color:#B91C1C;font-size:.75rem;">⚠ ${esc(a.errorStatus)}</div>` : ''}
                ${actions}
            </div>`;
        }).join('');
    }

    // Footer — Complete Route button
    const footerEl = document.getElementById('route-footer');
    const isActive = attr.status === 'in_progress' || attr.status === 'planned';
    const isDone = attr.status === 'completed';
    if (isDone) {
        footerEl.innerHTML = `
            <div style="text-align:center;color:#15803D;font-weight:700;">
                <i class="fa-solid fa-circle-check"></i> Route Completed!
            </div>
        `;
    } else if (attr.status === 'returning') {
        footerEl.innerHTML = `
            <div style="text-align:center;color:#3B82F6;font-weight:700;">
                <i class="fa-solid fa-clock-rotate-left"></i> Returned to Depot (Pending Approval)
            </div>
        `;
    } else if (isActive && completed === total && total > 0) {
        footerEl.innerHTML = `
            <button class="btn btn-primary w-full" style="background:#10B981;" onclick="confirmDepotArrival()">
                <i class="fa-solid fa-truck-ramp-box"></i> Returned to Depot
            </button>
        `;
    } else if (isActive) {
        footerEl.innerHTML = `<div style="font-size:.8rem;color:var(--text-muted);text-align:center;">
            ${total - completed} bins remaining</div>`;
    }
}

// The next actionable bin = first bin in route order not yet collected.
// Drivers must work the route in sequence (no skipping ahead).
function getActiveBinId() {
    if (!currentRoute) return null;
    const completed = new Set(currentRoute.objectDetails?.completedBinIds || []);
    const next = routeBins.find(b => !completed.has(api.getUUID(b)));
    return next ? api.getUUID(next) : null;
}

// ─── Actions ─────────────────────────────────────────────────────────────────

async function markCollected(binUUID) {
    if (!currentRoute) return;
    const routeUUID = api.getUUID(currentRoute);
    const attr = currentRoute.objectDetails || {};

    // Already collected?
    if ((attr.completedBinIds || []).includes(binUUID)) return;

    // Sequential lock — only the next uncollected bin in route order may be actioned
    if (getActiveBinId() !== binUUID) {
        showToast('Please finish the previous bin first.', 'warning');
        return;
    }

    try {
        const bin = routeBins.find(b => api.getUUID(b) === binUUID);
        const binAttr = bin?.objectDetails || {};
        const fill = parseFloat(binAttr.fillLevel) || 0;
        const cap = parseFloat(binAttr.capacity) || 0;
        const collectedWeight = Math.round((fill / 100) * cap);

        // A driver's only legal write is the command itself. The server is
        // expected to apply the side effects (empty the bin, advance the route,
        // add weight to the truck). We POST it and reflect progress locally.
        await api.invokeCommand({
            command: 'BinCollected',
            targetObject: { id: { systemID: SYSTEM_ID, objectId: binUUID } },
            invokedBy: { userId: { systemID: SYSTEM_ID, email: session.getEmail() } },
            commandAttributes: { binAlias: bin?.alias, binType: binAttr.binType || 'general', collectedAt: new Date().toISOString(), collectedWeight, fillAtCollection: fill }
        });

        // Session-local overlay: mark this bin collected and advance the route.
        localCompleted.add(binUUID);
        const newCompleted = [...new Set([...(attr.completedBinIds || []), binUUID])];
        const newAttr = { ...attr, completedBinIds: newCompleted, status: 'in_progress' };
        if (!newAttr.startedAt) newAttr.startedAt = new Date().toISOString();
        currentRoute.objectDetails = newAttr;
        if (bin) { bin.objectDetails = { ...bin.objectDetails, fillLevel: 0, errorStatus: null }; }

        renderRoutePanel(newAttr);
        renderMapBins(newCompleted);
        showToast('Bin marked as collected ✓', 'success');

    } catch (err) {
        showToast('Error: ' + err.message, 'error');
    }
}

async function confirmDepotArrival() {
    if (!currentRoute) return;
    const routeUUID = api.getUUID(currentRoute);
    const attr = currentRoute.objectDetails || {};
    
    try {
        const btn = document.querySelector('#route-footer button');
        if (btn) {
            btn.disabled = true;
            btn.textContent = 'Processing...';
        }

        // Report depot arrival as a command (the operator/server handles the
        // truck release + route close-out). Reflect 'returning' locally.
        await api.invokeCommand({
            command: 'TruckArrivedAtDepot',
            targetObject: { id: { systemID: SYSTEM_ID, objectId: attr.assignedTruckId || routeUUID } },
            invokedBy: { userId: { systemID: SYSTEM_ID, email: session.getEmail() } },
            commandAttributes: { routeAlias: currentRoute.alias, arrivedAt: new Date().toISOString() }
        });

        localRouteStatus = 'returning';
        const newRouteAttr = { ...attr, status: 'returning', completedAt: new Date().toISOString() };
        currentRoute.objectDetails = newRouteAttr;

        renderRoutePanel(newRouteAttr);
        showToast('Depot arrival reported! Pending operator approval.', 'success');
    } catch (err) {
        showToast('Error: ' + err.message, 'error');
        await loadRoute();
    }
}

function flyToBin(uuid) {
    const marker = binMarkers[uuid];
    if (marker) {
        driverMap.flyTo(marker.getLatLng(), 17, { animate: true, duration: 0.8 });
        marker.openPopup();
    }
}

// Hand off to the device's maps app for turn-by-turn directions to this bin.
function navigateToBin(uuid) {
    const bin = routeBins.find(b => api.getUUID(b) === uuid);
    const loc = bin?.location || {};
    const lat = parseFloat(loc.lat), lng = parseFloat(loc.lng);
    if (isNaN(lat) || isNaN(lng)) { showToast('No location set for this bin.', 'warning'); return; }
    const url = `https://www.google.com/maps/dir/?api=1&destination=${lat},${lng}&travelmode=driving`;
    window.open(url, '_blank', 'noopener');
}

// ─── Error Reporting ──────────────────────────────────────────────────────────

function openErrorModal(binUUID) {
    // Sequential lock — can only report the current (active) bin
    if (getActiveBinId() !== binUUID) {
        showToast('Please finish the previous bin first.', 'warning');
        return;
    }
    errorTargetBinId = binUUID;
    selectedErrorType = null;
    document.querySelectorAll('.error-option').forEach(o => o.classList.remove('selected'));
    document.getElementById('btn-report-error').disabled = true;
    document.getElementById('error-modal').classList.add('open');
}

function closeErrorModal() {
    document.getElementById('error-modal').classList.remove('open');
    errorTargetBinId = null;
    selectedErrorType = null;
}

function selectError(type, el) {
    selectedErrorType = type;
    document.querySelectorAll('.error-option').forEach(o => o.classList.remove('selected'));
    el.classList.add('selected');
    document.getElementById('btn-report-error').disabled = false;
}

async function submitErrorReport() {
    if (!errorTargetBinId || !selectedErrorType) return;
    const btn = document.getElementById('btn-report-error');
    btn.disabled = true; btn.textContent = 'Reporting...';

    try {
        const bin = routeBins.find(b => api.getUUID(b) === errorTargetBinId);

        // Report the issue as a command (the legal driver action). The server is
        // expected to flag the bin; we drop it from this session's route locally.
        await api.invokeCommand({
            command: 'BinErrorReported',
            targetObject: { id: { systemID: SYSTEM_ID, objectId: errorTargetBinId } },
            invokedBy: { userId: { systemID: SYSTEM_ID, email: session.getEmail() } },
            commandAttributes: { errorType: selectedErrorType, binAlias: bin?.alias, reportedAt: new Date().toISOString() }
        });

        // Session-local overlay: remember the error and remove the bin from the route.
        localErrors[errorTargetBinId] = selectedErrorType;
        localCompleted.delete(errorTargetBinId);
        if (currentRoute) {
            const rAttr = currentRoute.objectDetails || {};
            const newBinIds = (rAttr.binIds || []).filter(id => id !== errorTargetBinId);
            const newCompleted = (rAttr.completedBinIds || []).filter(id => id !== errorTargetBinId);
            const newRouteAttr = { ...rAttr, binIds: newBinIds, completedBinIds: newCompleted };
            currentRoute.objectDetails = newRouteAttr;
            routeBins = routeBins.filter(b => api.getUUID(b) !== errorTargetBinId);
        }

        closeErrorModal();
        renderRoutePanel(currentRoute.objectDetails);
        renderMapBins((currentRoute.objectDetails?.completedBinIds || []));
        showToast('Issue reported — bin removed from route.', 'warning');
    } catch (err) {
        showToast('Error: ' + err.message, 'error');
    } finally {
        btn.disabled = false;
        btn.innerHTML = '<i class="fa-solid fa-triangle-exclamation"></i> Report Issue';
    }
}
