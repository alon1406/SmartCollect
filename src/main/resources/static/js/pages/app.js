/* =============================================
   app.js — Login page logic
   ============================================= */

document.addEventListener('DOMContentLoaded', () => {
    // If already logged in, redirect to correct dashboard
    const existing = session.getUser();
    if (existing) redirectByRole(existing.role);

    document.getElementById('login-form').addEventListener('submit', handleLogin);
});

async function handleLogin(e) {
    e.preventDefault();
    hideError();

    const email = document.getElementById('login-email').value.trim();
    const password = document.getElementById('login-password').value;
    const btn = document.getElementById('btn-login');

    if (!email || !password) {
        showError('Please enter your email and password.');
        return;
    }

    btn.disabled = true;
    btn.innerHTML = '<div class="spinner" style="width:18px;height:18px;border-width:2px;margin:0 auto;"></div>';

    try {
        const user = await api.login(email, password);
        session.setUser(user);
        redirectByRole(user.role);
    } catch (err) {
        if (err.message && err.message.toLowerCase().includes('not found')) {
            showError('No account found with that email address.');
        } else if (err.message && err.message.toLowerCase().includes('password')) {
            showError('Incorrect password. Please try again.');
        } else {
            showError(err.message || 'Login failed. Is the server running?');
        }
    } finally {
        btn.disabled = false;
        btn.innerHTML = '<i class="fa-solid fa-arrow-right-to-bracket"></i> Login';
    }
}

function redirectByRole(role) {
    switch (role) {
        case 'ADMIN': window.location.href = 'admin.html'; break;
        case 'OPERATOR': window.location.href = 'operator.html'; break;
        case 'END_USER': window.location.href = 'driver.html'; break;
        default: window.location.href = 'admin.html';
    }
}

function showError(msg) {
    const el = document.getElementById('login-error');
    document.getElementById('login-error-msg').textContent = msg;
    el.style.display = 'flex';
}

function hideError() {
    document.getElementById('login-error').style.display = 'none';
}
