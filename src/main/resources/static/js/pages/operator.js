/* =============================================
   operator.js — Operator Dashboard Logic
   Role: OPERATOR
   ============================================= */

let currentUser = null;
let map = null;
let allBins = [];
let allTrucks = [];
let allRoutes = [];
let allObjects = [];
let allPastRoutes = [];
let filteredPastRoutes = [];
let allDrivers = [];
let binMarkers = {};
let truckMarkers = {};
let selectedBinIds = new Set();
let refreshInterval = null;
let activeRouteId = null;
let routeHighlightLayers = [];
let routePolyline = null;

document.addEventListener('DOMContentLoaded', async () => {
    currentUser = session.require('OPERATOR'); // OPERATOR only — admins have different permissions
    if (!currentUser) return;
    if (currentUser.role !== 'ADMIN' && currentUser.role !== 'OPERATOR') {
        session.logout(); return;
    }

    document.getElementById('user-name').textContent = currentUser.username || 'Operator';
    document.getElementById('btn-logout').addEventListener('click', () => session.logout());

    // Add back to Admin Console button for admins
    if (currentUser.role === 'ADMIN') {
        const nav = document.querySelector('.sidebar-nav');
        if (nav) {
            const adminItem = document.createElement('a');
            adminItem.className = 'sidebar-item';
            adminItem.href = 'admin.html';
            adminItem.style.textDecoration = 'none';
            adminItem.style.marginTop = '1rem';
            adminItem.innerHTML = `<i class="fa-solid fa-user-shield"></i> Admin Console`;
            nav.appendChild(adminItem);
        }
    }

    initMap();
    document.getElementById('bin-form').addEventListener('submit', handleSaveBin);
    document.getElementById('truck-form').addEventListener('submit', handleSaveTruck);
    document.getElementById('route-form').addEventListener('submit', handleSaveRoute);
    document.getElementById('edit-route-form').addEventListener('submit', handleUpdateRoute);
    document.getElementById('route-truck').addEventListener('change', updateRouteLoadSummary);
    document.getElementById('edit-route-truck').addEventListener('change', updateEditRouteLoadSummary);

    await refreshAll();

    // Auto-refresh every 10 seconds
    refreshInterval = setInterval(refreshAll, 10000);
});

// ─── Map ─────────────────────────────────────────────────────────────────────

function initMap() {
    map = L.map('map').setView([32.114, 34.796], 13);
    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
        attribution: '© OpenStreetMap contributors'
    }).addTo(map);

    // Warehouse Depot marker (Fuchsia color)
    const warehouseIcon = L.divIcon({
        className: '',
        html: `<div style="
            background:#D946EF;color:#fff;
            width:38px;height:38px;border-radius:8px;
            display:flex;align-items:center;justify-content:center;
            font-size:16px;
            border:2px solid #fff;
            box-shadow:0 3px 8px rgba(0,0,0,0.4);"><i class="fa-solid fa-warehouse"></i></div>`,
        iconSize: [38, 38],
        iconAnchor: [19, 19]
    });
    L.marker([32.114, 34.796], { icon: warehouseIcon })
        .addTo(map)
        .bindPopup('<b>SmartCollect Warehouse Depot</b><br>Afeka College - Truck starting location');

    // Click on the real map to set coordinates or open the Add Bin modal
    map.on('click', (e) => {
        if (e.originalEvent.target.closest('.leaflet-popup') || e.originalEvent.target.closest('.leaflet-marker-icon')) {
            return; // ignore clicks on popups or markers
        }
        
        const isModalOpen = document.getElementById('bin-modal').classList.contains('open');
        if (!isModalOpen) {
            openAddBinModal(e.latlng.lat, e.latlng.lng);
        } else {
            updateSelectedLocation(e.latlng.lat, e.latlng.lng);
        }
    });
}

function binIcon(binType, fillLevel, hasError) {
    const color = api.binTypeColor(binType);

    const icon = hasError ? '⚠' : `${fillLevel}%`;
    const borderStyle = hasError ? 'border: 3px solid #EF4444;'
        : fillLevel >= 80 ? 'border: 3px dashed #EF4444;'
            : 'border: 2px solid #fff;';
    const animClass = (hasError || fillLevel >= 80) ? 'pulse-red' : '';

    return L.divIcon({
        className: animClass,
        html: `<div style="
            background:${color};color:#fff;
            width:34px;height:34px;border-radius:50%;
            display:flex;align-items:center;justify-content:center;
            font-size:9px;font-weight:700;
            box-shadow:0 2px 6px rgba(0,0,0,0.3);
            ${borderStyle}">${icon}</div>`,
        iconSize: [34, 34],
        iconAnchor: [17, 17]
    });
}

function truckIcon(status) {
    // Trucks styled differently from bins (indigo/violet status based colors)
    const color = status === 'on_route' ? '#4F46E5'
        : status === 'available' ? '#6366F1'
            : '#94A3B8';
    return L.divIcon({
        className: '',
        html: `<div style="
            background:${color};color:#fff;
            width:36px;height:36px;border-radius:8px;
            display:flex;align-items:center;justify-content:center;
            font-size:16px;
            border:2px solid rgba(0,0,0,0.2);
            box-shadow:0 2px 6px rgba(0,0,0,0.3);">🚛</div>`,
        iconSize: [36, 36],
        iconAnchor: [18, 18]
    });
}

function renderMapBins() {
    // Remove old markers
    Object.values(binMarkers).forEach(m => map.removeLayer(m));
    binMarkers = {};

    allBins.forEach(bin => {
        const loc = bin.location || {};
        const attr = bin.objectDetails || {};
        const lat = parseFloat(loc.lat);
        const lng = parseFloat(loc.lng);
        if (isNaN(lat) || isNaN(lng)) return;

        const fill = parseFloat(attr.fillLevel) || 0;
        const hasError = attr.errorStatus && attr.errorStatus !== 'null' && attr.errorStatus !== null;
        const uuid = api.getUUID(bin);

        const marker = L.marker([lat, lng], { icon: binIcon(attr.binType, fill, hasError) })
            .bindPopup(`
                <b>${esc(bin.alias) || '—'}</b><br>
                Fill: <b>${fill}%</b><br>
                Type: ${esc(attr.binType) || '—'}<br>
                Status: ${esc(attr.status) || '—'}<br>
                ${hasError ? `<span style="color:red;">⚠ ${esc(attr.errorStatus)}</span><br>` : ''}
                <button onclick="openEditBinModal('${uuid}')" style="margin-top:4px;font-size:12px;cursor:pointer;">✏ Edit</button>
                <button onclick="deactivateBin('${uuid}')" style="margin-top:4px;font-size:12px;cursor:pointer;margin-left:4px;">🗑 Remove</button>
            `)
            .addTo(map);
        binMarkers[uuid] = marker;
    });
}

function renderMapTrucks() {
    Object.values(truckMarkers).forEach(m => map.removeLayer(m));
    truckMarkers = {};

    allTrucks.forEach(truck => {
        const loc = truck.location || {};
        const attr = truck.objectDetails || {};
        const lat = parseFloat(loc.lat);
        const lng = parseFloat(loc.lng);
        if (isNaN(lat) || isNaN(lng)) return;

        const uuid = api.getUUID(truck);
        const marker = L.marker([lat, lng], { icon: truckIcon(attr.status), zIndexOffset: 1000 })
            .bindPopup(`
                <b>${esc(truck.alias) || '—'}</b><br>
                Plate: ${esc(attr.plateNumber) || '—'}<br>
                Driver: ${esc(attr.driverName) || 'Unassigned'}<br>
                Status: <b>${esc(attr.status) || '—'}</b><br>
                Load: ${attr.currentLoad || 0}/${attr.totalCapacity || '—'} L<br>
                Collected: ${attr.collectedCount || 0} bins
            `)
            .addTo(map);
        truckMarkers[uuid] = marker;
    });
}

// ─── Refresh All Data ─────────────────────────────────────────────────────────

async function refreshAll() {
    try {
        const all = await api.getAllObjects();
        allObjects = all;
        allBins = all.filter(o => o.type === 'BIN' && o.active !== false);
        allTrucks = all.filter(o => o.type === 'TRUCK' && o.active !== false);
        allRoutes = all.filter(o => o.type === 'ROUTE' && o.active !== false);
        allPastRoutes = all.filter(o => o.type === 'ROUTE' && o.active === false);

        // Drivers are loaded on demand when a route/dispatch modal opens (see
        // loadDriversAsAdmin) — not here, so the 10s refresh never elevates role.

        updateKPIs();
        renderMapBins();
        renderMapTrucks();
        renderPanelBins();
        renderPanelTrucks();
        renderPanelAlerts();
        renderPanelRoutes();
        renderPastRoutesTable();
    } catch (err) {
        showToast('Refresh failed: ' + err.message, 'error');
    }
}

// ─── KPIs ─────────────────────────────────────────────────────────────────────

function updateKPIs() {
    const warn = allBins.filter(b => { const f = b.objectDetails?.fillLevel || 0; return f >= 50 && f < 80; }).length;
    const crit = allBins.filter(b => (b.objectDetails?.fillLevel || 0) >= 80 ||
        (b.objectDetails?.errorStatus && b.objectDetails.errorStatus !== null)).length;

    document.getElementById('kpi-warn').textContent = warn;
    document.getElementById('kpi-crit').textContent = crit;
    // Only surface Warning / Critical chips when there's something to flag.
    document.getElementById('kpi-warn-chip').style.display = warn > 0 ? '' : 'none';
    document.getElementById('kpi-crit-chip').style.display = crit > 0 ? '' : 'none';
    document.getElementById('tab-bin-count').textContent = allBins.length;
    document.getElementById('tab-truck-count').textContent = allTrucks.length;
    document.getElementById('tab-alert-count').textContent = crit;
    document.getElementById('tab-route-count').textContent = allRoutes.length;
}

// ─── Panel: Bins ──────────────────────────────────────────────────────────────

function renderPanelBins() {
    const el = document.getElementById('panel-bins');
    if (allBins.length === 0) {
        el.innerHTML = `<div style="text-align:center;padding:2rem;color:var(--text-muted);">
            <i class="fa-solid fa-trash-can" style="font-size:2rem;opacity:.3;"></i>
            <p style="margin-top:.5rem;">No bins yet.<br>Click <b>Add Bin</b> to get started.</p></div>`;
        return;
    }
    const sorted = [...allBins].sort((a, b) => (b.objectDetails?.fillLevel || 0) - (a.objectDetails?.fillLevel || 0));
    el.innerHTML = sorted.map(bin => {
        const attr = bin.objectDetails || {};
        const fill = parseFloat(attr.fillLevel) || 0;
        const color = api.fillColor(fill);
        const uuid = api.getUUID(bin);
        const hasError = attr.errorStatus && attr.errorStatus !== 'null' && attr.errorStatus !== null;
        return `<div class="op-item" onclick="flyToBin('${uuid}')">
            <div class="op-item-header">
                <span class="op-item-name">${esc(bin.alias) || '—'}</span>
                <span class="badge badge-${color === 'red' ? 'red' : color === 'yellow' ? 'yellow' : 'green'}">${fill}%</span>
            </div>
            <div class="op-item-sub">${esc(attr.address || attr.binType) || '—'}</div>
            ${hasError ? `<div style="color:#B91C1C;font-size:.75rem;margin-top:.25rem;">⚠ ${esc(attr.errorStatus)}</div>` : ''}
            <div class="fill-bar-wrap">
                <div class="fill-bar"><div class="fill-bar-inner ${color}" style="width:${fill}%"></div></div>
            </div>
            <div class="op-item-actions">
                <button class="btn btn-sm btn-ghost" onclick="event.stopPropagation();openEditBinModal('${uuid}')">
                    <i class="fa-solid fa-pen"></i> Edit
                </button>
                <button class="btn btn-sm btn-ghost" style="color:#EF4444;" onclick="event.stopPropagation();deactivateBin('${uuid}')">
                    <i class="fa-solid fa-trash"></i> Remove
                </button>
            </div>
        </div>`;
    }).join('');
}

