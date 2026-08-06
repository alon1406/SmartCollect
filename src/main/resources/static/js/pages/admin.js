/* =============================================
   admin.js — Admin Dashboard Logic
   Role: ADMIN only. Works within admin permissions:
   read users, read the command log, bulk-delete. No object reads.
   ============================================= */

let currentUser  = null;
let allUsers     = [];
let allCommands  = [];
let activeFilter = 'ALL';
let userSearch   = '';
let currentView  = 'overview';

document.addEventListener('DOMContentLoaded', async () => {
    currentUser = session.require('ADMIN');
    if (!currentUser) return;

    document.getElementById('user-name').textContent = currentUser.username || 'Admin';
    document.getElementById('btn-logout').addEventListener('click', () => session.logout());

    // Filter tabs (users)
    document.querySelectorAll('.filter-tab').forEach(tab => {
        tab.addEventListener('click', e => {
            document.querySelectorAll('.filter-tab').forEach(t => t.classList.remove('active'));
            e.target.classList.add('active');
            activeFilter = e.target.dataset.filter;
            renderUsersTable();
        });
    });
    document.getElementById('user-search').addEventListener('input', e => {
        userSearch = e.target.value.trim().toLowerCase();
        renderUsersTable();
    });
    document.getElementById('command-search').addEventListener('input', e => {
        renderCommandsTable(e.target.value.trim().toLowerCase());
    });
    document.getElementById('user-form').addEventListener('submit', handleCreateUser);

    await refreshAdmin();
});

// Pull both admin-readable datasets, then paint everything.
async function refreshAdmin() {
    await Promise.all([loadUsers(), loadCommands(true)]);
    renderOverview();
    renderSystemInfo();
}

// ─── View switching ───────────────────────────────────────────────────────────

function switchView(name) {
    currentView = name;
    ['overview', 'users', 'commands', 'system'].forEach(v => {
        document.getElementById('nav-' + v).classList.toggle('active', v === name);
        document.getElementById('view-' + v).style.display = v === name ? 'block' : 'none';
    });
    if (name === 'overview') renderOverview();   // re-render so charts size correctly
    if (name === 'system') renderSystemInfo();
}

// ─── Overview ─────────────────────────────────────────────────────────────────

const CMD_LABELS = {
    BinCollected: 'Collections',
    BinErrorReported: 'Issues',
    TruckArrivedAtDepot: 'Depot arrivals'
};
const CMD_COLORS = {
    BinCollected: '#16A34A',
    BinErrorReported: '#EF4444',
    TruckArrivedAtDepot: '#2563EB',
    Other: '#94A3B8'
};

let _charts = {};
function _mkChart(id, config) {
    if (typeof Chart === 'undefined') return;
    if (_charts[id]) _charts[id].destroy();
    const el = document.getElementById(id);
    if (el) _charts[id] = new Chart(el.getContext('2d'), config);
}

// Repaint charts against the new theme tokens when the user flips light/dark.
window.addEventListener('themechange', () => { if (Object.keys(_charts).length) renderOverview(); });

