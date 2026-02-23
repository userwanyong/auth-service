/**
 * Login Page Module
 * Handles login functionality
 */

(function() {
    'use strict';

    /**
     * Initialize login page
     */
    function init() {
        // Check if already authenticated
        if (Auth.isAuthenticated()) {
            window.location.href = '/';
            return;
        }

        // Setup login form
        setupLoginForm();

        // Load available tenants (public endpoint, no auth required)
        loadTenants();
    }

    /**
     * Setup login form
     */
    function setupLoginForm() {
        const form = document.getElementById('loginForm');

        form.addEventListener('submit', async (e) => {
            e.preventDefault();

            const username = document.getElementById('loginUsername').value.trim();
            const password = document.getElementById('loginPassword').value;
            const tenantIdValue = document.getElementById('loginTenantId').value;
            const tenantId = parseInt(tenantIdValue);

            console.log('Login form values:', { username, passwordLength: password?.length, tenantIdValue, tenantId });

            // Check if tenantIdValue is empty (not falsy, since 0 is a valid ID)
            if (!username || !password || tenantIdValue === '') {
                Toast.error('请填写所有必填项');
                return;
            }

            const submitBtn = form.querySelector('button[type="submit"]');
            const originalText = submitBtn.textContent;
            submitBtn.disabled = true;
            submitBtn.textContent = '登录中...';

            try {
                const tokenResponse = await API.Auth.login(username, password, tenantId);

                // Save token first (needed for subsequent API calls)
                // User info will be fetched on the dashboard
                localStorage.setItem('auth_access_token', tokenResponse.accessToken);
                localStorage.setItem('auth_refresh_token', tokenResponse.refreshToken);

                Toast.success('登录成功！');

                // Redirect to dashboard (will fetch user info there)
                setTimeout(() => {
                    window.location.href = '/';
                }, 500);
            } catch (error) {
                console.error('Login error:', error);
                Toast.error('登录失败: ' + (error.message || '用户名或密码错误'));
            } finally {
                submitBtn.disabled = false;
                submitBtn.textContent = originalText;
            }
        });
    }

    /**
     * Load available tenants from API
     * This is a public endpoint that doesn't require authentication
     */
    async function loadTenants() {
        const loginTenantSelect = document.getElementById('loginTenantId');

        if (!loginTenantSelect) return;

        try {
            const tenants = await API.Tenants.getAvailable();
            console.log('Loaded tenants:', tenants);

            // Clear existing options (keep the first "请选择租户" option)
            loginTenantSelect.innerHTML = '<option value="">请选择租户</option>';

            // Populate with fetched tenants
            tenants.forEach(tenant => {
                const option = `<option value="${tenant.id}">${escapeHtml(tenant.tenantName)} (${escapeHtml(tenant.tenantCode)})</option>`;
                loginTenantSelect.insertAdjacentHTML('beforeend', option);
            });
        } catch (error) {
            console.error('Failed to load tenants:', error);
            // Fallback to default tenant if API fails
            const fallbackOption = '<option value="1">默认租户</option>';
            loginTenantSelect.insertAdjacentHTML('beforeend', fallbackOption);
        }
    }

    /**
     * Escape HTML to prevent XSS
     */
    function escapeHtml(text) {
        if (!text) return '';
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }

    /**
     * Toast Module
     */
    const Toast = {
        show(message, type = 'info') {
            const container = document.getElementById('toastContainer');
            const toast = document.createElement('div');
            toast.className = `toast toast-${type}`;

            const icons = {
                success: '✓',
                error: '✕',
                warning: '⚠',
                info: 'ℹ'
            };

            toast.innerHTML = `
                <span class="toast-icon">${icons[type] || icons.info}</span>
                <span class="toast-message">${escapeHtml(message)}</span>
                <button class="toast-close">&times;</button>
            `;

            container.appendChild(toast);

            // Close button
            toast.querySelector('.toast-close').addEventListener('click', () => {
                toast.classList.add('removing');
                setTimeout(() => toast.remove(), 300);
            });

            // Auto remove
            setTimeout(() => {
                if (toast.parentNode) {
                    toast.classList.add('removing');
                    setTimeout(() => toast.remove(), 300);
                }
            }, 3000);
        },
        success(message) {
            this.show(message, 'success');
        },
        error(message) {
            this.show(message, 'error');
        },
        warning(message) {
            this.show(message, 'warning');
        },
        info(message) {
            this.show(message, 'info');
        }
    };

    // Expose Toast globally
    window.Toast = Toast;

    // Initialize on DOM ready
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();