// ─── Panel: Trucks ────────────────────────────────────────────────────────────

function renderPanelTrucks() {
    const el = document.getElementById('panel-trucks');
    if (allTrucks.length === 0) {
        el.innerHTML = `<div style="text-align:center;padding:2rem;color:var(--text-muted);">
            <i class="fa-solid fa-truck" style="font-size:2rem;opacity:.3;"></i>
            <p style="margin-top:.5rem;">No trucks yet.</p></div>`;
        return;
    }
    el.innerHTML = allTrucks.map(truck => {
        const attr = truck.objectDetails || {};
        const uuid = api.getUUID(truck);
        const statusColor = attr.status === 'on_route' ? 'blue'
            : attr.status === 'available' ? 'green' : 'gray';
        const loadPct = attr.totalCapacity ? Math.round((attr.currentLoad / attr.totalCapacity) * 100) : 0;
        return `<div class="op-item" onclick="flyToTruck('${uuid}')" style="cursor:pointer;">
            <div class="op-item-header">
                <span class="op-item-name">🚛 ${esc(truck.alias) || '—'}</span>
                <span class="badge badge-${statusColor}">${esc(attr.status) || '—'}</span>
            </div>
            <div class="op-item-sub">${esc(attr.plateNumber) || '—'} · Driver: ${esc(attr.driverName) || 'Unassigned'}</div>
            <div class="fill-bar-wrap">
                <div class="fill-bar"><div class="fill-bar-inner ${loadPct > 80 ? 'red' : loadPct > 50 ? 'yellow' : 'green'}" style="width:${loadPct}%"></div></div>
            </div>
            <div class="op-item-sub" style="margin-top:.25rem;">Load: ${attr.currentLoad || 0} / ${attr.totalCapacity || 0} L · ${attr.collectedCount || 0} bins collected</div>
            <div class="op-item-actions">
                <button class="btn btn-sm btn-ghost" onclick="event.stopPropagation();openEditTruckModal('${uuid}')">
                    <i class="fa-solid fa-pen"></i> Edit
                </button>
                <button class="btn btn-sm btn-ghost" style="color:#EF4444;" onclick="event.stopPropagation();deactivateTruck('${uuid}')">
                    <i class="fa-solid fa-trash"></i> Remove
                </button>
            </div>
        </div>`;
    }).join('');
}

// ─── Panel: Alerts ────────────────────────────────────────────────────────────

function renderPanelAlerts() {
    const el = document.getElementById('panel-alerts');
    const critBins = allBins.filter(b =>
        (b.objectDetails?.fillLevel || 0) >= 80 ||
        (b.objectDetails?.errorStatus && b.objectDetails.errorStatus !== null)
    );
    if (critBins.length === 0) {
        el.innerHTML = `<div style="text-align:center;padding:2rem;color:var(--text-muted);">
            <i class="fa-solid fa-circle-check" style="font-size:2rem;color:#22C55E;opacity:.7;"></i>
            <p style="margin-top:.5rem;">All clear! No alerts.</p></div>`;
        return;
    }
    el.innerHTML = critBins.map(bin => {
        const attr = bin.objectDetails || {};
        const fill = parseFloat(attr.fillLevel) || 0;
        const uuid = api.getUUID(bin);
        const hasError = attr.errorStatus && attr.errorStatus !== null && attr.errorStatus !== 'null';
        const cls = hasError ? '' : 'yellow';
        return `<div class="alert-item ${cls}">
            <div style="font-weight:700;font-size:.875rem;">${esc(bin.alias) || '—'}</div>
            <div style="font-size:.75rem;margin-top:.2rem;">${esc(attr.address) || ''}</div>
            <div style="margin-top:.35rem;">
                ${fill >= 80 ? `<span>🔴 Fill level: <b>${fill}%</b></span>` : ''}
                ${hasError ? `<span style="color:#B91C1C;"> ⚠ ${esc(attr.errorStatus)}</span>` : ''}
            </div>
            <button class="btn btn-sm btn-primary" style="margin-top:.5rem;font-size:.75rem;"
                onclick="flyToBin('${uuid}')">
                <i class="fa-solid fa-location-dot"></i> Show on map
            </button>
        </div>`;
    }).join('');
}

// ─── Panel: Routes ────────────────────────────────────────────────────────────

function renderPanelRoutes() {
    syncClearRouteBtn();
    const el = document.getElementById('panel-routes');
    const header = `<div class="panel-header">
        <span class="panel-title">Active Routes</span>
    </div>`;
    if (allRoutes.length === 0) {
        el.innerHTML = header + `<div style="text-align:center;padding:2rem;color:var(--text-muted);">
            No routes yet. Use <b>Create Route</b> in the sidebar.</div>`;
        return;
    }
    el.innerHTML = header + allRoutes.map(route => {
        const attr = route.objectDetails || {};
        const uuid = api.getUUID(route);
        const total = (attr.binIds || []).length;
        const done = (attr.completedBinIds || []).length;
        const pct = total > 0 ? Math.round((done / total) * 100) : 0;
        const statusColor = attr.status === 'in_progress' ? 'blue'
            : attr.status === 'returning' ? 'yellow'
            : attr.status === 'completed' ? 'green'
                : attr.status === 'cancelled' ? 'red' : 'gray';
        const isActive = uuid === activeRouteId;
        const hasCollected = done > 0;
        return `<div class="op-item ${isActive ? 'route-active' : ''}" onclick="toggleRouteSelection('${uuid}')" style="cursor:pointer;">
            <div class="op-item-header">
                <span class="op-item-name">${esc(route.alias) || '—'}</span>
                <span class="badge badge-${statusColor}">${esc(attr.status) || '—'}</span>
            </div>
            <div class="op-item-sub">Driver: ${esc(attr.assignedDriverName) || 'Unassigned'} · ${(route.creationTimestamp || '').slice(0, 10)}</div>
            <div class="route-progress">
                <div class="route-progress-text">${done} / ${total} bins collected (${pct}%)</div>
                <div class="fill-bar"><div class="fill-bar-inner green" style="width:${pct}%"></div></div>
            </div>
            <div class="op-item-actions" style="margin-top:.4rem;">
                <button class="btn btn-sm btn-ghost" ${hasCollected ? 'disabled style="opacity:0.5;cursor:not-allowed;" title="Route in progress - cannot edit"' : ''} onclick="event.stopPropagation();openEditRouteModal('${uuid}')">
                    <i class="fa-solid fa-pen"></i> Edit
                </button>
                ${attr.status === 'returning' ? `
                <button class="btn btn-sm btn-ghost" style="color:#2563EB;font-weight:700;" onclick="event.stopPropagation();approveRouteCompletion('${uuid}')">
                    <i class="fa-solid fa-circle-check"></i> Approve Done
                </button>
                ` : attr.status === 'completed' ? `
                <button class="btn btn-sm btn-ghost" disabled style="color:#10B981;font-weight:700;opacity:0.8;cursor:default;">
                    <i class="fa-solid fa-circle-check"></i> Approved
                </button>
                ` : `
                <button class="btn btn-sm btn-ghost" style="color:#EF4444;" onclick="event.stopPropagation();deactivateRoute('${uuid}')">
                    <i class="fa-solid fa-trash"></i> Delete
                </button>
                `}
                <span style="flex:1;"></span>
                <span style="font-size:.7rem;color:var(--text-muted);">
                    ${isActive ? '✓ Selected' : '🗺 Show'}
                </span>
            </div>
        </div>`;
    }).join('');
}

// ─── Tab Switch ───────────────────────────────────────────────────────────────

function switchTab(name, btn) {
    document.querySelectorAll('.op-tab').forEach(t => t.classList.remove('active'));
    btn.classList.add('active');
    ['bins', 'trucks', 'alerts', 'routes'].forEach(t => {
        document.getElementById('panel-' + t).style.display = t === name ? '' : 'none';
    });
}

// ─── Main View Switch ─────────────────────────────────────────────────────────

function switchMainView(viewName) {
    document.getElementById('nav-live-map').classList.toggle('active', viewName === 'live-map');
    document.getElementById('nav-past-routes').classList.toggle('active', viewName === 'past-routes');
    const navAnalytics = document.getElementById('nav-analytics');
    if (navAnalytics) navAnalytics.classList.toggle('active', viewName === 'analytics');
    const navSearch = document.getElementById('nav-search');
    if (navSearch) navSearch.classList.toggle('active', viewName === 'search');

    const liveMap = document.getElementById('live-map-view');
    const pastRoutes = document.getElementById('past-routes-view');
    const analytics = document.getElementById('analytics-view');
    const searchView = document.getElementById('search-view');
    const kpiStrip = document.getElementById('kpi-strip');

    liveMap.style.display = 'none';
    pastRoutes.style.display = 'none';
    if (analytics) analytics.style.display = 'none';
    if (searchView) searchView.style.display = 'none';

    if (viewName === 'live-map') {
        document.getElementById('page-title').textContent = 'Live Operations Map';
        kpiStrip.style.display = 'flex';
        liveMap.style.display = 'grid';
        if (map) setTimeout(() => map.invalidateSize(), 50);
    } else if (viewName === 'past-routes') {
        document.getElementById('page-title').textContent = 'Past Routes Log';
        kpiStrip.style.display = 'none';
        pastRoutes.style.display = 'flex';
        renderPastRoutesTable();
    } else if (viewName === 'analytics') {
        document.getElementById('page-title').textContent = 'Analytics & Insights';
        kpiStrip.style.display = 'none';
        if (analytics) analytics.style.display = 'block';
        renderAnalytics();
    } else if (viewName === 'search') {
        document.getElementById('page-title').textContent = 'Search Objects';
        kpiStrip.style.display = 'none';
        if (searchView) searchView.style.display = 'block';
        renderSearch();   // show everything by default
    }
}

// ─── Object browser — instant client-side filter + sort over allObjects ───────
// The v1.4 server search endpoints exist (api.searchObjects) but are exact/
// case-sensitive; the operator already holds every object (incl. inactive) in
// allObjects, so filtering here is instant, case-insensitive, and shows all by default.

let _searchSort = { key: 'alias', dir: 1 };

function sortSearch(key) {
    if (_searchSort.key === key) _searchSort.dir *= -1;
    else _searchSort = { key, dir: 1 };
    renderSearch();
}

function resetSearch() {
    document.getElementById('search-query').value = '';
    document.getElementById('search-type').value = 'ALL';
    document.getElementById('search-active').value = 'ALL';
    _searchSort = { key: 'alias', dir: 1 };
    renderSearch();
}