function renderOverview() {
    const byRole = r => allUsers.filter(u => u.role === r).length;
    const collected = allCommands.filter(c => c.command === 'BinCollected').length;

    document.getElementById('kpi-users').textContent     = allUsers.length;
    document.getElementById('kpi-admins').textContent    = byRole('ADMIN');
    document.getElementById('kpi-operators').textContent = byRole('OPERATOR');
    document.getElementById('kpi-drivers').textContent   = byRole('END_USER');
    document.getElementById('kpi-commands').textContent  = allCommands.length;
    document.getElementById('kpi-collected').textContent = collected;

    if (typeof Chart === 'undefined') return;
    const css = getComputedStyle(document.documentElement);
    Chart.defaults.font.family = "'Rubik', sans-serif";
    Chart.defaults.color = css.getPropertyValue('--text-secondary').trim() || '#64748B';
    const grid = 'rgba(100,116,139,0.15)';
    Chart.defaults.borderColor = grid;

    // Users by role
    const roles = ['ADMIN', 'OPERATOR', 'END_USER'];
    _mkChart('chart-roles', {
        type: 'doughnut',
        data: {
            labels: ['Admins', 'Operators', 'Drivers'],
            datasets: [{ data: roles.map(byRole), backgroundColor: ['#EF4444', '#F59E0B', '#16A34A'], borderWidth: 0 }]
        },
        options: { responsive: true, maintainAspectRatio: false, cutout: '62%', plugins: { legend: { position: 'bottom' } } }
    });

    // Commands by type
    const typeCounts = {};
    allCommands.forEach(c => { const k = CMD_LABELS[c.command] ? c.command : 'Other'; typeCounts[k] = (typeCounts[k] || 0) + 1; });
    const typeKeys = Object.keys(typeCounts);
    _mkChart('chart-cmd-types', {
        type: 'doughnut',
        data: {
            labels: typeKeys.length ? typeKeys.map(k => CMD_LABELS[k] || 'Other') : ['No commands'],
            datasets: [{ data: typeKeys.length ? typeKeys.map(k => typeCounts[k]) : [1], backgroundColor: typeKeys.length ? typeKeys.map(k => CMD_COLORS[k] || CMD_COLORS.Other) : ['#E2E8F0'], borderWidth: 0 }]
        },
        options: { responsive: true, maintainAspectRatio: false, cutout: '62%', plugins: { legend: { position: 'bottom' } } }
    });

    // Activity — last 14 days
    const dayKey = d => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
    const buckets = {};
    allCommands.forEach(c => {
        const ts = c.invocationTimestamp ? new Date(c.invocationTimestamp) : null;
        if (!ts || isNaN(ts)) return;
        const k = dayKey(ts);
        buckets[k] = (buckets[k] || 0) + 1;
    });
    const days = [];
    const today = new Date();
    for (let i = 13; i >= 0; i--) { const d = new Date(today); d.setDate(today.getDate() - i); days.push(dayKey(d)); }
    _mkChart('chart-activity', {
        type: 'bar',
        data: {
            labels: days.map(d => d.slice(5)),
            datasets: [{ data: days.map(d => buckets[d] || 0), backgroundColor: css.getPropertyValue('--primary').trim() || '#0D7A6A', borderRadius: 6, maxBarThickness: 26 }]
        },
        options: { responsive: true, maintainAspectRatio: false, plugins: { legend: { display: false } }, scales: { y: { beginAtZero: true, ticks: { precision: 0 }, grid: { color: grid } }, x: { grid: { display: false } } } }
    });

    renderRecentActivity();
}

function renderRecentActivity() {
    const el = document.getElementById('recent-activity');
    const recent = [...allCommands]
        .sort((a, b) => new Date(b.invocationTimestamp || 0) - new Date(a.invocationTimestamp || 0))
        .slice(0, 8);
    if (!recent.length) {
        el.innerHTML = `<p style="color:var(--text-muted);padding:0.5rem 0;font-size:.85rem;">No activity logged yet.</p>`;
        return;
    }
    el.innerHTML = recent.map(c => {
        const color = CMD_COLORS[c.command] || CMD_COLORS.Other;
        const icon = c.command === 'BinCollected' ? 'fa-recycle'
            : c.command === 'BinErrorReported' ? 'fa-triangle-exclamation'
            : c.command === 'TruckArrivedAtDepot' ? 'fa-warehouse' : 'fa-terminal';
        const who = c.invokedBy?.userId?.email || 'unknown';
        const time = c.invocationTimestamp ? new Date(c.invocationTimestamp).toLocaleString() : '—';
        return `<div class="activity-row">
            <div class="activity-icon" style="background:${color}22;color:${color};"><i class="fa-solid ${icon}"></i></div>
            <div class="activity-main">
                <div style="font-weight:600;">${esc(c.command) || '—'}</div>
                <div style="color:var(--text-muted);font-size:.72rem;">by ${esc(who)}</div>
            </div>
            <div class="activity-time">${esc(time)}</div>
        </div>`;
    }).join('');
}

