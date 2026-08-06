/* =============================================
   Core UI — shared helpers used across every page:
   HTML escaping, toasts, the mobile nav drawer, and global modal dismissal.
   ============================================= */

/**
 * Escape a value for safe insertion into HTML — both element text and
 * attribute values (quotes included). Use around any user-supplied string
 * (aliases, addresses, names, emails) rendered via innerHTML / template literals.
 */
function esc(value) {
    if (value === null || value === undefined) return '';
    return String(value)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#39;');
}

// ─── Toast ────────────────────────────────────────────────────────────────────

function showToast(msg, type = 'info') {
    let container = document.getElementById('toast-container');
    if (!container) {
        container = document.createElement('div');
        container.id = 'toast-container';
        document.body.appendChild(container);
    }
    const toast = document.createElement('div');
    toast.className = `toast ${type}`;
    const icons = { success: 'fa-check-circle', error: 'fa-times-circle', warning: 'fa-exclamation-triangle', info: 'fa-info-circle' };
    toast.innerHTML = `<i class="fa-solid ${icons[type] || icons.info}"></i> <span>${msg}</span>`;
    container.appendChild(toast);
    setTimeout(() => {
        toast.style.opacity = '0';
        toast.style.transform = 'translateX(100%)';
        toast.style.transition = 'all 0.3s ease';
        setTimeout(() => toast.remove(), 300);
    }, 3500);
}

// ─── Light / Dark theme ──────────────────────────────────────────────────────
// Applied synchronously (before paint) so there's no flash, then a floating
// toggle is injected on every page. Choice persists in localStorage.
(function applyStoredTheme() {
    // Login (no sidebar) has its own gradient look — always light, no toggle.
    if (!document.querySelector('.sidebar')) {
        document.documentElement.dataset.theme = 'light';
        return;
    }
    const saved = localStorage.getItem('theme');
    const theme = saved || (window.matchMedia && matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light');
    document.documentElement.dataset.theme = theme;
})();

function toggleTheme() {
    const next = document.documentElement.dataset.theme === 'dark' ? 'light' : 'dark';
    document.documentElement.dataset.theme = next;
    localStorage.setItem('theme', next);
    _syncThemeToggle();
    // Let pages with canvas charts (operator/admin) repaint against the new tokens.
    window.dispatchEvent(new Event('themechange'));
}

function _syncThemeToggle() {
    const dark = document.documentElement.dataset.theme === 'dark';
    document.querySelectorAll('.theme-toggle').forEach(btn => {
        btn.innerHTML = `<i class="fa-solid ${dark ? 'fa-sun' : 'fa-moon'}"></i>`;
        btn.setAttribute('aria-label', dark ? 'Switch to light mode' : 'Switch to dark mode');
    });
}

// Inline toggle in every topbar (admin has one per view). Login has no topbar,
// so it gets no toggle and stays light. Mirrors the mobile-nav injection pattern.
function _initThemeToggle() {
    if (document.querySelector('.theme-toggle')) return;
    document.querySelectorAll('.topbar').forEach(tb => {
        const b = document.createElement('button');
        b.type = 'button';
        b.className = 'theme-toggle';
        b.addEventListener('click', toggleTheme);
        (tb.querySelector('.topbar-actions') || tb).appendChild(b);
    });
    _syncThemeToggle();
}

if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', _initThemeToggle);
} else {
    _initThemeToggle();
}

// ─── Mobile navigation drawer (dashboards only) ──────────────────────────────
// On small screens the sidebar becomes a slide-in drawer. This injects a
// hamburger button into every topbar + a backdrop, so no per-page HTML edits
// are needed. Login page (no sidebar/topbar) is skipped automatically.
function _initMobileNav() {
    const sidebar = document.querySelector('.sidebar');
    const topbars = document.querySelectorAll('.topbar');
    if (!sidebar || topbars.length === 0) return;

    const closeNav = () => document.body.classList.remove('nav-open');

    let backdrop = document.querySelector('.nav-backdrop');
    if (!backdrop) {
        backdrop = document.createElement('div');
        backdrop.className = 'nav-backdrop';
        document.body.appendChild(backdrop);
        backdrop.addEventListener('click', closeNav);
    }

    topbars.forEach(tb => {
        if (tb.querySelector('.nav-toggle')) return;
        const btn = document.createElement('button');
        btn.type = 'button';
        btn.className = 'nav-toggle';
        btn.setAttribute('aria-label', 'Toggle navigation menu');
        btn.innerHTML = '<i class="fa-solid fa-bars"></i>';
        btn.addEventListener('click', () => document.body.classList.toggle('nav-open'));
        tb.insertBefore(btn, tb.firstChild);
    });

    // Close the drawer after picking a destination
    sidebar.addEventListener('click', e => {
        if (e.target.closest('.sidebar-item')) closeNav();
    });
    // Close on Escape
    document.addEventListener('keydown', e => { if (e.key === 'Escape') closeNav(); });
}

if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', _initMobileNav);
} else {
    _initMobileNav();
}

// ─── Dismiss any modal on backdrop click or Escape (shared across pages) ─────
// Clicking the dark overlay (not the modal card inside it) or pressing Escape
// closes the open modal — applies to every .modal-overlay on every page.
document.addEventListener('click', (e) => {
    if (e.target.classList && e.target.classList.contains('modal-overlay') && e.target.classList.contains('open')) {
        e.target.classList.remove('open');
    }
});
document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape') {
        document.querySelectorAll('.modal-overlay.open').forEach(m => m.classList.remove('open'));
    }
});