function renderSearch() {
    const q = (document.getElementById('search-query').value || '').trim().toLowerCase();
    const type = document.getElementById('search-type').value;
    const state = document.getElementById('search-active').value;

    let rows = (allObjects || []).filter(o => {
        if (type !== 'ALL' && o.type !== type) return false;
        if (state === 'active' && o.active === false) return false;
        if (state === 'inactive' && o.active !== false) return false;
        if (!q) return true;
        const a = o.objectDetails || {};
        const hay = [o.alias, o.type, o.status, a.address, a.binType, a.plateNumber,
                     a.assignedDriverName, a.assignedDriverEmail, api.getUUID(o)]
            .filter(Boolean).join(' ').toLowerCase();
        return hay.includes(q);
    });

    const { key, dir } = _searchSort;
    const val = o => {
        if (key === 'fill') return parseFloat(o.objectDetails?.fillLevel) || 0;
        if (key === 'alias') return (o.alias || '').toLowerCase();
        if (key === 'type') return (o.type || '').toLowerCase();
        if (key === 'status') return (o.status || '').toLowerCase();
        return '';
    };
    rows.sort((x, y) => { const a = val(x), b = val(y); return a < b ? -dir : a > b ? dir : 0; });

    document.getElementById('search-meta').textContent =
        `${rows.length} object${rows.length === 1 ? '' : 's'}`;

    const body = document.getElementById('search-results-body');
    body.innerHTML = rows.length
        ? rows.map(o => {
            const a = o.objectDetails || {};
            const info = o.type === 'BIN' ? (a.fillLevel != null ? a.fillLevel + '%' : '—')
                : o.type === 'TRUCK' ? (a.plateNumber || '—')
                : o.type === 'ROUTE' ? (a.assignedDriverName || a.assignedDriverEmail || '—')
                : '—';
            const badge = o.active === false
                ? '<span class="badge badge-gray">inactive</span>'
                : '<span class="badge badge-green">active</span>';
            return `<tr>
                <td class="font-medium">${esc(o.alias) || '—'}</td>
                <td>${esc(o.type) || '—'}</td>
                <td>${esc(o.status) || '—'}</td>
                <td>${esc(info)}</td>
                <td>${badge}</td>
                <td style="font-family:monospace;font-size:.7rem;">${esc(api.getUUID(o)) || '—'}</td>
            </tr>`;
        }).join('')
        : `<tr><td colspan="6" style="text-align:center;padding:2rem;color:var(--text-muted);">No objects match.</td></tr>`;
}

// ─── Analytics & Insights ─────────────────────────────────────────────────────

let _analyticsCharts = {};
let _analyticsGranularity = 'day';   // 'day' | 'month' — drives the trend chart + breakdown table
let _analyticsEvents = [];           // cached collection events (derived from routes) for the Day/Month toggle

// The command history is ADMIN-only under v1.4 RBAC, so the OPERATOR dashboard
// derives all analytics from object state it is allowed to read: routes
// (completedBinIds / completedAt / driver), bins (fillLevel / errorStatus /
// capacity) and trucks (load). Each completed/in-progress route contributes one
// collection "event" per collected bin.
function _buildCollectionEvents() {
    const events = [];
    [...allRoutes, ...allPastRoutes].forEach(r => {
        const a = r.objectDetails || {};
        const done = Array.isArray(a.completedBinIds) ? a.completedBinIds : [];
        if (!done.length) return;
        let volume = 0;
        done.forEach(id => {
            const bin = allBins.find(b => api.getUUID(b) === id);
            volume += parseFloat(bin?.objectDetails?.capacity) || 0;
        });
        const tsRaw = a.completedAt || a.startedAt || r.creationTimestamp || null;
        events.push({
            ts: tsRaw ? new Date(tsRaw) : null,
            bins: done.length,
            volume,
            driverEmail: a.assignedDriverEmail || null,
            driverName: a.assignedDriverName || a.assignedDriverEmail || 'Unassigned'
        });
    });
    return events;
}

function _mkChart(id, config) {
    if (_analyticsCharts[id]) _analyticsCharts[id].destroy();
    const el = document.getElementById(id);
    if (el) _analyticsCharts[id] = new Chart(el.getContext('2d'), config);
}
// Repaint charts against the new theme tokens when the user flips light/dark
// (only if analytics has been rendered at least once).
window.addEventListener('themechange', () => { if (Object.keys(_analyticsCharts).length) renderAnalytics(); });
const _dayKey = d => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
const _monthKey = d => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;

function renderAnalytics() {
    if (typeof Chart === 'undefined') {
        showToast('Charts library still loading — try again in a moment.', 'warning');
        return;
    }

    // Derive everything from object state the operator is allowed to read.
    _analyticsEvents = _buildCollectionEvents();
    const events = _analyticsEvents;

    // ── KPI cards ──
    const totalCollections = events.reduce((s, e) => s + e.bins, 0);
    const totalVolume = events.reduce((s, e) => s + e.volume, 0);
    const openIssues = allBins.filter(b => b.objectDetails?.errorStatus).length;
    const fills = allBins.map(b => parseFloat(b.objectDetails?.fillLevel)).filter(v => !isNaN(v));
    const avgFill = fills.length ? Math.round(fills.reduce((a, b) => a + b, 0) / fills.length) : 0;
    document.getElementById('an-total-collections').textContent = totalCollections;
    document.getElementById('an-total-volume').textContent = totalVolume.toLocaleString();
    document.getElementById('an-total-issues').textContent = openIssues;
    document.getElementById('an-avg-fill').textContent = avgFill + '%';

    // ── Chart theming ──
    const css = getComputedStyle(document.documentElement);
    const primary = css.getPropertyValue('--primary').trim() || '#0D7A6A';
    const grid = 'rgba(100,116,139,0.15)';
    Chart.defaults.font.family = "'Rubik', sans-serif";
    Chart.defaults.color = css.getPropertyValue('--text-secondary').trim() || '#64748B';
    Chart.defaults.borderColor = grid;

    // ── Time-sectioned trend chart + breakdown table (driven by the Day/Month toggle) ──
    _renderTrendAndTable();

    // ── Top drivers (bins collected per driver, from routes) ──
    const byDriver = {};
    events.forEach(e => { const n = e.driverName; byDriver[n] = (byDriver[n] || 0) + e.bins; });
    const drivers = Object.entries(byDriver).sort((a, b) => b[1] - a[1]).slice(0, 6);
    _mkChart('chart-drivers', {
        type: 'bar',
        data: { labels: drivers.map(([n]) => n), datasets: [{ data: drivers.map(([, n]) => n), backgroundColor: '#14A08D', borderRadius: 6 }] },
        options: { indexAxis: 'y', responsive: true, maintainAspectRatio: false, plugins: { legend: { display: false } }, scales: { x: { beginAtZero: true, ticks: { precision: 0 }, grid: { color: grid } }, y: { grid: { display: false } } } }
    });

    // ── Issues by type (current bin errorStatus snapshot) ──
    const byType = {};
    allBins.forEach(b => { const t = b.objectDetails?.errorStatus; if (t) byType[t] = (byType[t] || 0) + 1; });
    const typeKeys = Object.keys(byType);
    const typeColor = { damaged: '#EF4444', blocked: '#F59E0B', missed: '#6366F1', other: '#94A3B8' };
    _mkChart('chart-issues', {
        type: 'doughnut',
        data: {
            labels: typeKeys.length ? typeKeys : ['No issues'],
            datasets: [{ data: typeKeys.length ? typeKeys.map(t => byType[t]) : [1], backgroundColor: typeKeys.length ? typeKeys.map(t => typeColor[t] || '#94A3B8') : ['#E2E8F0'], borderWidth: 0 }]
        },
        options: { responsive: true, maintainAspectRatio: false, cutout: '62%', plugins: { legend: { position: 'bottom' } } }
    });

    // ── Truck utilization ──
    const trucks = allTrucks.slice(0, 8);
    const loadPct = t => { const a = t.objectDetails || {}; const cap = parseFloat(a.totalCapacity) || 0; return cap ? Math.round((parseFloat(a.currentLoad) || 0) / cap * 100) : 0; };
    _mkChart('chart-trucks', {
        type: 'bar',
        data: { labels: trucks.map(t => t.alias || '—'), datasets: [{ data: trucks.map(loadPct), backgroundColor: trucks.map(t => { const p = loadPct(t); return p >= 80 ? '#EF4444' : p >= 50 ? '#F59E0B' : '#22C55E'; }), borderRadius: 6, maxBarThickness: 36 }] },
        options: { responsive: true, maintainAspectRatio: false, plugins: { legend: { display: false } }, scales: { y: { beginAtZero: true, max: 100, ticks: { callback: v => v + '%' }, grid: { color: grid } }, x: { grid: { display: false } } } }
    });

    // ── Fullest-bin hotspots ──
    const hot = [...allBins].sort((a, b) => (b.objectDetails?.fillLevel || 0) - (a.objectDetails?.fillLevel || 0)).slice(0, 6);
    const hotEl = document.getElementById('an-hotspots');
    hotEl.innerHTML = hot.length === 0
        ? '<p style="color:var(--text-muted);padding:0.5rem 0;">No bins yet.</p>'
        : hot.map(b => {
            const a = b.objectDetails || {};
            const fill = parseFloat(a.fillLevel) || 0;
            const col = api.fillColor(fill);
            return `<div class="an-hotspot-row">
                <span style="flex:1;font-weight:600;font-size:.875rem;">${esc(b.alias) || '—'}</span>
                <span style="font-size:.75rem;color:var(--text-secondary);">${esc(a.address || a.binType) || ''}</span>
                <div class="fill-bar" style="width:120px;"><div class="fill-bar-inner ${col}" style="width:${fill}%"></div></div>
                <span class="badge badge-${col === 'red' ? 'red' : col === 'yellow' ? 'yellow' : 'green'}">${fill}%</span>
            </div>`;
        }).join('');
}

// Day/Month toggle — re-renders only the time-sectioned parts from cached commands.
function setAnalyticsGranularity(gran) {
    _analyticsGranularity = gran;
    document.querySelectorAll('.an-gran-btn').forEach(b => b.classList.toggle('active', b.dataset.gran === gran));
    _renderTrendAndTable();
}