function renderSystemInfo() {
    const set = (id, v) => { const el = document.getElementById(id); if (el) el.textContent = v; };
    set('sys-admin', currentUser?.userId?.email || currentUser?.username || '—');
    set('sys-id', (typeof SYSTEM_ID !== 'undefined' ? SYSTEM_ID : '—'));
    set('sys-api', (typeof API_VER !== 'undefined' ? API_VER : '—'));
    set('sys-counts', `${allUsers.length} users · ${allCommands.length} commands`);
}

// ─── Users ────────────────────────────────────────────────────────────────────

async function loadUsers() {
    const tbody = document.getElementById('users-table-body');
    tbody.innerHTML = `<tr><td colspan="5" style="text-align:center;padding:2rem;"><div class="spinner"></div></td></tr>`;
    try {
        allUsers = (await api.getAllUsers()) || [];
        renderUsersTable();
    } catch (err) {
        tbody.innerHTML = `<tr><td colspan="5" class="text-danger" style="text-align:center;padding:2rem;">
            <i class="fa-solid fa-triangle-exclamation"></i> Failed to load users: ${esc(err.message)}</td></tr>`;
    }
}

function renderUsersTable() {
    const tbody = document.getElementById('users-table-body');
    const filtered = allUsers
        .filter(u => activeFilter === 'ALL' || u.role === activeFilter)
        .filter(u => {
            if (!userSearch) return true;
            return (u.username || '').toLowerCase().includes(userSearch)
                || (u.userId?.email || '').toLowerCase().includes(userSearch);
        });

    if (filtered.length === 0) {
        tbody.innerHTML = `<tr><td colspan="5" style="text-align:center;padding:2rem;color:var(--text-muted);">
            <i class="fa-solid fa-users-slash"></i> No users found</td></tr>`;
        return;
    }

    tbody.innerHTML = filtered.map(u => {
        const { label, badgeClass } = roleDisplay(u.role);
        const avatar = u.avatar || (u.username ? u.username.charAt(0).toUpperCase() : '?');
        const email  = u.userId?.email || '';
        return `
        <tr>
            <td><div class="sidebar-user-avatar" style="width:36px;height:36px;font-size:0.875rem;">${esc(avatar)}</div></td>
            <td class="font-medium">${esc(email) || '—'}</td>
            <td>${esc(u.username) || '—'}</td>
            <td><span class="badge badge-${badgeClass}">${label}</span></td>
            <td>
                <button class="btn btn-sm btn-ghost" title="Edit" onclick="openEditModal('${esc(email)}')">
                    <i class="fa-solid fa-pen"></i>
                </button>
            </td>
        </tr>`;
    }).join('');
}

function roleDisplay(role) {
    switch (role) {
        case 'ADMIN':    return { label: 'Admin',    badgeClass: 'red' };
        case 'OPERATOR': return { label: 'Operator', badgeClass: 'yellow' };
        case 'END_USER': return { label: 'Driver',   badgeClass: 'green' };
        default:         return { label: role,        badgeClass: 'gray' };
    }
}

// ─── User modal ───────────────────────────────────────────────────────────────

function openAddUserModal() {
    document.getElementById('modal-title').textContent = 'Add New User';
    document.getElementById('user-form').reset();
    document.getElementById('u-email').disabled = false;
    document.getElementById('u-password-group').style.display = 'block';
    document.getElementById('edit-email').value = '';
    document.getElementById('user-modal').classList.add('open');
}

function openEditModal(email) {
    const user = allUsers.find(u => (u.userId?.email || '') === email);
    if (!user) { showToast('User not found — please refresh.', 'error'); return; }
    document.getElementById('modal-title').textContent = 'Edit User';
    document.getElementById('u-email').value    = email;
    document.getElementById('u-email').disabled = true;
    document.getElementById('u-name').value     = user.username || '';
    document.getElementById('u-role').value     = user.role || 'END_USER';
    document.getElementById('u-avatar').value   = user.avatar || '';
    document.getElementById('u-password').value = '';
    document.getElementById('u-password-group').style.display = 'none';
    document.getElementById('edit-email').value = email;
    document.getElementById('user-modal').classList.add('open');
}

