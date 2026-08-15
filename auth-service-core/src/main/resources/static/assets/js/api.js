/**
 * API Module
 * Handles all HTTP requests to the backend API
 */

const API = (function() {
    const BASE_URL = '/api';

    /**
     * Build fetch options with auth header
     */
    function buildOptions(options = {}) {
        const token = Auth.getAccessToken();
        // FormData（文件上传）时不设 Content-Type，让浏览器自动加 multipart boundary；
        // 否则会被强制成 application/json，导致后端报 not a multipart request
        const isFormData = typeof FormData !== 'undefined' && options.body instanceof FormData;
        const headers = {
            ...(isFormData ? {} : { 'Content-Type': 'application/json' }),
            ...options.headers
        };

        if (token) {
            headers['Authorization'] = `Bearer ${token}`;
        }

        return {
            ...options,
            headers
        };
    }

    /**
     * Handle API response
     */
    async function handleResponse(response) {
        if (response.status === 401) {
            // Try to refresh token
            try {
                await Auth.refreshToken();
                // Retry the request with new token
                return fetch(response.url, buildOptions({
                    method: response.method,
                    body: response.body
                })).then(handleResponse);
            } catch (error) {
                Auth.clearAuthData();
                window.location.href = '/login.html';
                throw new Error('会话已过期，请重新登录');
            }
        }

        const data = await response.json();

        if (!response.ok || data.code < 200 || data.code >= 300) {
            throw new Error(data.message || '请求失败');
        }

        return data.data;
    }

    /**
     * Generic request method
     */
    async function request(endpoint, options = {}) {
        try {
            const response = await fetch(`${BASE_URL}${endpoint}`, buildOptions(options));
            return await handleResponse(response);
        } catch (error) {
            console.error('API Error:', error);
            throw error;
        }
    }

    /**
     * GET request
     */
    async function get(endpoint, params = {}) {
        // Filter out null and undefined values
        const cleanParams = Object.fromEntries(
            Object.entries(params).filter(([_, v]) => v != null)
        );
        const queryString = new URLSearchParams(cleanParams).toString();
        const url = queryString ? `${endpoint}?${queryString}` : endpoint;
        return request(url, { method: 'GET' });
    }

    /**
     * POST request with JSON body
     */
    async function postJson(endpoint, data) {
        return request(endpoint, {
            method: 'POST',
            body: JSON.stringify(data)
        });
    }

    /**
     * POST request with form data
     */
    async function postForm(endpoint, formData) {
        return request(endpoint, {
            method: 'POST',
            headers: {
                // Don't set Content-Type for FormData, let browser set it with boundary
            },
            body: formData
        });
    }

    /**
     * PUT request
     */
    async function put(endpoint, data) {
        return request(endpoint, {
            method: 'PUT',
            body: JSON.stringify(data)
        });
    }

    /**
     * DELETE request
     */
    async function del(endpoint) {
        return request(endpoint, { method: 'DELETE' });
    }

    // ========== Auth API ==========
    const AuthAPI = {
        /**
         * User login
         * POST /api/auth/login
         */
        login: (username, password, tenantUid) =>
            postJson('/auth/login', { username, password, tenantUid }),

        /**
         * User register
         * POST /api/auth/register
         */
        register: (userData) =>
            postJson('/auth/register', userData),

        /**
         * User logout
         * POST /api/auth/logout
         */
        logout: () => {
            const token = Auth.getAccessToken();
            const refreshToken = Auth.getRefreshToken();
            return fetch(`${BASE_URL}/auth/logout`, {
                method: 'POST',
                headers: {
                    ...(token ? { 'Authorization': `Bearer ${token}` } : {}),
                    ...(refreshToken ? { 'X-Refresh-Token': refreshToken } : {})
                }
            }).then(handleResponse);
        },

        /**
         * Get current user info
         * GET /api/auth/me
         */
        getCurrentUser: () => get('/auth/me'),

        /**
         * Change password
         * PUT /api/auth/password
         */
        changePassword: (oldPassword, newPassword) =>
            put('/auth/password', { oldPassword, newPassword }),

        /**
         * 发送验证码（邮箱/手机）
         * POST /api/auth/send-code
         */
        sendCode: (tenantUid, method, target) =>
            postJson('/auth/send-code', { tenantUid, method, target }),

        /**
         * 验证码登录（邮箱/手机）
         * POST /api/auth/login-by-code
         */
        loginByCode: (tenantUid, method, target, code) =>
            postJson('/auth/login-by-code', { tenantUid, method, target, code })
    };

    // ========== Users API ==========
    const UsersAPI = {
        /**
         * Search users with pagination
         * GET /api/users?page=1&size=10&keyword=xxx
         */
        search: (page = 1, size = 10, keyword = null) =>
            get('/users', { page, size, keyword }),

        /**
         * Get user by ID
         * GET /api/users/{id}
         */
        getById: (id) => get(`/users/${id}`),

        /**
         * Assign roles to user
         * POST /api/users/{id}/roles
         */
        assignRoles: (id, roleIds) =>
            postJson(`/users/${id}/roles`, { roleIds }),

        /**
         * Update user status
         * PUT /api/users/{id}/status?status=0/1
         */
        updateStatus: (id, status) =>
            fetch(`${BASE_URL}/users/${id}/status?status=${status}`, {
                method: 'PUT',
                headers: { 'Authorization': `Bearer ${Auth.getAccessToken()}` }
            }).then(handleResponse),

        /**
         * Update user profile
         * PUT /api/users/{id}
         */
        update: (id, data) => put(`/users/${id}`, data),

        /**
         * Delete user
         * DELETE /api/users/{id}
         */
        delete: (id) => del(`/users/${id}`)
    };

    // ========== Roles API ==========
    const RolesAPI = {
        /**
         * Get all roles
         * GET /api/roles
         */
        getAll: () => get('/roles'),

        /**
         * Get role by ID
         * GET /api/roles/{id}
         */
        getById: (id) => get(`/roles/${id}`),

        /**
         * Create role
         * POST /api/roles
         */
        create: (code, name, description) => {
            const params = new URLSearchParams();
            params.append('code', code);
            params.append('name', name);
            if (description) params.append('description', description);
            return fetch(`${BASE_URL}/roles`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/x-www-form-urlencoded',
                    'Authorization': `Bearer ${Auth.getAccessToken()}`
                },
                body: params
            }).then(handleResponse);
        },

        /**
         * Update role
         * PUT /api/roles/{id}
         */
        update: (id, name, description) => {
            const params = new URLSearchParams();
            params.append('name', name);
            if (description) params.append('description', description);
            return fetch(`${BASE_URL}/roles/${id}`, {
                method: 'PUT',
                headers: {
                    'Content-Type': 'application/x-www-form-urlencoded',
                    'Authorization': `Bearer ${Auth.getAccessToken()}`
                },
                body: params
            }).then(handleResponse);
        },

        /**
         * Assign permissions to role
         * POST /api/roles/{id}/permissions
         */
        assignPermissions: (id, permissionIds) =>
            postJson(`/roles/${id}/permissions`, { permissionIds }),

        /**
         * Delete role
         * DELETE /api/roles/{id}
         */
        delete: (id) => del(`/roles/${id}`)
    };

    // ========== Permissions API ==========
    const PermissionsAPI = {
        /**
         * Get all permissions
         * GET /api/permissions
         */
        getAll: () => get('/permissions'),

        /**
         * Get permission by ID
         * GET /api/permissions/{id}
         */
        getById: (id) => get(`/permissions/${id}`),

        /**
         * Create permission
         * POST /api/permissions
         */
        create: (code, name, resource, action, description) => {
            const params = new URLSearchParams();
            params.append('code', code);
            params.append('name', name);
            params.append('resource', resource);
            params.append('action', action);
            if (description) params.append('description', description);
            return fetch(`${BASE_URL}/permissions`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/x-www-form-urlencoded',
                    'Authorization': `Bearer ${Auth.getAccessToken()}`
                },
                body: params
            }).then(handleResponse);
        },

        /**
         * Delete permission
         * DELETE /api/permissions/{id}
         */
        delete: (id) => del(`/permissions/${id}`)
    };

    // ========== Tenants API ==========
    const TenantsAPI = {
        /**
         * Get all tenants
         * GET /api/tenant
         */
        getAll: () => get('/tenant'),

        /**
         * Get tenant by ID
         * GET /api/tenant/{id}
         */
        getById: (uid) => get(`/tenant/${uid}`),

        /**
         * Create tenant
         * POST /api/tenant
         */
        create: (tenantCode, tenantName, status, maxUsers, expiredAt) =>
            postJson('/tenant', {
                tenantCode,
                tenantName,
                status,
                maxUsers,
                expiredAt
            }),

        /**
         * Update tenant
         * PUT /api/tenant/{id}
         */
        update: (uid, data) => {
            return request(`/tenant/${uid}`, {
                method: 'PUT',
                body: JSON.stringify(data)
            });
        },

        /**
         * Delete tenant
         * DELETE /api/tenant/{id}
         */
        delete: (uid) => del(`/tenant/${uid}`),

        /**
         * Check if tenant code is available
         * GET /api/tenant/check-code?code=xxx
         */
        checkCode: (code) => get('/tenant/check-code', { code }),

        /**
         * Get available tenants (for login page)
         * GET /api/tenant/available
         */
        getAvailable: () => get('/tenant/available')
    };

    // ========== Upload API ==========
    const UploadAPI = {
        /**
         * Upload avatar (multipart/form-data)
         * POST /api/upload/avatar → { url }
         */
        avatar: (file, targetUserId) => {
            const formData = new FormData();
            formData.append('file', file);
            const query = targetUserId ? '?targetUserId=' + encodeURIComponent(targetUserId) : '';
            return postForm('/upload/avatar' + query, formData);
        }
    };

    // ========== Login Methods API（平台级 + 租户级二级开关） ==========
    const LoginMethodsAPI = {
        /** 平台级：列出所有登录方式及平台总开关/默认凭证配置状态（平台管理员） */
        listPlatform: () => get('/platform/login-methods'),

        /** 平台级：保存某方式的开关与默认凭证 */
        savePlatform: (method, data) => put(`/platform/login-methods/${method}`, data),

        /** 租户级：列出本租户可配置的登录方式（平台已开启的子集）及本租户开关/凭证来源 */
        listTenant: () => get('/tenant/login-methods'),

        /** 租户级：保存本租户某方式的开关与凭证来源 */
        saveTenant: (method, data) => put(`/tenant/login-methods/${method}`, data),

        /** 公开：按对外租户标识查询该租户对用户开放的登录方式列表（登录页动态渲染用） */
        getEnabled: (tenantUid) => get('/auth/login-methods', { tenantUid }),

        /** 当前用户的第三方绑定列表 */
        listMyBindings: () => get('/auth/me/oauth'),

        /** 获取绑定授权 URL（需登录态，fetch 带 token 拿到 URL 再由前端跳转） */
        getBindUrl: (provider) => get('/auth/oauth/' + encodeURIComponent(provider) + '/bind'),

        /** 解绑某第三方平台 */
        unbind: (provider) => del('/auth/me/oauth/' + encodeURIComponent(provider)),

        /** 绑定/换绑邮箱（验证码验证后覆盖旧值） */
        bindEmail: (method, target, code) => postJson('/auth/me/email', { method, target, code }),

        /** 解绑邮箱 */
        unbindEmail: () => del('/auth/me/email'),

        /** 绑定/换绑手机号（验证码验证后覆盖旧值） */
        bindPhone: (method, target, code) => postJson('/auth/me/phone', { method, target, code }),

        /** 解绑手机号 */
        unbindPhone: () => del('/auth/me/phone')
    };

    // Public API
    return {
        Auth: AuthAPI,
        Users: UsersAPI,
        Roles: RolesAPI,
        Permissions: PermissionsAPI,
        Tenants: TenantsAPI,
        Upload: UploadAPI,
        LoginMethods: LoginMethodsAPI
    };
})();