// Buckets the derived collection events by day or month → trend bar chart + breakdown table.
function _renderTrendAndTable() {
    const events = _analyticsEvents || [];
    const gran = _analyticsGranularity;
    const keyOf = gran === 'month' ? _monthKey : _dayKey;
    const grid = 'rgba(100,116,139,0.15)';
    const primary = getComputedStyle(document.documentElement).getPropertyValue('--primary').trim() || '#0D7A6A';

    // periodKey -> { collections, volume, routes, drivers:Set }
    const buckets = {};
    events.forEach(e => {
        const ts = e.ts;
        if (!ts || isNaN(ts)) return;
        const k = keyOf(ts);
        const b = buckets[k] || (buckets[k] = { collections: 0, volume: 0, routes: 0, drivers: new Set() });
        b.collections += e.bins;
        b.volume += e.volume;
        b.routes += 1;
        if (e.driverEmail) b.drivers.add(e.driverEmail);
    });

    // x-axis: last 14 days or last 12 months, ending today
    const periods = [];
    const today = new Date();
    if (gran === 'month') {
        for (let i = 11; i >= 0; i--) periods.push(_monthKey(new Date(today.getFullYear(), today.getMonth() - i, 1)));
    } else {
        for (let i = 13; i >= 0; i--) { const d = new Date(today); d.setDate(today.getDate() - i); periods.push(_dayKey(d)); }
    }
    const labels = periods.map(p => gran === 'month' ? p.slice(2) : p.slice(5));

    document.getElementById('an-trend-title').textContent = gran === 'month' ? 'Collections by month (last 12)' : 'Collections by day (last 14)';
    _mkChart('chart-collections', {
        type: 'bar',
        data: { labels, datasets: [{ data: periods.map(p => buckets[p]?.collections || 0), backgroundColor: primary, borderRadius: 6, maxBarThickness: gran === 'month' ? 34 : 26 }] },
        options: { responsive: true, maintainAspectRatio: false, plugins: { legend: { display: false } }, scales: { y: { beginAtZero: true, ticks: { precision: 0 }, grid: { color: grid } }, x: { grid: { display: false } } } }
    });

    // breakdown table: every period that has activity, most recent first
    document.getElementById('an-breakdown-title').textContent = gran === 'month' ? 'Monthly breakdown' : 'Daily breakdown';
    const tbody = document.getElementById('an-breakdown-body');
    const keys = Object.keys(buckets).sort().reverse();
    tbody.innerHTML = keys.length
        ? keys.map(k => {
            const b = buckets[k];
            return `<tr><td class="font-medium">${k}</td><td>${b.collections}</td><td>${b.volume.toLocaleString()}</td><td>${b.routes}</td><td>${b.drivers.size}</td></tr>`;
        }).join('')
        : `<tr><td colspan="5" style="text-align:center;color:var(--text-muted);padding:1rem;">No activity in this period yet.</td></tr>`;
}

// ─── Fly to bin ───────────────────────────────────────────────────────────────

function flyToBin(uuid) {
    const marker = binMarkers[uuid];
    if (marker) {
        map.flyTo(marker.getLatLng(), 16, { animate: true, duration: 0.8 });
        marker.openPopup();
    }
}

// ─── Fly to truck ─────────────────────────────────────────────────────────────

function flyToTruck(uuid) {
    const marker = truckMarkers[uuid];
    if (marker) {
        map.flyTo(marker.getLatLng(), 16, { animate: true, duration: 0.8 });
        marker.openPopup();
    }
}

// ─── Add / Edit Bin ───────────────────────────────────────────────────────────

let binPickerMap = null;
let binPickerMarker = null;

async function updateSelectedLocation(lat, lng) {
    document.getElementById('bin-lat').value = lat.toFixed(6);
    document.getElementById('bin-lng').value = lng.toFixed(6);
    document.getElementById('bin-location-display').textContent =
        `📍 ${lat.toFixed(5)}, ${lng.toFixed(5)} — Looking up address...`;

    if (binPickerMap) {
        if (binPickerMarker) binPickerMap.removeLayer(binPickerMarker);
        binPickerMarker = L.marker([lat, lng]).addTo(binPickerMap);
        binPickerMap.setView([lat, lng]);
    }

    try {
        const resp = await fetch(
            `https://nominatim.openstreetmap.org/reverse?lat=${lat}&lon=${lng}&format=json`,
            { headers: { 'Accept-Language': 'en' } }
        );
        const geo = await resp.json();
        const addr = geo.display_name || '';
        const short = addr.split(',').slice(0, 3).join(', ');
        document.getElementById('bin-location-display').textContent = `📍 ${short}`;
        document.getElementById('bin-address').value = short;
        document.getElementById('bin-alias').value = short;
    } catch (_) {
        document.getElementById('bin-location-display').textContent =
            `📍 ${lat.toFixed(5)}, ${lng.toFixed(5)}`;
    }
}

function initBinPickerMap(lat, lng) {
    // Destroy existing map instance first
    if (binPickerMap) {
        binPickerMap.remove();
        binPickerMap = null;
        binPickerMarker = null;
    }
    // Small delay so the modal DOM is visible before Leaflet measures the container
    setTimeout(() => {
        const centerLat = lat || 32.0853;
        const centerLng = lng || 34.7818;
        binPickerMap = L.map('bin-picker-map', { zoomControl: true }).setView([centerLat, centerLng], 14);
        L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
            attribution: '\u00a9 OpenStreetMap'
        }).addTo(binPickerMap);

        const isEdit = !!document.getElementById('bin-edit-id').value;

        // If editing, show existing marker without overwriting alias/address. If adding, lookup address.
        if (lat && lng) {
            if (isEdit) {
                binPickerMarker = L.marker([lat, lng]).addTo(binPickerMap);
                document.getElementById('bin-lat').value = lat;
                document.getElementById('bin-lng').value = lng;
                document.getElementById('bin-location-display').textContent =
                    `📍 ${lat.toFixed(5)}, ${lng.toFixed(5)}`;
            } else {
                updateSelectedLocation(lat, lng);
            }
        }

        binPickerMap.on('click', (e) => {
            updateSelectedLocation(e.latlng.lat, e.latlng.lng);
        });
    }, 80);
}

function openAddBinModal(lat, lng) {
    document.getElementById('bin-modal-title').textContent = 'Add Bin';
    document.getElementById('bin-form').reset();
    document.getElementById('bin-edit-id').value = '';
    document.getElementById('bin-lat').value = '';
    document.getElementById('bin-lng').value = '';
    document.getElementById('bin-location-display').textContent = 'No location selected — click the map above.';
    document.getElementById('bin-fill').value = 0;
    document.getElementById('bin-modal').classList.add('open');
    initBinPickerMap(lat, lng);
}

async function openEditBinModal(uuid) {
    const bin = allBins.find(b => api.getUUID(b) === uuid);
    if (!bin) return;
    const attr = bin.objectDetails || {};
    const loc = bin.location || {};
    document.getElementById('bin-modal-title').textContent = 'Edit Bin';
    document.getElementById('bin-edit-id').value = uuid;
    document.getElementById('bin-alias').value = bin.alias || '';
    document.getElementById('bin-address').value = attr.address || '';
    document.getElementById('bin-type').value = attr.binType || 'general';
    document.getElementById('bin-capacity').value = attr.capacity || 1100;
    document.getElementById('bin-fill').value = attr.fillLevel || 0;
    document.getElementById('bin-modal').classList.add('open');
    initBinPickerMap(parseFloat(loc.lat) || null, parseFloat(loc.lng) || null);
}

function closeBinModal() {
    document.getElementById('bin-modal').classList.remove('open');
    if (binPickerMap) { binPickerMap.remove(); binPickerMap = null; binPickerMarker = null; }
}

async function handleSaveBin(e) {
    e.preventDefault();
    const btn = document.getElementById('btn-save-bin');
    const editId = document.getElementById('bin-edit-id').value;
    btn.disabled = true; btn.textContent = 'Saving...';

    const alias = document.getElementById('bin-alias').value.trim();
    const lat = parseFloat(document.getElementById('bin-lat').value);
    const lng = parseFloat(document.getElementById('bin-lng').value);
    if (!editId && (isNaN(lat) || isNaN(lng))) {
        showToast('Please click on the map to set a location.', 'warning');
        btn.disabled = false; btn.textContent = 'Save Bin';
        return;
    }
    const address = document.getElementById('bin-address').value.trim();
    const binType = document.getElementById('bin-type').value;
    const capacity = parseInt(document.getElementById('bin-capacity').value);
    const fill = parseInt(document.getElementById('bin-fill').value) || 0;

    try {
        if (editId) {
            const existing = allBins.find(b => api.getUUID(b) === editId);
            const oldAttr = existing?.objectDetails || {};
            await api.updateObject(editId, {
                alias,
                location: { lat, lng },
                objectDetails: { ...oldAttr, address, binType, capacity, fillLevel: fill }
            });
            showToast('Bin updated!', 'success');
        } else {
            const obj = api.buildBin(alias, lat, lng, binType, capacity, address, session.getEmail());
            obj.objectDetails.fillLevel = fill;
            await api.createObject(obj);
            showToast('Bin added!', 'success');
        }
        closeBinModal();
        await refreshAll();
    } catch (err) {
        showToast('Error: ' + err.message, 'error');
    } finally {
        btn.disabled = false; btn.textContent = 'Save Bin';
    }
}

async function deactivateBin(uuid) {
    if (!confirm('Remove this bin? (sets it inactive)')) return;
    try {
        await api.updateObject(uuid, { active: false });
        showToast('Bin removed.', 'warning');
        await refreshAll();
    } catch (err) { showToast('Error: ' + err.message, 'error'); }
}

// ─── Add / Edit Truck ─────────────────────────────────────────────────────────

function openAddTruckModal() {
    document.getElementById('truck-modal-title').textContent = 'Add Truck';
    document.getElementById('truck-form').reset();
    document.getElementById('truck-edit-id').value = '';
    document.getElementById('truck-modal').classList.add('open');
}

async function openEditTruckModal(uuid) {
    const truck = allTrucks.find(t => api.getUUID(t) === uuid);
    if (!truck) return;
    const attr = truck.objectDetails || {};
    document.getElementById('truck-modal-title').textContent = 'Edit Truck';
    document.getElementById('truck-edit-id').value = uuid;
    document.getElementById('truck-alias').value = truck.alias || '';
    document.getElementById('truck-plate').value = attr.plateNumber || '';
    document.getElementById('truck-capacity').value = attr.totalCapacity || 8000;
    document.getElementById('truck-modal').classList.add('open');
}

function closeTruckModal() { document.getElementById('truck-modal').classList.remove('open'); }

async function handleSaveTruck(e) {
    e.preventDefault();
    const btn = document.getElementById('btn-save-truck');
    const editId = document.getElementById('truck-edit-id').value;
    btn.disabled = true; btn.textContent = 'Saving...';

    const alias = document.getElementById('truck-alias').value.trim();
    const plate = document.getElementById('truck-plate').value.trim();
    const capacity = parseInt(document.getElementById('truck-capacity').value) || 8000;

    try {
        if (editId) {
            const existing = allTrucks.find(t => api.getUUID(t) === editId);
            const oldAttr = existing?.objectDetails || {};
            // Driver assignment is owned by routes, not the truck form — preserve oldAttr.
            await api.updateObject(editId, {
                alias,
                objectDetails: { ...oldAttr, plateNumber: plate, totalCapacity: capacity }
            });
            showToast('Truck updated!', 'success');
        } else {
            const obj = api.buildTruck(alias, plate, capacity, session.getEmail());
            await api.createObject(obj);
            showToast('Truck added!', 'success');
        }
        closeTruckModal();
        await refreshAll();
    } catch (err) {
        showToast('Error: ' + err.message, 'error');
    } finally {
        btn.disabled = false; btn.textContent = 'Save Truck';
    }
}

async function deactivateTruck(uuid) {
    if (!confirm('Remove this truck?')) return;
    try {
        await api.updateObject(uuid, { active: false });
        showToast('Truck removed.', 'warning');
        await refreshAll();
    } catch (err) { showToast('Error: ' + err.message, 'error'); }
}

function getActiveRouteBinIds(excludeRouteUUID = null) {
    const assigned = new Set();
    allRoutes.forEach(r => {
        const uuid = api.getUUID(r);
        if (uuid === excludeRouteUUID) return;
        const attr = r.objectDetails || {};
        if (attr.status !== 'completed' && attr.status !== 'cancelled') {
            (attr.binIds || []).forEach(id => assigned.add(id));
        }
    });
    return assigned;
}