function closeAddUserModal() {
    document.getElementById('user-modal').classList.remove('open');
}

async function handleCreateUser(e) {
    e.preventDefault();
    const btn       = document.getElementById('btn-save-user');
    const editEmail = document.getElementById('edit-email').value;
    const isEdit    = !!editEmail;

    btn.disabled = true;
    btn.innerHTML = '<div class="spinner" style="width:14px;height:14px;border-width:2px;"></div>';

    const email    = document.getElementById('u-email').value.trim();
    const username = document.getElementById('u-name').value.trim();
    const role     = document.getElementById('u-role').value;
    const password = document.getElementById('u-password').value;
    const avatar   = document.getElementById('u-avatar').value.trim() || username.charAt(0).toUpperCase();

    try {
        if (isEdit) {
            const currentPwd = prompt(`Enter the CURRENT password for ${editEmail} to authorize the update:`);
            if (!currentPwd) { btn.disabled = false; btn.innerHTML = 'Save User'; return; }
            await api.updateUser(editEmail, currentPwd, { role, username, avatar });
            showToast(`User ${editEmail} updated.`, 'success');
        } else {
            await api.createUser({ email, password, role, username, avatar });
            showToast(`User ${email} created!`, 'success');
        }
        closeAddUserModal();
        await loadUsers();
        renderOverview();
    } catch (err) {
        showToast('Error: ' + err.message, 'error');
    } finally {
        btn.disabled = false;
        btn.innerHTML = 'Save User';
    }
}

// ─── Audit Log (Commands) ─────────────────────────────────────────────────────

async function loadCommands(silent = false) {
    const tbody = document.getElementById('commands-table-body');
    if (!silent) {
        tbody.innerHTML = `<tr><td colspan="6" style="text-align:center;padding:2rem;"><div class="spinner"></div></td></tr>`;
        document.getElementById('command-count-label').textContent = 'Loading commands…';
    }
    try {
        allCommands = (await api.getAllCommands()) || [];
        allCommands.sort((a, b) => new Date(b.invocationTimestamp || 0) - new Date(a.invocationTimestamp || 0));
        document.getElementById('command-search').value = '';
        renderCommandsTable();
    } catch (err) {
        if (!silent) {
            tbody.innerHTML = `<tr><td colspan="6" class="text-danger" style="text-align:center;padding:2rem;">
                <i class="fa-solid fa-triangle-exclamation"></i> Failed to load commands: ${esc(err.message)}</td></tr>`;
            document.getElementById('command-count-label').textContent = 'Error loading commands';
        }
    }
}

function renderCommandsTable(query = '') {
    const tbody = document.getElementById('commands-table-body');
    const filtered = allCommands.filter(c => {
        if (!query) return true;
        const cmdName  = (c.command || '').toLowerCase();
        const cmdId    = (c.id?.commandId || '').toLowerCase();
        const targetId = (c.targetObject?.id?.objectId || '').toLowerCase();
        const email    = (c.invokedBy?.userId?.email || '').toLowerCase();
        return cmdName.includes(query) || cmdId.includes(query) || targetId.includes(query) || email.includes(query);
    });

    document.getElementById('command-count-label').textContent = `Showing ${filtered.length} of ${allCommands.length} commands`;

    if (filtered.length === 0) {
        tbody.innerHTML = `<tr><td colspan="6" style="text-align:center;padding:2rem;color:var(--text-muted);">
            <i class="fa-solid fa-list-check"></i> No commands found</td></tr>`;
        return;
    }

    tbody.innerHTML = filtered.map(c => {
        const cmdId  = c.id?.commandId || '—';
        const name   = c.command || '—';
        const target = c.targetObject?.id?.objectId || '—';
        const email  = c.invokedBy?.userId?.email || '—';
        const time   = c.invocationTimestamp ? new Date(c.invocationTimestamp).toLocaleString() : '—';
        return `
        <tr>
            <td class="font-medium">#${esc(cmdId)}</td>
            <td><span class="badge badge-yellow" style="text-transform:none;font-family:monospace;">${esc(name)}</span></td>
            <td style="font-size:0.8125rem;color:var(--text-secondary);font-family:monospace;">${esc(target)}</td>
            <td>${esc(email)}</td>
            <td style="font-size:0.8125rem;color:var(--text-muted);">${esc(time)}</td>
            <td>${formatAttributes(c.commandAttributes)}</td>
        </tr>`;
    }).join('');
}

