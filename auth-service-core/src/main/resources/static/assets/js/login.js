/**
 * Login Page Module
 * 支持账号密码 / 邮箱验证码 / 手机验证码 多方式登录（按租户可用方式动态渲染）
 */

(function() {
    'use strict';

    let activeMethod = 'password';        // 当前选中的登录方式
    let enabledMethods = ['password'];    // 当前租户对用户开放的方式

    // 登录方式 → Tab/Pane 定义
    const TAB_DEFS = [
        { method: 'password', label: '账号密码', pane: 'pane-password' },
        { method: 'email:aliyun', label: '邮箱', pane: 'pane-email' },
        { method: 'sms:aliyun', label: '手机', pane: 'pane-sms' }
    ];

    function init() {
        // OAuth 回调：URL fragment 形如 #oauth=success&accessToken=...&refreshToken=...
        if (window.location.hash && window.location.hash.indexOf('oauth=success') !== -1) {
            const params = new URLSearchParams(window.location.hash.substring(1));
            const at = params.get('accessToken');
            const rt = params.get('refreshToken');
            if (at && rt) {
                localStorage.setItem('auth_access_token', at);
                localStorage.setItem('auth_refresh_token', rt);
                window.location.hash = '';
                window.location.href = '/';
                return;
            }
        }
        if (Auth.isAuthenticated()) {
            window.location.href = '/';
            return;
        }
        setupLoginForm();
        setupCodeButtons();
        loadTenants();
    }

    /**
     * 提交登录：按当前 active 方式路由到对应 API
     */
    function setupLoginForm() {
        const form = document.getElementById('loginForm');
        form.addEventListener('submit', async (e) => {
            e.preventDefault();
            const tenantUid = document.getElementById('loginTenantId').value;
            if (!tenantUid) {
                Toast.error('请选择租户');
                return;
            }

            const submitBtn = form.querySelector('button[type="submit"]');
            const originalText = submitBtn.textContent;
            submitBtn.disabled = true;
            submitBtn.textContent = '登录中...';

            try {
                let tokenResponse;
                if (activeMethod === 'password') {
                    const username = document.getElementById('loginUsername').value.trim();
                    const password = document.getElementById('loginPassword').value;
                    if (!username || !password) {
                        Toast.error('请输入用户名和密码');
                        return;
                    }
                    tokenResponse = await API.Auth.login(username, password, tenantUid);
                } else {
                    const category = activeMethod.split(':')[0];
                    const target = category === 'email'
                        ? document.getElementById('emailInput').value.trim()
                        : document.getElementById('phoneInput').value.trim();
                    const code = category === 'email'
                        ? document.getElementById('emailCode').value.trim()
                        : document.getElementById('phoneCode').value.trim();
                    if (!target || !code) {
                        Toast.error('请输入' + (category === 'email' ? '邮箱' : '手机号') + '和验证码');
                        return;
                    }
                    tokenResponse = await API.Auth.loginByCode(tenantUid, activeMethod, target, code);
                }

                localStorage.setItem('auth_access_token', tokenResponse.accessToken);
                localStorage.setItem('auth_refresh_token', tokenResponse.refreshToken);

                Toast.success('登录成功！');
                setTimeout(() => { window.location.href = '/'; }, 500);
            } catch (error) {
                console.error('Login error:', error);
                Toast.error('登录失败: ' + (error.message || '请重试'));
            } finally {
                submitBtn.disabled = false;
                submitBtn.textContent = originalText;
            }
        });
    }

    /**
     * 绑定邮箱/手机「获取验证码」按钮
     */
    function setupCodeButtons() {
        const emailBtn = document.getElementById('sendEmailCodeBtn');
        const phoneBtn = document.getElementById('sendPhoneCodeBtn');
        if (emailBtn) {
            emailBtn.addEventListener('click', () => {
                sendCode('email:aliyun', document.getElementById('emailInput').value.trim(), 'sendEmailCodeBtn');
            });
        }
        if (phoneBtn) {
            phoneBtn.addEventListener('click', () => {
                sendCode('sms:aliyun', document.getElementById('phoneInput').value.trim(), 'sendPhoneCodeBtn');
            });
        }
    }

    async function sendCode(method, target, btnId) {
        const tenantUid = document.getElementById('loginTenantId').value;
        if (!tenantUid) {
            Toast.error('请先选择租户');
            return;
        }
        if (!target) {
            Toast.error(method.startsWith('email') ? '请输入邮箱' : '请输入手机号');
            return;
        }
        const btn = document.getElementById(btnId);
        try {
            btn.disabled = true;
            await API.Auth.sendCode(tenantUid, method, target);
            Toast.success('验证码已发送');
            countdown(btn, 60);
        } catch (error) {
            Toast.error('发送失败: ' + (error.message || '请重试'));
            btn.disabled = false;
        }
    }

    function countdown(btn, seconds) {
        let left = seconds;
        const original = btn.dataset.originalText || btn.textContent;
        btn.dataset.originalText = original;
        btn.textContent = left + 's 后重发';
        const timer = setInterval(() => {
            left--;
            if (left <= 0) {
                clearInterval(timer);
                btn.disabled = false;
                btn.textContent = original;
            } else {
                btn.textContent = left + 's 后重发';
            }
        }, 1000);
    }

    /**
     * 加载可用租户，并在切换租户时刷新该租户可用的登录方式
     */
    async function loadTenants() {
        const loginTenantSelect = document.getElementById('loginTenantId');
        if (!loginTenantSelect) return;

        try {
            const tenants = await API.Tenants.getAvailable();
            loginTenantSelect.innerHTML = '';
            tenants.forEach(tenant => {
                const option = `<option value="${tenant.tenantUid}">${escapeHtml(tenant.tenantName)} (${escapeHtml(tenant.tenantCode)})</option>`;
                loginTenantSelect.insertAdjacentHTML('beforeend', option);
            });
        } catch (error) {
            console.error('Failed to load tenants:', error);
            loginTenantSelect.insertAdjacentHTML('beforeend', '<option value="">默认租户</option>');
        }

        loginTenantSelect.addEventListener('change', () => {
            refreshMethods(loginTenantSelect.value);
        });
        if (loginTenantSelect.value) {
            refreshMethods(loginTenantSelect.value);
        } else {
            renderTabs(['password']);
        }
    }

    /**
     * 按租户标识拉取可用登录方式，渲染 Tab 与社交登录入口
     */
    async function refreshMethods(tenantUid) {
        if (!tenantUid) {
            enabledMethods = ['password'];
            renderTabs(enabledMethods);
            renderSocial([]);
            return;
        }
        try {
            const methods = await API.LoginMethods.getEnabled(tenantUid);
            enabledMethods = (methods && methods.length) ? methods : ['password'];
        } catch (e) {
            enabledMethods = ['password'];
        }
        renderTabs(enabledMethods);
        renderSocial(enabledMethods.filter(m => m.startsWith('oauth:')));
    }

    /**
     * 渲染登录方式 Tab，并切换到可用方式（active 不可用时回退首个）
     */
    function renderTabs(methods) {
        const tabsEl = document.getElementById('loginTabs');
        const visible = TAB_DEFS.filter(t => methods.includes(t.method));
        tabsEl.innerHTML = visible.map(t =>
            `<button type="button" class="login-tab${t.method === activeMethod ? ' active' : ''}" data-method="${t.method}" data-pane="${t.pane}">${t.label}</button>`
        ).join('');
        tabsEl.querySelectorAll('.login-tab').forEach(tab => {
            tab.addEventListener('click', () => switchPane(tab.dataset.method, tab.dataset.pane));
        });
        tabsEl.style.display = visible.length > 1 ? '' : 'none';

        const target = visible.find(t => t.method === activeMethod) || visible[0];
        if (target) {
            switchPane(target.method, target.pane);
        }
    }

    function switchPane(method, paneId) {
        activeMethod = method;
        document.querySelectorAll('.login-pane').forEach(p => p.style.display = 'none');
        const pane = document.getElementById(paneId);
        if (pane) pane.style.display = '';
        document.querySelectorAll('.login-tab').forEach(t => {
            t.classList.toggle('active', t.dataset.method === method);
        });
    }

    function renderSocial(oauthMethods) {
        const container = document.getElementById('socialLoginMethods');
        const divider = document.getElementById('loginDivider');
        if (!container || !divider) return;
        if (oauthMethods && oauthMethods.length) {
            divider.style.display = '';
            container.innerHTML = oauthMethods.map(m => {
                const name = (m.split(':')[1] || '').toUpperCase();
                return `<button type="button" class="btn-social" data-method="${escapeHtml(m)}">${escapeHtml(name)} 登录</button>`;
            }).join('');
            container.querySelectorAll('.btn-social').forEach(btn => {
                btn.addEventListener('click', () => {
                    const provider = btn.dataset.method.split(':')[1];
                    const tenantUid = document.getElementById('loginTenantId').value;
                    if (!tenantUid) {
                        Toast.error('请先选择租户');
                        return;
                    }
                    window.location.href = '/api/auth/oauth/' + encodeURIComponent(provider)
                        + '/authorize?tenantUid=' + encodeURIComponent(tenantUid);
                });
            });
        } else {
            divider.style.display = 'none';
            container.innerHTML = '';
        }
    }

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

            const icons = { success: '✓', error: '✕', warning: '⚠', info: 'ℹ' };

            toast.innerHTML = `
                <span class="toast-icon">${icons[type] || icons.info}</span>
                <span class="toast-message">${escapeHtml(message)}</span>
                <button class="toast-close">&times;</button>
            `;

            container.appendChild(toast);
            toast.querySelector('.toast-close').addEventListener('click', () => {
                toast.classList.add('removing');
                setTimeout(() => toast.remove(), 300);
            });
            setTimeout(() => {
                if (toast.parentNode) {
                    toast.classList.add('removing');
                    setTimeout(() => toast.remove(), 300);
                }
            }, 3000);
        },
        success(message) { this.show(message, 'success'); },
        error(message) { this.show(message, 'error'); },
        warning(message) { this.show(message, 'warning'); },
        info(message) { this.show(message, 'info'); }
    };

    window.Toast = Toast;

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();