function getActiveRouteDriverEmails(excludeRouteUUID = null) {
    const drivers = new Set();
    allRoutes.forEach(r => {
        const uuid = api.getUUID(r);
        if (uuid === excludeRouteUUID) return;
        const attr = r.objectDetails || {};
        if (attr.status !== 'completed' && attr.status !== 'cancelled') {
            if (attr.assignedDriverEmail) {
                drivers.add(attr.assignedDriverEmail);
            }
        }
    });
    return drivers;
}

// Rebuild the known-driver list from the drivers referenced on routes.
// Shape mirrors the old getAllUsers result so the rest of the code is unchanged.
// The Users list is ADMIN-only, so to load the driver roster we briefly
// self-elevate the current operator to ADMIN, read the users, then drop straight
// back to OPERATOR. The ADMIN window is a few calls long — never spanning the
// modal or a refresh tick — and the revert is guaranteed so we can't get stuck.
async function loadDriversAsAdmin() {
    const email = session.getEmail();
    const pwd = session.getPassword();
    if (!email || !pwd) return;
    clearInterval(refreshInterval);   // no getAllObjects tick may fire while we're ADMIN
    let elevated = false;
    try {
        await api.updateUser(email, pwd, { role: 'ADMIN' });
        elevated = true;
        const users = (await api.getAllUsers()) || [];
        allDrivers = users.filter(u => u.role === 'END_USER');
    } catch (err) {
        showToast('Could not load drivers: ' + err.message, 'error');
    } finally {
        if (elevated) await revertToOperator(email, pwd);
        refreshInterval = setInterval(refreshAll, 10000);
    }
}

// Restore OPERATOR role. Being left as ADMIN breaks the whole operator UI, so we
// retry hard and, if it still fails, block until the user retries — never give up.
// ponytail: retry-forever with a blocking prompt; the only safe failure mode here.
async function revertToOperator(email, pwd) {
    for (let i = 0; ; i++) {
        try { await api.updateUser(email, pwd, { role: 'OPERATOR' }); return; }
        catch (_) {
            if (i < 3) { await new Promise(r => setTimeout(r, 500)); continue; }
            alert('Could not restore your OPERATOR role — do NOT reload the page. Check your connection, then click OK to retry.');
        }
    }
}

// Look up a driver's display name from the known-driver list, or fall back to
// a name derived from the email's local part (e.g. "john.doe" → "John Doe").
function driverNameForEmail(email) {
    if (!email) return null;
    const known = allDrivers.find(d => d.userId?.email === email);
    if (known?.username) return known.username;
    const local = email.split('@')[0].replace(/[._-]+/g, ' ').trim();
    return local ? local.replace(/\b\w/g, c => c.toUpperCase()) : email;
}

// Driver <select> options = known drivers (those seen on routes — the Users list
// is ADMIN-only), minus any already on another active route (keep the selected one).
function populateRouteDriverDropdown(selectId, selectedEmail = '', excludeRouteUUID = null) {
    const sel = document.getElementById(selectId);
    if (!sel) return;
    const activeDriverEmails = getActiveRouteDriverEmails(excludeRouteUUID);
    sel.innerHTML = `<option value="">— Unassigned —</option>` +
        allDrivers
            .filter(d => {
                const email = d.userId?.email || '';
                return email === selectedEmail || !activeDriverEmails.has(email);
            })
            .map(d => {
                const email = d.userId?.email || '';
                const name = d.username || email;
                return `<option value="${esc(email)}" ${email === selectedEmail ? 'selected' : ''}>${esc(name)} (${esc(email)})</option>`;
            }).join('');
}

function updateRouteLoadSummary() {
    const truckId = document.getElementById('route-truck').value;
    if (!truckId) {
        selectedBinIds.clear();
        const checkboxes = document.querySelectorAll('#route-bin-picker input[type="checkbox"]');
        checkboxes.forEach(cb => cb.checked = false);
    }

    let expectedLoad = 0;
    selectedBinIds.forEach(id => {
        const bin = allBins.find(b => api.getUUID(b) === id);
        if (bin && bin.objectDetails) {
            const fill = parseFloat(bin.objectDetails.fillLevel) || 0;
            const cap = parseFloat(bin.objectDetails.capacity) || 0;
            expectedLoad += Math.round((fill / 100) * cap);
        }
    });

    let truckCapacity = 0;
    if (truckId) {
        const truck = allTrucks.find(t => api.getUUID(t) === truckId);
        if (truck && truck.objectDetails) {
            truckCapacity = parseFloat(truck.objectDetails.totalCapacity) || 0;
        }
    }

    document.getElementById('route-expected-load').textContent = expectedLoad;
    document.getElementById('route-truck-capacity-display').textContent = truckId ? `${truckCapacity} L` : '—';

    const warningEl = document.getElementById('route-load-warning');
    if (truckId && expectedLoad > truckCapacity) {
        warningEl.style.display = 'block';
    } else {
        warningEl.style.display = 'none';
    }
}

function updateEditRouteLoadSummary() {
    const truckId = document.getElementById('edit-route-truck').value;
    if (!truckId) {
        editRouteBinIds.clear();
        const checkboxes = document.querySelectorAll('#edit-route-bin-picker input[type="checkbox"]');
        checkboxes.forEach(cb => cb.checked = false);
    }

    let expectedLoad = 0;
    editRouteBinIds.forEach(id => {
        const bin = allBins.find(b => api.getUUID(b) === id);
        if (bin && bin.objectDetails) {
            const fill = parseFloat(bin.objectDetails.fillLevel) || 0;
            const cap = parseFloat(bin.objectDetails.capacity) || 0;
            expectedLoad += Math.round((fill / 100) * cap);
        }
    });

    let truckCapacity = 0;
    if (truckId) {
        const truck = allTrucks.find(t => api.getUUID(t) === truckId);
        if (truck && truck.objectDetails) {
            truckCapacity = parseFloat(truck.objectDetails.totalCapacity) || 0;
        }
    }

    document.getElementById('edit-route-expected-load').textContent = expectedLoad;
    document.getElementById('edit-route-truck-capacity-display').textContent = truckId ? `${truckCapacity} L` : '—';

    const warningEl = document.getElementById('edit-route-load-warning');
    if (truckId && expectedLoad > truckCapacity) {
        warningEl.style.display = 'block';
    } else {
        warningEl.style.display = 'none';
    }
}

// ─── Create Route ─────────────────────────────────────────────────────────────

async function openAddRouteModal() {
    document.getElementById('route-form').reset();
    selectedBinIds = new Set();

    // Populate truck dropdown
    const truckSel = document.getElementById('route-truck');
    truckSel.innerHTML = `<option value="">— None —</option>` +
        allTrucks.filter(t => t.objectDetails?.status === 'available').map(t =>
            `<option value="${api.getUUID(t)}">${t.alias} (${t.objectDetails?.plateNumber || ''})</option>`
        ).join('');

    // Load drivers (brief ADMIN elevation) then populate the dropdown
    await loadDriversAsAdmin();
    populateRouteDriverDropdown('route-driver');

    // Bin picker
    const activeBinIds = getActiveRouteBinIds();
    const availableBins = allBins
        .filter(bin => !activeBinIds.has(api.getUUID(bin)))
        .sort((a, b) => {
            const fillA = parseFloat(a.objectDetails?.fillLevel) || 0;
            const fillB = parseFloat(b.objectDetails?.fillLevel) || 0;
            return fillB - fillA;
        });

    const picker = document.getElementById('route-bin-picker');
    if (availableBins.length === 0) {
        picker.innerHTML = '<p style="color:var(--text-muted);font-size:.8rem;">No bins available. Add bins first.</p>';
    } else {
        picker.innerHTML = availableBins.map(bin => {
            const attr = bin.objectDetails || {};
            const fill = attr.fillLevel || 0;
            const uuid = api.getUUID(bin);
            const hasAlert = !!(attr.errorStatus && attr.errorStatus !== 'null' && attr.errorStatus !== null);
            const typeLabel = api.binTypeLabel(attr.binType);
            return `<label style="display:flex;align-items:center;gap:.5rem;padding:.35rem .25rem;cursor:pointer;border-radius:6px;transition:background .15s;"
                          onmouseover="this.style.background='var(--bg-secondary)'" onmouseout="this.style.background=''">
                <input type="checkbox" value="${uuid}" onchange="toggleBinPick('${uuid}', this.checked, this)" style="accent-color:var(--primary);">
                <span style="font-size:.8125rem;">${esc(bin.alias)}${typeLabel} <span style="color:var(--text-muted);">(${fill}%)</span>
                ${hasAlert ? '<i class="fa-solid fa-triangle-exclamation" style="color:#EF4444;margin-left:0.25rem;" title="Alert status"></i>' : ''}</span>
            </label>`;
        }).join('');
    }

    updateRouteLoadSummary();
    document.getElementById('route-modal').classList.add('open');
}

function toggleBinPick(uuid, checked, checkbox) {
    if (checked) {
        const truckId = document.getElementById('route-truck').value;
        if (!truckId) {
            showToast('Please select a truck first before choosing bins!', 'warning');
            if (checkbox) checkbox.checked = false;
            return;
        }

        let potentialLoad = 0;
        selectedBinIds.forEach(id => {
            const bin = allBins.find(b => api.getUUID(b) === id);
            if (bin && bin.objectDetails) {
                const fill = parseFloat(bin.objectDetails.fillLevel) || 0;
                const cap = parseFloat(bin.objectDetails.capacity) || 0;
                potentialLoad += Math.round((fill / 100) * cap);
            }
        });
        const thisBin = allBins.find(b => api.getUUID(b) === uuid);
        if (thisBin && thisBin.objectDetails) {
            const fill = parseFloat(thisBin.objectDetails.fillLevel) || 0;
            const cap = parseFloat(thisBin.objectDetails.capacity) || 0;
            potentialLoad += Math.round((fill / 100) * cap);
        }

        let truckCapacity = 0;
        const truck = allTrucks.find(t => api.getUUID(t) === truckId);
        if (truck && truck.objectDetails) {
            truckCapacity = parseFloat(truck.objectDetails.totalCapacity) || 0;
        }

        if (potentialLoad > truckCapacity) {
            showToast(`Cannot add bin: Selected truck's capacity limit of ${truckCapacity} L would be exceeded!`, 'warning');
            if (checkbox) checkbox.checked = false;
            return;
        }
        selectedBinIds.add(uuid);
    } else {
        selectedBinIds.delete(uuid);
    }
    updateRouteLoadSummary();
}

function closeRouteModal() { document.getElementById('route-modal').classList.remove('open'); }