function formatAttributes(attrs) {
    if (!attrs || Object.keys(attrs).length === 0) return `<span class="text-muted">—</span>`;
    return Object.entries(attrs).map(([k, v]) => {
        const valStr = typeof v === 'object' ? JSON.stringify(v) : String(v);
        const kv = esc(`${k}: ${valStr}`);
        return `<span class="badge badge-gray" style="font-family:monospace; margin:2px; text-transform:none; display:inline-block;" title="${kv}">${kv}</span>`;
    }).join(' ');
}

// ─── Demo data seeding ────────────────────────────────────────────────────────
// Admin can't create objects, so we self-elevate to OPERATOR for the object
// creates, then restore ADMIN (guaranteed). Drivers are plain users (open API).

async function _setRole(role) {
    await api.updateUser(session.getEmail(), session.getPassword(), { role });
}

async function _revertToAdmin() {
    // ponytail: retry-forever + blocking prompt — being stuck as OPERATOR breaks the admin console.
    for (let i = 0; ; i++) {
        try { await _setRole('ADMIN'); return; }
        catch (_) {
            if (i < 3) { await new Promise(r => setTimeout(r, 500)); continue; }
            alert('Could not restore your ADMIN role — do NOT reload. Check your connection, click OK to retry.');
        }
    }
}

// Forward-geocode an address to its real coordinate (Nominatim, same service the
// app already uses for reverse geocoding). Returns null on miss/failure.
async function geocode(q) {
    try {
        const url = `https://nominatim.openstreetmap.org/search?format=json&limit=1&q=${encodeURIComponent(q)}`;
        const res = await fetch(url, { headers: { Accept: 'application/json' } });
        const data = await res.json();
        if (Array.isArray(data) && data[0]) return { lat: parseFloat(data[0].lat), lng: parseFloat(data[0].lon) };
    } catch (_) {}
    return null;
}

