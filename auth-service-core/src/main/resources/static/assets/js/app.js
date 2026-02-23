/**
 * Main Application Module
 * Handles dashboard initialization and page routing
 */

(function() {
    'use strict';

    // State
    let currentPage = 'dashboard';
    let usersData = { items: [], total: 0 };
    let rolesData = [];
    let permissionsData = [];
    let tenantsData = [];
    let currentUsersPage = 1;
    let currentUsersKeyword = null;

    /**
     * Initialize application
     */
    async function init() {
        // Check authentication
        if (!Auth.checkAuthAndRedirect()) {
            return;
        }

        // Fetch current user info first
        try {
            const userResponse = await API.Auth.getCurrentUser();
            console.log('Current user response:', userResponse);
            console.log('User tenantId:', userResponse.tenantId);
            console.log('Is platform tenant:', userResponse.tenantId === 0);
            localStorage.setItem('auth_user', JSON.stringify(userResponse));
        } catch (error) {
            console.error('Failed to fetch user info:', error);
            // If we can't get user info, clear auth and redirect to login
            Auth.clearAuthData();
            window.location.href = '/login.html';
            return;
        }

        // Update user info display
        updateUserInfoDisplay();

        // Show/hide platform admin features
        updatePlatformFeatures();

        // Setup navigation
        setupNavigation();

        // Setup sidebar toggle
        setupSidebarToggle();

        // Setup logout
        setupLogout();

        // Setup modals
        setupModals();

        // Load initial page - all tenants go to dashboard first
        navigateTo('dashboard');
    }

    /**
     * Update user info display in sidebar and header
     */
    function updateUserInfoDisplay() {
        const user = Auth.getCurrentUser();
        if (!user) return;

        // Sidebar user info
        document.getElementById('userAvatar').textContent = Auth.getUserAvatar();
        document.getElementById('userName').textContent = user.username || '-';

        // Display tenant info more user-friendly
        const tenantDisplay = user.tenantId === 0 ? '平台租户' : `租户ID: ${user.tenantId}`;
        document.getElementById('userTenant').textContent = tenantDisplay;

        // Header role badge
        const roles = Auth.getUserRolesDisplay();
        const roleBadge = document.getElementById('userRoleBadge');
        roleBadge.textContent = roles.join(', ') || '-';
    }

    /**
     * Show/hide features based on tenant type
     * Platform tenant (tenantId=0): show tenant management only
     * Normal tenant (tenantId>0): show users, roles, permissions management
     */
    function updatePlatformFeatures() {
        const isPlatformTenant = Auth.isPlatformTenant();
        console.log('updatePlatformFeatures - isPlatformTenant:', isPlatformTenant);
        console.log('updatePlatformFeatures - user from localStorage:', JSON.parse(localStorage.getItem('auth_user')));

        // Platform tenant elements (tenant management)
        const platformTenantElements = document.querySelectorAll('.platform-tenant-only');
        console.log('Platform tenant elements found:', platformTenantElements.length);
        platformTenantElements.forEach(el => {
            el.style.display = isPlatformTenant ? '' : 'none';
        });

        // Normal tenant elements (users, roles, permissions)
        const tenantElements = document.querySelectorAll('.tenant-only');
        console.log('Tenant elements found:', tenantElements.length);
        tenantElements.forEach(el => {
            el.style.display = isPlatformTenant ? 'none' : '';
        });
    }

    /**
     * Setup navigation
     */
    function setupNavigation() {
        const navLinks = document.querySelectorAll('.nav-link');
        navLinks.forEach(link => {
            link.addEventListener('click', (e) => {
                e.preventDefault();
                const page = link.getAttribute('data-page');
                navigateTo(page);
            });
        });
    }

    /**
     * Navigate to page
     */
    async function navigateTo(page) {
        // Update nav links
        document.querySelectorAll('.nav-link').forEach(link => {
            link.classList.remove('active');
            if (link.getAttribute('data-page') === page) {
                link.classList.add('active');
            }
        });

        // Show page
        document.querySelectorAll('.page').forEach(p => p.classList.remove('active'));
        const pageElement = document.getElementById(`page-${page}`);
        if (pageElement) {
            pageElement.classList.add('active');
        }

        currentPage = page;

        // Load page data
        switch (page) {
            case 'dashboard':
                await loadDashboard();
                break;
            case 'users':
                await loadUsers();
                break;
            case 'roles':
                await loadRoles();
                break;
            case 'permissions':
                await loadPermissions();
                break;
            case 'tenants':
                await loadTenants();
                break;
        }

        // Close sidebar on mobile
        if (window.innerWidth <= 768) {
            document.getElementById('sidebar').classList.remove('active');
        }
    }

    /**
     * Setup sidebar toggle
     */
    function setupSidebarToggle() {
        const menuToggle = document.getElementById('menuToggle');
        const sidebar = document.getElementById('sidebar');

        menuToggle.addEventListener('click', () => {
            sidebar.classList.toggle('active');
        });

        // Close sidebar when clicking outside on mobile
        document.addEventListener('click', (e) => {
            if (window.innerWidth <= 768) {
                if (!sidebar.contains(e.target) && !menuToggle.contains(e.target)) {
                    sidebar.classList.remove('active');
                }
            }
        });
    }

    /**
     * Setup logout
     */
    function setupLogout() {
        document.getElementById('logoutBtn').addEventListener('click', async () => {
            try {
                await API.Auth.logout();
            } catch (error) {
                console.error('Logout error:', error);
            } finally {
                Auth.clearAuthData();
                window.location.href = '/login.html';
            }
        });
    }

    /**
     * Setup modals
     */
    function setupModals() {
        // Close buttons
        document.querySelectorAll('[data-close]').forEach(btn => {
            btn.addEventListener('click', () => {
                const modalId = btn.getAttribute('data-close');
                closeModal(modalId);
            });
        });

        // Close on backdrop click
        document.querySelectorAll('.modal').forEach(modal => {
            modal.addEventListener('click', (e) => {
                if (e.target === modal) {
                    closeModal(modal.id);
                }
            });
        });

        // User modal
        setupUserModal();
        // Role modal
        setupRoleModal();
        // Permission modal
        setupPermissionModal();
        // Tenant modal
        setupTenantModal();
    }

    function openModal(modalId) {
        document.getElementById(modalId).classList.add('active');
    }

    function closeModal(modalId) {
        document.getElementById(modalId).classList.remove('active');
    }

    // ========== Dashboard ==========
    async function loadDashboard() {
        try {
            const isPlatformTenant = Auth.isPlatformTenant();

            if (isPlatformTenant) {
                // Platform tenant: load tenants count only
                try {
                    const tenants = await API.Tenants.getAll();
                    document.getElementById('statTenants').textContent = tenants.length || 0;
                } catch (error) {
                    document.getElementById('statTenants').textContent = '0';
                }
            } else {
                // Normal tenant: load users, roles, permissions counts
                const [usersResult, rolesResult, permissionsResult] = await Promise.all([
                    API.Users.search(1, 1).catch(() => ({ total: 0 })),
                    API.Roles.getAll().catch(() => []),
                    API.Permissions.getAll().catch(() => [])
                ]);

                document.getElementById('statUsers').textContent = usersResult.total || 0;
                document.getElementById('statRoles').textContent = rolesResult.length || 0;
                document.getElementById('statPermissions').textContent = permissionsResult.length || 0;
            }
        } catch (error) {
            Toast.error('加载仪表盘数据失败: ' + error.message);
        }
    }

    // ========== Users ==========
    async function loadUsers() {
        try {
            const result = await API.Users.search(currentUsersPage, 10, currentUsersKeyword);
            usersData = result;
            renderUsersTable();
            renderUsersPagination();
        } catch (error) {
            Toast.error('加载用户列表失败: ' + error.message);
        }
    }

    function renderUsersTable() {
        const tbody = document.getElementById('usersTableBody');
        if (usersData.items.length === 0) {
            tbody.innerHTML = '<tr><td colspan="7" class="text-center">暂无数据</td></tr>';
            return;
        }

        tbody.innerHTML = usersData.items.map(user => `
            <tr>
                <td>${user.id}</td>
                <td>${escapeHtml(user.username)}</td>
                <td>${escapeHtml(user.email || '-')}</td>
                <td>${escapeHtml(user.nickname || '-')}</td>
                <td>${user.status === 1 ?
                    '<span class="badge badge-success">启用</span>' :
                    '<span class="badge badge-danger">禁用</span>'}</td>
                <td>${Array.isArray(user.roles) && user.roles.length > 0 ? user.roles.map(r =>
                    `<span class="badge badge-info">${r.replace('ROLE_', '')}</span>`
                ).join(' ') : '<span class="text-muted">未分配</span>'}</td>
                <td>
                    <div class="action-buttons">
                        <button class="btn btn-sm btn-outline" onclick="App.editUser(${user.id})">编辑</button>
                        <button class="btn btn-sm btn-secondary" onclick="App.assignUserRoles(${user.id})">角色</button>
                        <button class="btn btn-sm btn-danger" onclick="App.deleteUser(${user.id})">删除</button>
                    </div>
                </td>
            </tr>
        `).join('');
    }

    function renderUsersPagination() {
        const container = document.getElementById('usersPagination');
        const totalPages = Math.ceil((usersData.total || 0) / 10);
        if (totalPages <= 1) {
            container.innerHTML = '';
            return;
        }

        let html = `
            <button ${currentUsersPage === 1 ? 'disabled' : ''} onclick="App.usersPage(${currentUsersPage - 1})">上一页</button>
            <span class="page-info">第 ${currentUsersPage} / ${totalPages} 页</span>
            <button ${currentUsersPage >= totalPages ? 'disabled' : ''} onclick="App.usersPage(${currentUsersPage + 1})">下一页</button>
        `;
        container.innerHTML = html;
    }

    function setupUserModal() {
        // Add user button
        document.getElementById('addUserBtn').addEventListener('click', () => {
            openUserModal();
        });

        // Search
        document.getElementById('userSearchBtn').addEventListener('click', async () => {
            currentUsersKeyword = document.getElementById('userSearch').value || null;
            currentUsersPage = 1;
            await loadUsers();
        });

        // Reset
        document.getElementById('userResetBtn').addEventListener('click', async () => {
            document.getElementById('userSearch').value = '';
            currentUsersKeyword = null;
            currentUsersPage = 1;
            await loadUsers();
        });

        // Save
        document.getElementById('userSaveBtn').addEventListener('click', async () => {
            await saveUser();
        });

        // User roles modal
        setupUserRolesModal();
    }

    async function openUserModal(user = null) {
        const modal = document.getElementById('userModal');
        document.getElementById('userModalTitle').textContent = user ? '编辑用户' : '添加用户';
        document.getElementById('userId').value = user ? user.id : '';
        document.getElementById('userUsername').value = user ? user.username : '';
        document.getElementById('userPassword').value = '';
        document.getElementById('userPassword').placeholder = user ? '留空则不修改密码' : '请输入密码';
        document.getElementById('userEmail').value = user ? (user.email || '') : '';
        document.getElementById('userNickname').value = user ? (user.nickname || '') : '';
        document.getElementById('userStatus').value = user ? user.status : 1;

        openModal('userModal');
    }

    async function saveUser() {
        const id = document.getElementById('userId').value;
        const username = document.getElementById('userUsername').value.trim();
        const password = document.getElementById('userPassword').value;
        const email = document.getElementById('userEmail').value.trim();
        const nickname = document.getElementById('userNickname').value.trim();
        const status = parseInt(document.getElementById('userStatus').value);

        if (!username) {
            Toast.error('请输入用户名');
            return;
        }

        if (!id && !password) {
            Toast.error('请输入密码');
            return;
        }

        try {
            // For now, we only support updating user status
            // Creating new user would need registration endpoint
            if (id) {
                await API.Users.updateStatus(id, status);
                Toast.success('用户更新成功');
            }
            closeModal('userModal');
            await loadUsers();
        } catch (error) {
            Toast.error('保存失败: ' + error.message);
        }
    }

    async function editUser(id) {
        try {
            const user = await API.Users.getById(id);
            openUserModal(user);
        } catch (error) {
            Toast.error('加载用户信息失败: ' + error.message);
        }
    }

    async function deleteUser(id) {
        if (!confirm('确定要删除该用户吗？')) return;

        try {
            await API.Users.delete(id);
            Toast.success('用户删除成功');
            await loadUsers();
        } catch (error) {
            Toast.error('删除失败: ' + error.message);
        }
    }

    async function assignUserRoles(userId) {
        try {
            const user = await API.Users.getById(userId);
            const roles = await API.Roles.getAll();

            const checkboxContainer = document.getElementById('userRolesCheckboxes');
            checkboxContainer.innerHTML = roles.map(r => `
                <label class="checkbox-item">
                    <input type="checkbox" name="userRoles" value="${r.id}"
                        ${user.roles && user.roles.includes(r.code) ? 'checked' : ''}>
                    ${escapeHtml(r.name)} (${escapeHtml(r.code)})
                </label>
            `).join('');

            document.getElementById('userRolesUserId').value = userId;
            openModal('userRolesModal');
        } catch (error) {
            Toast.error('加载数据失败: ' + error.message);
        }
    }

    async function saveUserRoles() {
        const userId = document.getElementById('userRolesUserId').value;
        const checkboxes = document.querySelectorAll('input[name="userRoles"]:checked');
        const roleIds = Array.from(checkboxes).map(cb => parseInt(cb.value));

        try {
            await API.Users.assignRoles(userId, roleIds);
            Toast.success('角色分配成功');
            closeModal('userRolesModal');
            await loadUsers();
        } catch (error) {
            Toast.error('保存失败: ' + error.message);
        }
    }

    function setupUserRolesModal() {
        document.getElementById('userRolesSaveBtn').addEventListener('click', async () => {
            await saveUserRoles();
        });
    }

    function usersPage(page) {
        currentUsersPage = page;
        loadUsers();
    }

    // ========== Roles ==========
    async function loadRoles() {
        try {
            rolesData = await API.Roles.getAll();
            renderRolesTable();
        } catch (error) {
            Toast.error('加载角色列表失败: ' + error.message);
        }
    }

    function renderRolesTable() {
        const tbody = document.getElementById('rolesTableBody');
        if (rolesData.length === 0) {
            tbody.innerHTML = '<tr><td colspan="6" class="text-center">暂无数据</td></tr>';
            return;
        }

        tbody.innerHTML = rolesData.map(role => `
            <tr>
                <td>${role.id}</td>
                <td>${escapeHtml(role.code)}</td>
                <td>${escapeHtml(role.name)}</td>
                <td>${escapeHtml(role.description || '-')}</td>
                <td>${role.status === 1 ?
                    '<span class="badge badge-success">启用</span>' :
                    '<span class="badge badge-danger">禁用</span>'}</td>
                <td>
                    <div class="action-buttons">
                        <button class="btn btn-sm btn-outline" onclick="App.editRole(${role.id})">编辑</button>
                        <button class="btn btn-sm btn-secondary" onclick="App.rolePermissions(${role.id})">权限</button>
                        <button class="btn btn-sm btn-danger" onclick="App.deleteRole(${role.id})">删除</button>
                    </div>
                </td>
            </tr>
        `).join('');
    }

    function setupRoleModal() {
        document.getElementById('addRoleBtn').addEventListener('click', () => {
            openRoleModal();
        });
        document.getElementById('roleSaveBtn').addEventListener('click', async () => {
            await saveRole();
        });
        document.getElementById('rolePermissionsSaveBtn').addEventListener('click', async () => {
            await saveRolePermissions();
        });
    }

    async function openRoleModal(role = null) {
        document.getElementById('roleModalTitle').textContent = role ? '编辑角色' : '添加角色';
        document.getElementById('roleId').value = role ? role.id : '';
        document.getElementById('roleCode').value = role ? role.code : '';
        document.getElementById('roleCode').disabled = !!role;
        document.getElementById('roleName').value = role ? role.name : '';
        document.getElementById('roleDescription').value = role ? (role.description || '') : '';
        openModal('roleModal');
    }

    async function saveRole() {
        const id = document.getElementById('roleId').value;
        const code = document.getElementById('roleCode').value.trim();
        const name = document.getElementById('roleName').value.trim();
        const description = document.getElementById('roleDescription').value.trim();

        if (!code || !name) {
            Toast.error('请填写必填项');
            return;
        }

        try {
            if (id) {
                await API.Roles.update(id, name, description);
                Toast.success('角色更新成功');
            } else {
                await API.Roles.create(code, name, description);
                Toast.success('角色创建成功');
            }
            closeModal('roleModal');
            await loadRoles();
        } catch (error) {
            Toast.error('保存失败: ' + error.message);
        }
    }

    async function editRole(id) {
        try {
            const role = await API.Roles.getById(id);
            openRoleModal(role);
        } catch (error) {
            Toast.error('加载角色信息失败: ' + error.message);
        }
    }

    async function deleteRole(id) {
        if (!confirm('确定要删除该角色吗？')) return;

        try {
            await API.Roles.delete(id);
            Toast.success('角色删除成功');
            await loadRoles();
        } catch (error) {
            Toast.error('删除失败: ' + error.message);
        }
    }

    async function rolePermissions(id) {
        try {
            const role = await API.Roles.getById(id);
            const permissions = await API.Permissions.getAll();

            const checkboxContainer = document.getElementById('permissionsCheckboxes');
            checkboxContainer.innerHTML = permissions.map(p => `
                <label class="checkbox-item">
                    <input type="checkbox" name="rolePermissions" value="${p.id}"
                        ${role.permissions && role.permissions.includes(p.code) ? 'checked' : ''}>
                    ${escapeHtml(p.name)} (${escapeHtml(p.code)})
                </label>
            `).join('');

            document.getElementById('rolePermissionsRoleId').value = id;
            openModal('rolePermissionsModal');
        } catch (error) {
            Toast.error('加载数据失败: ' + error.message);
        }
    }

    async function saveRolePermissions() {
        const roleId = document.getElementById('rolePermissionsRoleId').value;
        const checkboxes = document.querySelectorAll('#permissionsCheckboxes input:checked');
        const permissionIds = Array.from(checkboxes).map(cb => parseInt(cb.value));

        try {
            await API.Roles.assignPermissions(roleId, permissionIds);
            Toast.success('权限分配成功');
            closeModal('rolePermissionsModal');
        } catch (error) {
            Toast.error('保存失败: ' + error.message);
        }
    }

    // ========== Permissions ==========
    async function loadPermissions() {
        try {
            permissionsData = await API.Permissions.getAll();
            renderPermissionsTable();
        } catch (error) {
            Toast.error('加载权限列表失败: ' + error.message);
        }
    }

    function renderPermissionsTable() {
        const tbody = document.getElementById('permissionsTableBody');
        if (permissionsData.length === 0) {
            tbody.innerHTML = '<tr><td colspan="7" class="text-center">暂无数据</td></tr>';
            return;
        }

        tbody.innerHTML = permissionsData.map(p => `
            <tr>
                <td>${p.id}</td>
                <td>${escapeHtml(p.code)}</td>
                <td>${escapeHtml(p.name)}</td>
                <td>${escapeHtml(p.resource)}</td>
                <td>${escapeHtml(p.action)}</td>
                <td>${escapeHtml(p.description || '-')}</td>
                <td>
                    <button class="btn btn-sm btn-danger" onclick="App.deletePermission(${p.id})">删除</button>
                </td>
            </tr>
        `).join('');
    }

    function setupPermissionModal() {
        document.getElementById('addPermissionBtn').addEventListener('click', () => {
            document.getElementById('permissionModalTitle').textContent = '添加权限';
            document.getElementById('permissionId').value = '';
            document.getElementById('permissionCode').value = '';
            document.getElementById('permissionName').value = '';
            document.getElementById('permissionResource').value = '';
            document.getElementById('permissionAction').value = '';
            document.getElementById('permissionDescription').value = '';
            openModal('permissionModal');
        });

        document.getElementById('permissionSaveBtn').addEventListener('click', async () => {
            await savePermission();
        });
    }

    async function savePermission() {
        const code = document.getElementById('permissionCode').value.trim();
        const name = document.getElementById('permissionName').value.trim();
        const resource = document.getElementById('permissionResource').value.trim();
        const action = document.getElementById('permissionAction').value;
        const description = document.getElementById('permissionDescription').value.trim();

        if (!code || !name || !resource || !action) {
            Toast.error('请填写必填项');
            return;
        }

        try {
            await API.Permissions.create(code, name, resource, action, description);
            Toast.success('权限创建成功');
            closeModal('permissionModal');
            await loadPermissions();
        } catch (error) {
            Toast.error('保存失败: ' + error.message);
        }
    }

    async function deletePermission(id) {
        if (!confirm('确定要删除该权限吗？')) return;

        try {
            await API.Permissions.delete(id);
            Toast.success('权限删除成功');
            await loadPermissions();
        } catch (error) {
            Toast.error('删除失败: ' + error.message);
        }
    }

    // ========== Tenants ==========
    async function loadTenants() {
        try {
            tenantsData = await API.Tenants.getAll();
            renderTenantsTable();
        } catch (error) {
            Toast.error('加载租户列表失败: ' + error.message);
        }
    }

    function renderTenantsTable() {
        const tbody = document.getElementById('tenantsTableBody');
        if (tenantsData.length === 0) {
            tbody.innerHTML = '<tr><td colspan="7" class="text-center">暂无数据</td></tr>';
            return;
        }

        tbody.innerHTML = tenantsData.map(t => `
            <tr>
                <td>${t.id}</td>
                <td>${escapeHtml(t.tenantCode)}</td>
                <td>${escapeHtml(t.tenantName)}</td>
                <td>${t.status === 1 ?
                    '<span class="badge badge-success">启用</span>' :
                    '<span class="badge badge-danger">禁用</span>'}</td>
                <td>${t.currentUserCount || 0} / ${t.maxUsers || '∞'}</td>
                <td>${t.expiredAt ? new Date(t.expiredAt).toLocaleDateString() : '-'}</td>
                <td>
                    <div class="action-buttons">
                        <button class="btn btn-sm btn-outline" onclick="App.editTenant(${t.id})">编辑</button>
                        ${t.id !== 0 ? `<button class="btn btn-sm btn-danger" onclick="App.deleteTenant(${t.id})">删除</button>` : ''}
                    </div>
                </td>
            </tr>
        `).join('');
    }

    function setupTenantModal() {
        document.getElementById('addTenantBtn').addEventListener('click', () => {
            openTenantModal();
        });

        document.getElementById('tenantSaveBtn').addEventListener('click', async () => {
            await saveTenant();
        });
    }

    async function openTenantModal(tenant = null) {
        document.getElementById('tenantModalTitle').textContent = tenant ? '编辑租户' : '添加租户';
        document.getElementById('tenantId').value = tenant ? tenant.id : '';
        document.getElementById('tenantCode').value = tenant ? tenant.tenantCode : '';
        document.getElementById('tenantCode').disabled = !!tenant;
        document.getElementById('tenantName').value = tenant ? tenant.tenantName : '';
        document.getElementById('tenantStatus').value = tenant ? tenant.status : 1;
        document.getElementById('tenantMaxUsers').value = tenant ? (tenant.maxUsers || 100) : 100;
        document.getElementById('tenantExpiredAt').value = tenant && tenant.expiredAt ?
            new Date(tenant.expiredAt).toISOString().slice(0, 16) : '';
        openModal('tenantModal');
    }

    async function saveTenant() {
        const id = document.getElementById('tenantId').value;
        const tenantCode = document.getElementById('tenantCode').value.trim();
        const tenantName = document.getElementById('tenantName').value.trim();
        const status = parseInt(document.getElementById('tenantStatus').value);
        const maxUsers = parseInt(document.getElementById('tenantMaxUsers').value) || 100;
        const expiredAt = document.getElementById('tenantExpiredAt').value;

        if (!tenantCode || !tenantName) {
            Toast.error('请填写必填项');
            return;
        }

        try {
            const data = {
                tenantName,
                status,
                maxUsers,
                expiredAt: expiredAt || null
            };

            if (id) {
                await API.Tenants.update(id, data);
                Toast.success('租户更新成功');
            } else {
                await API.Tenants.create(tenantCode, tenantName, status, maxUsers, expiredAt);
                Toast.success('租户创建成功');
            }
            closeModal('tenantModal');
            await loadTenants();
        } catch (error) {
            Toast.error('保存失败: ' + error.message);
        }
    }

    async function editTenant(id) {
        const tenant = tenantsData.find(t => t.id === id);
        if (tenant) {
            openTenantModal(tenant);
        }
    }

    async function deleteTenant(id) {
        // Get tenant info to show in warning
        let tenantName = '';
        try {
            const tenant = await API.Tenants.getById(id);
            tenantName = tenant.tenantName || tenant.tenantCode || `ID: ${id}`;
        } catch (e) {
            tenantName = `ID: ${id}`;
        }

        const warningMessage = `确定要删除租户「${tenantName}」吗？\n\n` +
            `此操作将同时删除该租户下的所有数据，包括：\n` +
            `• 所有用户\n` +
            `• 所有角色\n` +
            `• 所有权限\n` +
            `• 用户和角色的关联关系\n` +
            `• 角色和权限的关联关系\n\n` +
            `此操作不可恢复！`;

        if (!confirm(warningMessage)) return;

        try {
            await API.Tenants.delete(id);
            Toast.success('租户删除成功');
            await loadTenants();
        } catch (error) {
            Toast.error('删除失败: ' + error.message);
        }
    }

    // ========== Utility Functions ==========
    function escapeHtml(text) {
        if (!text) return '';
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }

    // ========== Toast Module ==========
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

    // ========== Public API ==========
    window.App = {
        editUser,
        deleteUser,
        assignUserRoles,
        usersPage,
        editRole,
        deleteRole,
        rolePermissions,
        deletePermission,
        editTenant,
        deleteTenant
    };

    // Also expose Toast globally
    window.Toast = Toast;

    // Initialize on DOM ready
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();