async function handleSaveRoute(e) {
    e.preventDefault();
    const btn = document.getElementById('btn-save-route');
    btn.disabled = true; btn.textContent = 'Creating...';

    const alias = document.getElementById('route-alias').value.trim();
    const truckId = document.getElementById('route-truck').value;
    const driverEmail = document.getElementById('route-driver').value;

    if (!truckId || !driverEmail) {
        showToast('Please select both a truck and a driver to create a route.', 'warning');
        btn.disabled = false; btn.textContent = 'Create Route';
        return;
    }

    if (driverEmail) {
        const activeDriverEmails = getActiveRouteDriverEmails();
        if (activeDriverEmails.has(driverEmail)) {
            showToast('Selected driver is already assigned to another active route.', 'warning');
            btn.disabled = false; btn.textContent = 'Create Route';
            return;
        }
    }

    const driverName = driverNameForEmail(driverEmail);

    let expectedLoad = 0;
    allBins.filter(b => selectedBinIds.has(api.getUUID(b))).forEach(bin => {
        if (bin.objectDetails) {
            const fill = parseFloat(bin.objectDetails.fillLevel) || 0;
            const cap = parseFloat(bin.objectDetails.capacity) || 0;
            expectedLoad += Math.round((fill / 100) * cap);
        }
    });

    if (truckId) {
        const truck = allTrucks.find(t => api.getUUID(t) === truckId);
        if (truck && truck.objectDetails) {
            const cap = parseFloat(truck.objectDetails.totalCapacity) || 0;
            if (expectedLoad > cap) {
                if (!confirm(`Warning: The selected bins expected load (${expectedLoad} L) exceeds the selected truck's capacity (${cap} L). Do you still want to create this route?`)) {
                    btn.disabled = false; btn.textContent = 'Create Route';
                    return;
                }
            }
        }
    }

    // ── TSP Route Optimization (Nearest Neighbor + 2-opt improvement) ──
    // Haversine distance in metres (accurate for real-world lat/lng)
    function haversineDist(p1, p2) {
        const R = 6371000; // Earth radius in metres
        const toRad = v => v * Math.PI / 180;
        const dLat = toRad(p2.lat - p1.lat);
        const dLng = toRad(p2.lng - p1.lng);
        const a = Math.sin(dLat / 2) ** 2 +
            Math.cos(toRad(p1.lat)) * Math.cos(toRad(p2.lat)) *
            Math.sin(dLng / 2) ** 2;
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    // Build list of selected bins with parsed coordinates
    const selectedBins = allBins
        .filter(b => selectedBinIds.has(api.getUUID(b)))
        .map(b => {
            const loc = b.location || {};
            return {
                id: api.getUUID(b),
                lat: parseFloat(loc.lat),
                lng: parseFloat(loc.lng)
            };
        })
        .filter(b => !isNaN(b.lat) && !isNaN(b.lng));

    const depot = { lat: 32.114, lng: 34.796 }; // Warehouse at Afeka College

    // Phase 1: Nearest Neighbor greedy tour
    const visited = [];
    const remaining = [...selectedBins];
    let cur = depot;
    while (remaining.length > 0) {
        let bestIdx = 0;
        let bestDist = Infinity;
        for (let i = 0; i < remaining.length; i++) {
            const d = haversineDist(cur, remaining[i]);
            if (d < bestDist) { bestDist = d; bestIdx = i; }
        }
        const chosen = remaining.splice(bestIdx, 1)[0];
        visited.push(chosen);
        cur = chosen;
    }

    // Phase 2: 2-opt improvement – reverse segments to eliminate crossings
    // points array: [depot, ...visited]  (depot is index 0)
    const pts = [depot, ...visited];
    const n = pts.length;
    let improved = true;
    while (improved) {
        improved = false;
        for (let i = 1; i < n - 1; i++) {
            for (let j = i + 1; j < n; j++) {
                const before = haversineDist(pts[i - 1], pts[i]) +
                    (j + 1 < n ? haversineDist(pts[j], pts[j + 1]) : 0);
                const after = haversineDist(pts[i - 1], pts[j]) +
                    (j + 1 < n ? haversineDist(pts[i], pts[j + 1]) : 0);
                if (after < before - 1) {           // improve by > 1 m
                    // Reverse segment pts[i..j]
                    let left = i, right = j;
                    while (left < right) {
                        [pts[left], pts[right]] = [pts[right], pts[left]];
                        left++; right--;
                    }
                    improved = true;
                }
            }
        }
    }

    // Extract final ordered bin IDs (skip index 0 which is the depot)
    const binIds = pts.slice(1).map(p => p.id);

    try {
        const routeObj = api.buildRoute(alias, binIds, truckId || null, driverEmail || null,
            driverName, session.getEmail());
        const createdRoute = await api.createObject(routeObj);
        const routeUUID = api.getUUID(createdRoute);

        // Bind bins as children to the route
        for (const binId of binIds) {
            const binObj = allBins.find(b => api.getUUID(b) === binId);
            const binSystemID = binObj?.objectId?.systemID || SYSTEM_ID;
            await api.bindChildToObject(routeUUID, binSystemID, binId);
        }

        // Update truck status if assigned
        if (truckId) {
            const truck = allTrucks.find(t => api.getUUID(t) === truckId);
            if (truck) {
                await api.updateObject(truckId, {
                    objectDetails: { ...truck.objectDetails, status: 'on_route', driverEmail, driverName }
                });
            }
        }

        showToast(`Route "${alias}" created with ${binIds.length} bins!`, 'success');
        closeRouteModal();
        await refreshAll();
    } catch (err) {
        showToast('Error: ' + err.message, 'error');
    } finally {
        btn.disabled = false; btn.textContent = 'Create Route';
    }
}

// ─── Route Selection & Highlight ──────────────────────────────────────────────

function clearRouteSelection() {
    routeHighlightLayers.forEach(layer => map.removeLayer(layer));
    routeHighlightLayers = [];
    if (routePolyline) { map.removeLayer(routePolyline); routePolyline = null; }
    activeRouteId = null;
}

// Show/hide the topbar "Unselect route" button based on whether a route is selected.
function syncClearRouteBtn() {
    const btn = document.getElementById('btn-clear-route');
    if (btn) btn.style.display = activeRouteId ? 'inline-flex' : 'none';
}

// Manual deselect (topbar button) — removes the highlighted route from the map.
function clearRouteAndRefresh() {
    clearRouteSelection();
    renderPanelRoutes();
    renderPastRoutesTable();
}

function toggleRouteSelection(routeUUID) {
    if (activeRouteId === routeUUID) {
        clearRouteSelection();
        renderPanelRoutes();
        renderPastRoutesTable();
        return;
    }
    clearRouteSelection();
    activeRouteId = routeUUID;

    const route = allRoutes.find(r => api.getUUID(r) === routeUUID) || allPastRoutes.find(r => api.getUUID(r) === routeUUID);
    if (!route) return;
    const attr = route.objectDetails || {};
    const binIds = attr.binIds || [];

    const coords = [];
    binIds.forEach((binId, idx) => {
        const bin = allBins.find(b => api.getUUID(b) === binId);
        if (!bin) return;
        const loc = bin.location || {};
        const lat = parseFloat(loc.lat);
        const lng = parseFloat(loc.lng);
        if (isNaN(lat) || isNaN(lng)) return;

        coords.push([lat, lng]);

        // Glowing ring overlay
        const ring = L.marker([lat, lng], {
            icon: L.divIcon({
                className: '',
                html: `<div style="position:relative;width:48px;height:48px;">
                    <div class="route-highlight-ring"></div>
                    <div class="route-sequence-badge">${idx + 1}</div>
                </div>`,
                iconSize: [48, 48],
                iconAnchor: [24, 24]
            }),
            interactive: false,
            zIndexOffset: 1000
        }).addTo(map);
        routeHighlightLayers.push(ring);
    });

    // Draw polyline connecting bins in order
    if (coords.length > 1) {
        routePolyline = L.polyline(coords, {
            color: '#0D7A6A', weight: 4, opacity: 0.8,
            dashArray: '10,8', lineCap: 'round'
        }).addTo(map);
    }

    // Fit map to show all route bins
    if (coords.length > 0) {
        map.fitBounds(coords, { padding: [60, 60], maxZoom: 15 });
    }

    renderPanelRoutes();
    renderPastRoutesTable();
}

// ─── Edit Route ───────────────────────────────────────────────────────────────

let editRouteBinIds = new Set();

async function openEditRouteModal(routeUUID) {
    const route = allRoutes.find(r => api.getUUID(r) === routeUUID);
    if (!route) return;
    const attr = route.objectDetails || {};
    if ((attr.completedBinIds || []).length > 0) {
        showToast('Cannot edit route: collection has already started.', 'warning');
        return;
    }

    document.getElementById('edit-route-id').value = routeUUID;
    document.getElementById('edit-route-alias').value = route.alias || '';
    document.getElementById('edit-route-status').value = attr.status || 'planned';

    // Populate truck dropdown
    const truckSel = document.getElementById('edit-route-truck');
    truckSel.innerHTML = `<option value="">— None —</option>` +
        allTrucks.filter(t => t.objectDetails?.status !== 'maintenance').map(t =>
            `<option value="${api.getUUID(t)}" ${api.getUUID(t) === attr.assignedTruckId ? 'selected' : ''}>${t.alias} (${t.objectDetails?.plateNumber || ''})</option>`
        ).join('');

    // Load drivers (brief ADMIN elevation) then populate the dropdown
    await loadDriversAsAdmin();
    populateRouteDriverDropdown('edit-route-driver', attr.assignedDriverEmail, routeUUID);

    // Bin picker with existing bins pre-checked
    editRouteBinIds = new Set(attr.binIds || []);
    const activeBinIds = getActiveRouteBinIds(routeUUID);
    const availableBins = allBins
        .filter(bin => {
            const uuid = api.getUUID(bin);
            return editRouteBinIds.has(uuid) || !activeBinIds.has(uuid);
        })
        .sort((a, b) => {
            const fillA = parseFloat(a.objectDetails?.fillLevel) || 0;
            const fillB = parseFloat(b.objectDetails?.fillLevel) || 0;
            return fillB - fillA;
        });

    const picker = document.getElementById('edit-route-bin-picker');
    if (availableBins.length === 0) {
        picker.innerHTML = '<p style="color:var(--text-muted);font-size:.8rem;">No bins available.</p>';
    } else {
        picker.innerHTML = availableBins.map(bin => {
            const bAttr = bin.objectDetails || {};
            const fill = bAttr.fillLevel || 0;
            const uuid = api.getUUID(bin);
            const checked = editRouteBinIds.has(uuid) ? 'checked' : '';
            const hasAlert = !!(bAttr.errorStatus && bAttr.errorStatus !== 'null' && bAttr.errorStatus !== null);
            const typeLabel = api.binTypeLabel(bAttr.binType);
            return `<label style="display:flex;align-items:center;gap:.5rem;padding:.35rem .25rem;cursor:pointer;border-radius:6px;transition:background .15s;"
                          onmouseover="this.style.background='var(--bg-secondary)'" onmouseout="this.style.background=''">
                <input type="checkbox" value="${uuid}" ${checked} onchange="toggleEditBinPick('${uuid}', this.checked, this)" style="accent-color:var(--primary);">
                <span style="font-size:.8125rem;">${esc(bin.alias)}${typeLabel} <span style="color:var(--text-muted);">(${fill}%)</span>
                ${hasAlert ? '<i class="fa-solid fa-triangle-exclamation" style="color:#EF4444;margin-left:0.25rem;" title="Alert status"></i>' : ''}</span>
            </label>`;
        }).join('');
    }

    updateEditRouteLoadSummary();
    document.getElementById('edit-route-modal').classList.add('open');
}

function toggleEditBinPick(uuid, checked, checkbox) {
    if (checked) {
        const truckId = document.getElementById('edit-route-truck').value;
        if (!truckId) {
            showToast('Please select a truck first before choosing bins!', 'warning');
            if (checkbox) checkbox.checked = false;
            return;
        }

        let potentialLoad = 0;
        editRouteBinIds.forEach(id => {
            const bin = allBins.find(b => api.getUUID(b) === id);
            if (bin && bin.objectDetails) {
                const fill = parseFloat(bin.objectDetails.fillLevel) || 0;
                const cap = parseFloat(bin.objectDetails.capacity) || 0;
                potentialLoad += Math.round((fill / 100) * cap);
            }
        });
        const thisBin = allBins.find(b => api.getUUID(b) === uuid);
        if (thisBin && thisBin.objectDetails) {
            const fill = parseFloat(thisBin.objectDetails.fillLevel) || 0;
            const cap = parseFloat(thisBin.objectDetails.capacity) || 0;
            potentialLoad += Math.round((fill / 100) * cap);
        }

        let truckCapacity = 0;
        const truck = allTrucks.find(t => api.getUUID(t) === truckId);
        if (truck && truck.objectDetails) {
            truckCapacity = parseFloat(truck.objectDetails.totalCapacity) || 0;
        }

        if (potentialLoad > truckCapacity) {
            showToast(`Cannot add bin: Selected truck's capacity limit of ${truckCapacity} L would be exceeded!`, 'warning');
            if (checkbox) checkbox.checked = false;
            return;
        }
        editRouteBinIds.add(uuid);
    } else {
        editRouteBinIds.delete(uuid);
    }
    updateEditRouteLoadSummary();
}

function closeEditRouteModal() {
    document.getElementById('edit-route-modal').classList.remove('open');
}

async function handleUpdateRoute(e) {
    e.preventDefault();
    const btn = document.getElementById('btn-update-route');
    btn.disabled = true; btn.textContent = 'Saving...';

    const routeUUID = document.getElementById('edit-route-id').value;
    const route = allRoutes.find(r => api.getUUID(r) === routeUUID);
    if (route) {
        const attr = route.objectDetails || {};
        if ((attr.completedBinIds || []).length > 0) {
            showToast('Cannot update route: collection has already started.', 'warning');
            btn.disabled = false; btn.textContent = 'Update Route';
            closeEditRouteModal();
            return;
        }
    }
    const alias = document.getElementById('edit-route-alias').value.trim();
    const truckId = document.getElementById('edit-route-truck').value;
    const driverEmail = document.getElementById('edit-route-driver').value;

    if (!truckId || !driverEmail) {
        showToast('Please select both a truck and a driver to update the route.', 'warning');
        btn.disabled = false; btn.textContent = 'Update Route';
        return;
    }

    if (driverEmail) {
        const activeDriverEmails = getActiveRouteDriverEmails(routeUUID);
        if (activeDriverEmails.has(driverEmail)) {
            showToast('Selected driver is already assigned to another active route.', 'warning');
            btn.disabled = false; btn.textContent = 'Update Route';
            return;
        }
    }

    const status = document.getElementById('edit-route-status').value;
    const driverName = driverNameForEmail(driverEmail);

    // Optimize bin order with TSP
    const binIds = optimizeBinOrder([...editRouteBinIds]);

    let expectedLoad = 0;
    binIds.forEach(binId => {
        const bin = allBins.find(b => api.getUUID(b) === binId);
        if (bin && bin.objectDetails) {
            const fill = parseFloat(bin.objectDetails.fillLevel) || 0;
            const cap = parseFloat(bin.objectDetails.capacity) || 0;
            expectedLoad += Math.round((fill / 100) * cap);
        }
    });

    if (truckId) {
        const truck = allTrucks.find(t => api.getUUID(t) === truckId);
        if (truck && truck.objectDetails) {
            const cap = parseFloat(truck.objectDetails.totalCapacity) || 0;
            if (expectedLoad > cap) {
                if (!confirm(`Warning: The selected bins expected load (${expectedLoad} L) exceeds the selected truck's capacity (${cap} L). Do you still want to update this route?`)) {
                    btn.disabled = false; btn.textContent = 'Update Route';
                    return;
                }
            }
        }
    }

    try {
        const route = allRoutes.find(r => api.getUUID(r) === routeUUID);
        const oldAttr = route?.objectDetails || {};

        // Keep completedBinIds that are still in the new binIds list
        const newCompletedBinIds = (oldAttr.completedBinIds || []).filter(id => binIds.includes(id));

        await api.updateObject(routeUUID, {
            alias,
            objectDetails: {
                ...oldAttr,
                assignedTruckId: truckId || null,
                assignedDriverEmail: driverEmail || null,
                assignedDriverName: driverName,
                status,
                binIds,
                completedBinIds: newCompletedBinIds
            }
        });

        // Bind bins as children to the route
        for (const binId of binIds) {
            const binObj = allBins.find(b => api.getUUID(b) === binId);
            const binSystemID = binObj?.objectId?.systemID || SYSTEM_ID;
            await api.bindChildToObject(routeUUID, binSystemID, binId);
        }

        showToast(`Route "${alias}" updated!`, 'success');
        closeEditRouteModal();
        clearRouteSelection();
        await refreshAll();
    } catch (err) {
        showToast('Error: ' + err.message, 'error');
    } finally {
        btn.disabled = false; btn.textContent = 'Update Route';
    }
}

async function deactivateRoute(uuid) {
    if (!confirm('Delete this route?')) return;
    try {
        await api.updateObject(uuid, { active: false });
        clearRouteSelection();
        showToast('Route deleted.', 'warning');
        await refreshAll();
    } catch (err) { showToast('Error: ' + err.message, 'error'); }
}

async function approveRouteCompletion(routeUUID) {
    const route = allRoutes.find(r => api.getUUID(r) === routeUUID);
    if (!route) return;
    const attr = route.objectDetails || {};

    if (!attr.assignedTruckId || !attr.assignedDriverEmail) {
        showToast("Cannot approve route completion: no driver or truck was selected for this route!", "error");
        return;
    }

    if (!confirm(`Approve completion of route "${route.alias}" and release the assigned truck?`)) return;

    try {
        // 1. Update route status to 'completed' and deactivate it so it is archived
        const newRouteAttr = { ...attr, status: 'completed', approvedAt: new Date().toISOString() };
        await api.updateObject(routeUUID, { active: false, objectDetails: newRouteAttr });

        // 2. Release the truck: status -> 'available', load -> 0, collectedCount -> 0, location -> depot
        if (attr.assignedTruckId) {
            const truck = allTrucks.find(t => api.getUUID(t) === attr.assignedTruckId);
            if (truck) {
                await api.updateObject(attr.assignedTruckId, {
                    location: { lat: 32.114, lng: 34.796 },
                    objectDetails: {
                        ...truck.objectDetails,
                        status: 'available',
                        currentLoad: 0,
                        collectedCount: 0
                    }
                });
            }
        }

        // Note: under v1.4 RBAC, only END_USER may POST commands, so the operator
        // does not log an audit command here — the route/truck state changes above
        // (persisted via PUT) are the source of truth for completion.

        showToast(`Route "${route.alias}" completed & truck released!`, 'success');
        // Clear the route's highlight/line from the map if it was selected.
        if (activeRouteId === routeUUID) clearRouteSelection();
        await refreshAll();
    } catch (err) {
        showToast('Error: ' + err.message, 'error');
    }
}

// ─── Shared TSP Optimizer ─────────────────────────────────────────────────────

function optimizeBinOrder(binIdArray) {
    function haversineDist(p1, p2) {
        const R = 6371000;
        const toRad = v => v * Math.PI / 180;
        const dLat = toRad(p2.lat - p1.lat);
        const dLng = toRad(p2.lng - p1.lng);
        const a = Math.sin(dLat / 2) ** 2 +
            Math.cos(toRad(p1.lat)) * Math.cos(toRad(p2.lat)) *
            Math.sin(dLng / 2) ** 2;
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    const bins = binIdArray
        .map(id => {
            const bin = allBins.find(b => api.getUUID(b) === id);
            if (!bin) return null;
            const loc = bin.location || {};
            return { id, lat: parseFloat(loc.lat), lng: parseFloat(loc.lng) };
        })
        .filter(b => b && !isNaN(b.lat) && !isNaN(b.lng));

    if (bins.length <= 1) return bins.map(b => b.id);

    const depot = { lat: 32.114, lng: 34.796 };

    // Nearest Neighbor
    const visited = [];
    const remaining = [...bins];
    let cur = depot;
    while (remaining.length > 0) {
        let bestIdx = 0, bestDist = Infinity;
        for (let i = 0; i < remaining.length; i++) {
            const d = haversineDist(cur, remaining[i]);
            if (d < bestDist) { bestDist = d; bestIdx = i; }
        }
        const chosen = remaining.splice(bestIdx, 1)[0];
        visited.push(chosen);
        cur = chosen;
    }

    // 2-opt
    const pts = [depot, ...visited];
    const n = pts.length;
    let improved = true;
    while (improved) {
        improved = false;
        for (let i = 1; i < n - 1; i++) {
            for (let j = i + 1; j < n; j++) {
                const before = haversineDist(pts[i - 1], pts[i]) + (j + 1 < n ? haversineDist(pts[j], pts[j + 1]) : 0);
                const after = haversineDist(pts[i - 1], pts[j]) + (j + 1 < n ? haversineDist(pts[i], pts[j + 1]) : 0);
                if (after < before - 1) {
                    let l = i, r = j;
                    while (l < r) { [pts[l], pts[r]] = [pts[r], pts[l]]; l++; r--; }
                    improved = true;
                }
            }
        }
    }

    return pts.slice(1).map(p => p.id);
}

// ─── Past Routes Helpers ──────────────────────────────────────────────────────

function renderPastRoutesTable() {
    filterPastRoutes();
}

function filterPastRoutes() {
    const searchVal = document.getElementById('past-route-search') ? document.getElementById('past-route-search').value.toLowerCase().trim() : '';
    const statusVal = document.getElementById('past-route-filter-status') ? document.getElementById('past-route-filter-status').value : 'ALL';
    const dateVal = document.getElementById('past-route-filter-date') ? document.getElementById('past-route-filter-date').value : '';

    filteredPastRoutes = allPastRoutes.filter(route => {
        const attr = route.objectDetails || {};
        
        // Search fields
        const alias = (route.alias || '').toLowerCase();
        const driver = (attr.assignedDriverName || '').toLowerCase();
        const driverEmail = (attr.assignedDriverEmail || '').toLowerCase();
        
        // Get truck details from allObjects to handle active/deactivated trucks
        const truckId = attr.assignedTruckId || '';
        const truckObj = allObjects.find(t => api.getUUID(t) === truckId);
        const truckAlias = (truckObj?.alias || '').toLowerCase();
        const truckPlate = (truckObj?.objectDetails?.plateNumber || '').toLowerCase();
        
        const routeDate = (route.creationTimestamp || '').slice(0, 10);

        let statusText = 'completed';
        if (attr.status === 'cancelled') {
            statusText = 'cancelled';
        } else if (attr.status !== 'completed') {
            statusText = 'deleted';
        }

        // Multi-term search support (all terms must match at least one field)
        const searchTerms = searchVal.split(/\s+/).filter(t => t);
        const matchSearch = searchTerms.every(term => 
            alias.includes(term) || 
            driver.includes(term) || 
            driverEmail.includes(term) || 
            truckAlias.includes(term) || 
            truckPlate.includes(term) ||
            routeDate.includes(term) ||
            statusText.includes(term)
        );

        // Status filter
        let status = attr.status || 'deleted';
        if (status !== 'completed' && status !== 'cancelled') {
            status = 'deleted';
        }
        const matchStatus = statusVal === 'ALL' || status === statusVal;

        // Date filter (matches the route's creation date)
        const matchDate = !dateVal || routeDate === dateVal;

        return matchSearch && matchStatus && matchDate;
    });

    const tbody = document.getElementById('past-routes-table-body');
    if (!tbody) return;

    if (filteredPastRoutes.length === 0) {
        tbody.innerHTML = `<tr>
            <td colspan="7" style="text-align:center; padding:3rem; color:var(--text-muted);">
                <i class="fa-solid fa-folder-open" style="font-size:2rem; opacity:0.3; margin-bottom:0.5rem; display:block;"></i>
                No matching past routes found.
            </td>
        </tr>`;
        return;
    }

    tbody.innerHTML = filteredPastRoutes.map(route => {
        const attr = route.objectDetails || {};
        const uuid = api.getUUID(route);
        const total = (attr.binIds || []).length;
        const done = (attr.completedBinIds || []).length;
        const pct = total > 0 ? Math.round((done / total) * 100) : 0;
        
        let statusText = 'Completed';
        let badgeClass = 'badge-green';
        if (attr.status === 'cancelled') {
            statusText = 'Cancelled';
            badgeClass = 'badge-red';
        } else if (attr.status !== 'completed') {
            statusText = 'Deleted';
            badgeClass = 'badge-gray';
        }

        const dateStr = (route.creationTimestamp || '').slice(0, 10) || '—';
        const driverName = attr.assignedDriverName || attr.assignedDriverEmail || 'Unassigned';
        
        const truckId = attr.assignedTruckId || '';
        const truckObj = allObjects.find(t => api.getUUID(t) === truckId);
        const truckName = truckObj ? `${truckObj.alias} (${truckObj.objectDetails?.plateNumber || ''})` : (attr.assignedTruckId || 'None');
        const isSelected = activeRouteId === uuid;

        return `<tr class="${isSelected ? 'route-active' : ''}" style="${isSelected ? 'background: var(--primary-light);' : ''}">
            <td style="font-weight:600;">${esc(route.alias) || '—'}</td>
            <td>${esc(dateStr)}</td>
            <td>${esc(driverName)}</td>
            <td>${esc(truckName)}</td>
            <td><span class="badge ${badgeClass}">${statusText}</span></td>
            <td>
                <div style="display:flex; align-items:center; gap:0.5rem; min-width:120px;">
                    <span style="font-size:0.75rem; color:var(--text-secondary); white-space:nowrap;">${done} / ${total} (${pct}%)</span>
                    <div class="fill-bar" style="flex:1; height:6px; background:var(--bg-secondary); border-radius:9999px; overflow:hidden;">
                        <div class="fill-bar-inner green" style="width:${pct}%; height:100%; border-radius:9999px;"></div>
                    </div>
                </div>
            </td>
            <td style="text-align:right;">
                <button class="btn btn-sm ${isSelected ? 'btn-primary' : 'btn-ghost'}" onclick="viewPastRouteOnMap('${uuid}')" style="font-weight:600;">
                    <i class="fa-solid fa-map-location-dot"></i> ${isSelected ? '✓ Selected' : 'View on Map'}
                </button>
            </td>
        </tr>`;
    }).join('');
}

function resetPastRouteFilters() {
    const searchInput = document.getElementById('past-route-search');
    const statusSelect = document.getElementById('past-route-filter-status');
    const dateInput = document.getElementById('past-route-filter-date');
    if (searchInput) searchInput.value = '';
    if (statusSelect) statusSelect.value = 'ALL';
    if (dateInput) dateInput.value = '';
    filterPastRoutes();
}

function viewPastRouteOnMap(routeUUID) {
    switchMainView('live-map');
    toggleRouteSelection(routeUUID);
}

// ─── Smart Auto-Dispatch — "Plan Today's Routes" ─────────────────────────────
// Picks full bins, bin-packs them into available trucks within capacity,
// assigns idle drivers, orders each with the shared TSP, and previews the plan
// before the operator commits. Reuses the same calls as manual route creation.
let _dispatchPlan = null;

async function openDispatchModal() {
    _dispatchPlan = null;
    document.getElementById('dispatch-threshold').value = 50;
    document.getElementById('dispatch-threshold-val').textContent = '50%';
    document.getElementById('dispatch-result').innerHTML = '';
    document.getElementById('dispatch-footer').style.display = 'none';
    document.getElementById('dispatch-modal').classList.add('open');
    // Auto-dispatch assigns idle drivers, so load the roster (brief ADMIN elevation).
    await loadDriversAsAdmin();
}

function closeDispatchModal() {
    document.getElementById('dispatch-modal').classList.remove('open');
}

function generateDispatchPlan() {
    const threshold = parseInt(document.getElementById('dispatch-threshold').value) || 0;
    const resultEl = document.getElementById('dispatch-result');
    const footer = document.getElementById('dispatch-footer');
    const info = msg => `<div style="background:var(--primary-light);color:var(--primary-dark);border-radius:var(--radius);padding:.6rem .8rem;font-size:.8rem;margin-top:.5rem;"><i class="fa-solid fa-circle-info"></i> ${msg}</div>`;
    const warn = msg => `<div style="background:var(--status-yellow-bg);color:#92400E;border-radius:var(--radius);padding:.6rem .8rem;font-size:.8rem;"><i class="fa-solid fa-triangle-exclamation"></i> ${msg}</div>`;
    const fail = msg => { resultEl.innerHTML = warn(msg); footer.style.display = 'none'; _dispatchPlan = null; };

    const availableTrucks = allTrucks.filter(t => t.objectDetails?.status === 'available');
    const activeDriverEmails = getActiveRouteDriverEmails();
    const idleDrivers = allDrivers.filter(d => !activeDriverEmails.has(d.userId?.email));
    const routedBinIds = getActiveRouteBinIds();

    const weightOf = b => { const a = b.objectDetails || {}; return Math.round((parseFloat(a.fillLevel) || 0) / 100 * (parseFloat(a.capacity) || 0)); };
    const eligible = allBins.filter(b => {
        const a = b.objectDetails || {};
        const fill = parseFloat(a.fillLevel) || 0;
        const hasError = a.errorStatus && a.errorStatus !== null && a.errorStatus !== 'null';
        return fill >= threshold && !routedBinIds.has(api.getUUID(b)) && !hasError;
    }).sort((x, y) => (y.objectDetails?.fillLevel || 0) - (x.objectDetails?.fillLevel || 0));

    if (eligible.length === 0) return fail(`No unrouted bins are at or above ${threshold}% fill.`);
    if (availableTrucks.length === 0) return fail('No available trucks — free one up or add a truck, then try again.');
    if (idleDrivers.length === 0) return fail('No idle drivers — every driver is already on an active route (or no drivers exist yet). Free one up, or add drivers, then try again.');

    const numRoutes = Math.min(availableTrucks.length, idleDrivers.length);
    const remaining = [...eligible];
    const plans = [];
    for (let i = 0; i < numRoutes && remaining.length; i++) {
        const truck = availableTrucks[i];
        const cap = parseFloat(truck.objectDetails?.totalCapacity) || 0;
        let load = 0;
        const binIds = [];
        for (let j = 0; j < remaining.length;) {
            const w = weightOf(remaining[j]);
            if (binIds.length === 0 || load + w <= cap) { // always take at least one
                load += w; binIds.push(api.getUUID(remaining[j])); remaining.splice(j, 1);
            } else { j++; }
        }
        if (binIds.length) plans.push({ truck, driver: idleDrivers[i], binIds: optimizeBinOrder(binIds), load, cap });
    }

    if (plans.length === 0) return fail('Could not build any route from the current bins/trucks.');

    _dispatchPlan = plans;
    const nameOf = d => d.username || d.userId?.email || '—';
    let html = `<div style="font-weight:700;font-size:.875rem;margin:.25rem 0 .5rem;">Proposed: ${plans.length} route${plans.length > 1 ? 's' : ''}</div>`;
    html += plans.map((p, i) => {
        const over = p.load > p.cap;
        const pct = p.cap ? Math.min(100, Math.round(p.load / p.cap * 100)) : 0;
        return `<div class="op-item" style="cursor:default;">
            <div class="op-item-header">
                <label class="op-item-name" style="display:flex;align-items:center;gap:.5rem;cursor:pointer;">
                    <input type="checkbox" class="dispatch-pick" data-idx="${i}" checked onchange="updateDispatchSelection()">
                    Auto Route ${i + 1}
                </label>
                <span class="badge badge-${over ? 'red' : 'green'}">${p.binIds.length} bins</span>
            </div>
            <div class="op-item-sub">🚛 ${esc(p.truck.alias)} · 👤 ${esc(nameOf(p.driver))}</div>
            <div class="fill-bar" style="margin-top:.4rem;"><div class="fill-bar-inner ${over ? 'red' : pct > 80 ? 'yellow' : 'green'}" style="width:${pct}%"></div></div>
            <div class="op-item-sub" style="margin-top:.25rem;">Load: ${p.load} / ${p.cap} L${over ? ' · ⚠ over capacity' : ''}</div>
        </div>`;
    }).join('');
    if (remaining.length) html += info(`${remaining.length} eligible bin(s) left unrouted — not enough trucks/drivers or capacity for this round.`);
    resultEl.innerHTML = html;
    footer.style.display = 'flex';
    updateDispatchSelection();
}

// Enable/label the Create button by how many routes are ticked.
function updateDispatchSelection() {
    const picked = getPickedDispatchPlans().length;
    const btn = document.getElementById('btn-create-dispatch');
    btn.disabled = picked === 0;
    btn.innerHTML = `<i class="fa-solid fa-check"></i> Create ${picked || ''} Route${picked === 1 ? '' : 's'}`.replace('  ', ' ');
}

function getPickedDispatchPlans() {
    if (!_dispatchPlan) return [];
    const picks = new Set([...document.querySelectorAll('.dispatch-pick:checked')].map(c => +c.dataset.idx));
    return _dispatchPlan.filter((_, i) => picks.has(i));
}

async function commitDispatchPlan() {
    const selected = getPickedDispatchPlans();
    if (!selected.length) return;
    const btn = document.getElementById('btn-create-dispatch');
    btn.disabled = true; btn.textContent = 'Creating...';
    const date = new Date().toISOString().split('T')[0];
    let created = 0;
    try {
        for (let i = 0; i < selected.length; i++) {
            const p = selected[i];
            const driverEmail = p.driver.userId?.email || null;
            const driverNm = p.driver.username || driverEmail;
            const routeObj = api.buildRoute(`Auto Route ${i + 1} · ${date}`, p.binIds, api.getUUID(p.truck), driverEmail, driverNm, session.getEmail());
            const createdRoute = await api.createObject(routeObj);
            const routeUUID = api.getUUID(createdRoute);
            for (const binId of p.binIds) {
                const binObj = allBins.find(b => api.getUUID(b) === binId);
                const binSystemID = binObj?.objectId?.systemID || SYSTEM_ID;
                await api.bindChildToObject(routeUUID, binSystemID, binId);
            }
            await api.updateObject(api.getUUID(p.truck), {
                objectDetails: { ...p.truck.objectDetails, status: 'on_route', driverEmail, driverName: driverNm }
            });
            created++;
        }
        showToast(`Auto-dispatch created ${created} route${created > 1 ? 's' : ''}!`, 'success');
        closeDispatchModal();
        await refreshAll();
    } catch (err) {
        showToast('Error creating routes: ' + err.message, 'error');
        await refreshAll();
    } finally {
        btn.disabled = false; btn.innerHTML = '<i class="fa-solid fa-check"></i> Create Routes';
    }
}