async function seedDemoData() {
    if (!confirm('Seed 5 drivers, 5 trucks and 70 Tel Aviv bins?\nBins are geocoded to their real address (~70s). Briefly switches you to OPERATOR.')) return;
    const btn = document.getElementById('btn-seed');
    const orig = btn.innerHTML;
    btn.disabled = true;
    btn.innerHTML = '<div class="spinner" style="width:14px;height:14px;border-width:2px;"></div> Seeding…';

    const email = session.getEmail();
    const TYPES = ['general', 'plastic', 'paper', 'glass', 'organic'];
    const DRIVERS = [['Dan Cohen', 'dan@sc.com'], ['Noa Levi', 'noa@sc.com'], ['Omer Bar', 'omer@sc.com'],
                     ['Maya Katz', 'maya@sc.com'], ['Yossi Mizrahi', 'yossi@sc.com']];

    // Real Tel Aviv streets with an approximate midpoint coord. We generate 70
    // addresses from them; each is geocoded to its exact spot at seed time, with
    // the street coord (+ tiny jitter) as the fallback so misses still land on-street.
    const STREETS = [
        ['Rothschild Blvd', 32.0660, 34.7720], ['Dizengoff St', 32.0800, 34.7742],
        ['Ibn Gabirol St', 32.0830, 34.7830], ['Allenby St', 32.0680, 34.7715],
        ['Ben Yehuda St', 32.0820, 34.7685], ['King George St', 32.0730, 34.7755],
        ['HaYarkon St', 32.0840, 34.7670], ['Bograshov St', 32.0775, 34.7715],
        ['Shenkin St', 32.0685, 34.7735], ['Nachalat Binyamin St', 32.0670, 34.7705],
        ['Florentin St', 32.0575, 34.7690], ['Levinsky St', 32.0575, 34.7760],
        ['Yehuda HaLevi St', 32.0680, 34.7770], ['Montefiore St', 32.0665, 34.7800],
        ['Ben Gurion Blvd', 32.0865, 34.7720], ['Arlozorov St', 32.0870, 34.7800],
        ['Frishman St', 32.0805, 34.7695], ['Gordon St', 32.0820, 34.7690],
        ['Basel St', 32.0895, 34.7830], ['Weizmann St', 32.0850, 34.7900]
    ];
    const LOCATIONS = [];
    for (let i = 0; i < 70; i++) {
        const [st, blat, blng] = STREETS[i % STREETS.length];
        const num = 1 + ((i * 7) % 180);
        LOCATIONS.push([`${st} ${num}, Tel Aviv`,
            blat + (Math.random() * 2 - 1) * 0.0015,
            blng + (Math.random() * 2 - 1) * 0.0012]);
    }

    try {
        // 1. Drivers (createUser needs no role) — skip any that already exist.
        for (const [name, em] of DRIVERS) {
            try { await api.createUser({ email: em, password: 'Driver1!', role: 'END_USER', username: name, avatar: name[0] }); }
            catch (_) { /* already exists */ }
        }

        // 2. Elevate, create objects, always revert.
        await _setRole('OPERATOR');
        try {
            for (let i = 0; i < 5; i++) {
                const plate = `${11 + i}-${100 + i * 7}-${20 + i}`;
                await api.createObject(api.buildTruck(`Truck ${String.fromCharCode(65 + i)}`, plate, 8000, email));
            }
            for (let i = 0; i < LOCATIONS.length; i++) {
                const [addr, flat, flng] = LOCATIONS[i];
                btn.innerHTML = `<div class="spinner" style="width:14px;height:14px;border-width:2px;"></div> Placing bin ${i + 1}/${LOCATIONS.length}…`;
                const g = await geocode(addr);            // real coordinate for this address
                const lat = g ? g.lat : flat;             // fall back to the listed coord on a miss
                const lng = g ? g.lng : flng;
                const t = TYPES[i % TYPES.length];
                const bin = api.buildBin(`${t[0].toUpperCase() + t.slice(1)} Bin ${i + 1}`, lat, lng, t, 1100, addr, email);
                bin.objectDetails.fillLevel = Math.floor(Math.random() * 96);   // lively demo
                await api.createObject(bin);
                await new Promise(r => setTimeout(r, 1000));   // Nominatim: max ~1 req/sec
            }
        } finally {
            await _revertToAdmin();
        }

        showToast('Seeded 5 drivers, 5 trucks and 70 bins.', 'success');
        await refreshAdmin();
    } catch (err) {
        showToast('Seeding failed: ' + err.message, 'error');
    } finally {
        btn.disabled = false;
        btn.innerHTML = orig;
    }
}

// ─── Danger Zone ──────────────────────────────────────────────────────────────

async function deleteAllUsers() {
    if (!confirm('⚠️ Delete ALL users from the database? This cannot be undone.')) return;
    try {
        await api.deleteAllUsers();
        showToast('All users deleted.', 'warning');
        allUsers = [];
        renderUsersTable();
        renderOverview();
        renderSystemInfo();
    } catch (err) { showToast('Error: ' + err.message, 'error'); }
}

async function deleteAllObjects() {
    if (!confirm('⚠️ Delete ALL objects (bins, trucks, routes)? This cannot be undone.')) return;
    try {
        await api.deleteAllObjects();
        showToast('All objects deleted.', 'warning');
    } catch (err) { showToast('Error: ' + err.message, 'error'); }
}

async function deleteAllCommands() {
    if (!confirm('⚠️ Delete ALL command history?')) return;
    try {
        await api.deleteAllCommands();
        showToast('All commands deleted.', 'warning');
        allCommands = [];
        renderCommandsTable();
        renderOverview();
        renderSystemInfo();
    } catch (err) { showToast('Error: ' + err.message, 'error'); }
}
