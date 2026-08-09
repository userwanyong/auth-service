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
    let roleMap = {};
    let permissionsData = [];
    let tenantsData = [];
    let currentUsersPage = 1;
    let currentUsersKeyword = null;

    function isPlatformTenantId(tenantId) {
        return String(tenantId) === '0';
    }

    function quoteJsString(value) {
        return JSON.stringify(String(value));
    }

    /**
     * Initialize application
     */
    async function init() {
        // Check authentication
        if (!Auth.checkAuthAndRedirect()) {
            return;
        }

        // 立即（同步）切换到 URL hash 指定的页面，避免刷新时先闪现仪表盘再跳转。
        // 权限判断先用 localStorage 中上次缓存的用户信息（fetch 之前即可读取）。
        const targetPage = getPageFromHash();
        switchPageView(targetPage);

        // Fetch current user info
        try {
            const userResponse = await API.Auth.getCurrentUser();
            localStorage.setItem('auth_user', JSON.stringify(userResponse));
        } catch (error) {
            console.error('Failed to fetch user info:', error);
            // If we can't get user info, clear auth and redirect to login
            Auth.clearAuthData();
            window.location.href = '/login.html';
            return;
        }

        // 先加载角色映射，用户信息区的角色徽章要显示角色名称
        await ensureRoleMap();

        // Update user info display
        updateUserInfoDisplay();

        // Show/hide platform admin features
        updatePlatformFeatures();

        // 用户信息刷新后，若租户类型与缓存不一致导致页面归属变化，重新校正显示
        const correctedPage = resolveAccessiblePage(targetPage);
        if (correctedPage !== currentPage) {
            switchPageView(correctedPage);
        }

        // Setup navigation
        setupNavigation();

        // Setup sidebar toggle
        setupSidebarToggle();

        // Setup logout
        setupLogout();

        // Setup modals
        setupModals();

        // Setup hash-based routing (so refresh / browser back-forward restores the page)
        setupHashRouting();

        // Load current page data
        await loadPageData(currentPage);
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
        const tenantDisplay = isPlatformTenantId(user.tenantId) ? '平台租户' : `租户ID: ${user.tenantId}`;
        document.getElementById('userTenant').textContent = tenantDisplay;

        // Header role badge（显示角色名称）
        const roleNames = (user.roles || []).map(r => roleDisplayName(r));
        const roleBadge = document.getElementById('userRoleBadge');
        roleBadge.textContent = roleNames.join(', ') || '-';
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

    // Pages that can be stored in the URL hash
    const VALID_PAGES = ['dashboard', 'users', 'roles', 'permissions', 'tenants'];

    /**
     * Read target page from URL hash, fall back to dashboard
     */
    function getPageFromHash() {
        const hash = (window.location.hash || '').replace('#', '');
        return VALID_PAGES.includes(hash) ? hash : 'dashboard';
    }

    /**
     * Keep currentPage in sync with the URL hash so manual URL changes
     * and browser back/forward restore the right page
     */
    function setupHashRouting() {
        window.addEventListener('hashchange', () => {
            const page = getPageFromHash();
            if (page !== currentPage) {
                navigateTo(page);
            }
        });
    }

    /**
     * 根据当前租户类型校正目标页面：
     * 平台租户只能看仪表盘/租户管理；普通租户不能看租户管理。
     */
    function resolveAccessiblePage(page) {
        const isPlatform = Auth.isPlatformTenant();
        if (isPlatform && ['users', 'roles', 'permissions'].includes(page)) {
            return 'dashboard';
        }
        if (!isPlatform && page === 'tenants') {
            return 'dashboard';
        }
        return page;
    }

    /**
     * 同步切换页面显示（DOM 高亮、nav active、URL hash），无网络 IO。
     * 在 init 最早期调用，避免刷新时先闪现仪表盘再跳到目标页面。
     */
    function switchPageView(page) {
        page = resolveAccessiblePage(page);

        document.querySelectorAll('.nav-link').forEach(link => {
            link.classList.toggle('active', link.getAttribute('data-page') === page);
        });
        document.querySelectorAll('.page').forEach(p => p.classList.remove('active'));
        const pageElement = document.getElementById(`page-${page}`);
        if (pageElement) {
            pageElement.classList.add('active');
        }

        currentPage = page;

        // Sync URL hash (replaceState avoids piling up duplicate history entries on each click)
        const targetHash = '#' + page;
        if (window.location.hash !== targetHash) {
            history.replaceState(null, '', targetHash);
        }
    }

    /** 加载当前页面数据，并在移动端收起侧栏 */
    async function loadPageData(page) {
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
     * Navigate to page
     */
    async function navigateTo(page) {
        switchPageView(page);
        await loadPageData(currentPage);
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

    /** 确保角色列表已加载，并维护 code→name 映射，用于把用户角色编码显示为名称 */
    async function ensureRoleMap() {
        if (!rolesData.length) {
            try {
                rolesData = await API.Roles.getAll();
            } catch (error) {
                rolesData = [];
            }
        }
        roleMap = {};
        rolesData.forEach(r => { roleMap[r.code] = r.name; });
    }

    /** 取角色显示名：优先用名称，取不到则去掉 ROLE_ 前缀的编码 */
    function roleDisplayName(code) {
        return roleMap[code] || (code ? code.replace('ROLE_', '') : '');
    }

    async function loadUsers() {
        try {
            await ensureRoleMap();
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
            tbody.innerHTML = '<tr><td colspan="9" class="text-center">暂无数据</td></tr>';
            return;
        }

        tbody.innerHTML = usersData.items.map(user => `
            <tr>
                <td>${user.id}</td>
                <td>${user.avatar ? `<img src="${escapeHtml(user.avatar)}" alt="" style="width:32px;height:32px;border-radius:50%;object-fit:cover" onerror="this.style.display='none'">` : '-'}</td>
                <td>${escapeHtml(user.username)}</td>
                <td>${escapeHtml(user.email || '-')}${verifiedBadge(user.emailVerified)}</td>
                <td>${escapeHtml(user.phone || '-')}${verifiedBadge(user.phoneVerified)}</td>
                <td>${escapeHtml(user.nickname || '-')}</td>
                <td>${user.status === 1 ?
                    '<span class="badge badge-success">启用</span>' :
                    '<span class="badge badge-danger">禁用</span>'}</td>
                <td>${Array.isArray(user.roles) && user.roles.length > 0 ? user.roles.map(r =>
                    `<span class="badge badge-info">${escapeHtml(roleDisplayName(r))}</span>`
                ).join(' ') : '<span class="text-muted">无</span>'}</td>
                <td>
                    <div class="action-buttons">
                        <button class="btn btn-sm btn-outline" onclick='App.showUserDetail(${quoteJsString(user.id)})'>详情</button>
                        <button class="btn btn-sm btn-outline" onclick='App.editUser(${quoteJsString(user.id)})'>编辑</button>
                        <button class="btn btn-sm btn-secondary" onclick='App.assignUserRoles(${quoteJsString(user.id)})'>角色</button>
                        <button class="btn btn-sm btn-danger" onclick='App.deleteUser(${quoteJsString(user.id)})'>删除</button>
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

        // Avatar upload (选文件后自动上传到 OSS)
        document.getElementById('userAvatarFile').addEventListener('change', async (e) => {
            const file = e.target.files[0];
            if (!file) return;
            // 编辑已有用户时带上 targetUserId，让头像落到该用户的 OSS 路径；新建时不带
            const targetUserId = document.getElementById('userId').value || null;
            try {
                const result = await API.Upload.avatar(file, targetUserId);
                document.getElementById('userAvatar').value = result.url;
                const preview = document.getElementById('userAvatarPreview');
                preview.src = result.url;
                preview.style.display = '';
                Toast.success('头像上传成功');
            } catch (error) {
                Toast.error('头像上传失败: ' + error.message);
            }
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
        document.getElementById('userPassword').placeholder = user ? '留空表示不修改密码' : '请输入密码';
        // 必填标识：新增时密码必填（显示红色 *），编辑时选填（留空不修改）
        const passwordLabel = document.getElementById('userPasswordLabel');
        const passwordHint = document.getElementById('userPasswordHint');
        if (passwordLabel && passwordHint) {
            if (user) {
                passwordLabel.innerHTML = '密码';
                passwordHint.textContent = '编辑时留空则不修改密码';
            } else {
                passwordLabel.innerHTML = '密码 <span class="required-mark">*</span>';
                passwordHint.textContent = '密码长度不少于 6 位';
            }
        }
        document.getElementById('userEmail').value = user ? (user.email || '') : '';
        document.getElementById('userNickname').value = user ? (user.nickname || '') : '';
        document.getElementById('userPhone').value = user ? (user.phone || '') : '';
        document.getElementById('userRealName').value = user ? (user.realName || '') : '';
        document.getElementById('userGender').value = user && user.gender != null ? user.gender : 0;
        // birthday 兼容后端返回的字符串("2000-01-01")或数组([2000,1,1])
        document.getElementById('userBirthday').value = user && user.birthday
            ? (Array.isArray(user.birthday)
                ? `${user.birthday[0]}-${String(user.birthday[1]).padStart(2, '0')}-${String(user.birthday[2]).padStart(2, '0')}`
                : String(user.birthday).slice(0, 10))
            : '';
        // 头像回填
        document.getElementById('userAvatar').value = user ? (user.avatar || '') : '';
        document.getElementById('userAvatarFile').value = '';
        const avatarPreview = document.getElementById('userAvatarPreview');
        if (user && user.avatar) {
            avatarPreview.src = user.avatar;
            avatarPreview.style.display = '';
        } else {
            avatarPreview.src = '';
            avatarPreview.style.display = 'none';
        }
        document.getElementById('userStatus').value = user ? user.status : 1;

        openModal('userModal');
    }

    async function saveUser() {
        const id = document.getElementById('userId').value;
        const username = document.getElementById('userUsername').value.trim();
        const password = document.getElementById('userPassword').value;
        const email = document.getElementById('userEmail').value.trim();
        const nickname = document.getElementById('userNickname').value.trim();
        const phone = document.getElementById('userPhone').value.trim();
        const realName = document.getElementById('userRealName').value.trim();
        const gender = parseInt(document.getElementById('userGender').value);
        const birthday = document.getElementById('userBirthday').value;
        const avatar = document.getElementById('userAvatar').value.trim();
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
            if (id) {
                await API.Users.update(id, {
                    username,
                    password: password || null,
                    email: email || null,
                    phone: phone || null,
                    nickname: nickname || null,
                    realName: realName || null,
                    gender,
                    birthday: birthday || null,
                    avatar: avatar || null,
                    status
                });
                Toast.success('用户更新成功');
            } else {
                const currentUser = Auth.getCurrentUser();
                if (!currentUser || currentUser.tenantId === null || currentUser.tenantId === undefined) {
                    throw new Error('无法获取当前租户信息');
                }

                const registerResult = await API.Auth.register({
                    username,
                    password,
                    tenantId: currentUser.tenantId,
                    email: email || null,
                    phone: phone || null,
                    nickname: nickname || null,
                    realName: realName || null,
                    gender,
                    birthday: birthday || null,
                    avatar: avatar || null
                });

                // Register defaults to enabled status. If disabled is selected, sync status after creation.
                if (status !== 1 && registerResult && registerResult.user && registerResult.user.id) {
                    await API.Users.updateStatus(registerResult.user.id, status);
                }

                Toast.success('用户创建成功');
            }
            closeModal('userModal');
            await loadUsers();
        } catch (error) {
            Toast.error('保存失败: ' + error.message);
        }
    }

    async function showUserDetail(id) {
        try {
            await ensureRoleMap();
            const user = await API.Users.getById(id);
            const rolesHtml = Array.isArray(user.roles) && user.roles.length > 0
                ? user.roles.map(r => `<span class="badge badge-info">${escapeHtml(roleDisplayName(r))}</span>`).join(' ')
                : '<span class="text-muted">无</span>';
            const perms = Array.isArray(user.permissions) ? user.permissions : [];
            const permHtml = perms.length
                ? `<div style="display:flex;flex-wrap:wrap;gap:6px;">` +
                  perms.map(p => `<span class="badge badge-info">${escapeHtml(p)}</span>`).join('') +
                  `</div>`
                : '<span class="text-muted">无</span>';
            const rows = [
                ['ID', user.id],
                ['租户ID', user.tenantId],
                ['用户名', escapeHtml(user.username)],
                ['真实姓名', escapeHtml(user.realName || '-')],
                ['邮箱', escapeHtml(user.email || '-') + verifiedBadge(user.emailVerified)],
                ['手机号', escapeHtml(user.phone || '-') + verifiedBadge(user.phoneVerified)],
                ['昵称', escapeHtml(user.nickname || '-')],
                ['头像', user.avatar ? `<img src="${escapeHtml(user.avatar)}" alt="" style="width:48px;height:48px;border-radius:50%;object-fit:cover;vertical-align:middle">` : '-'],
                ['性别', formatGender(user.gender)],
                ['生日', formatDate(user.birthday)],
                ['状态', user.status === 1 ? '<span class="badge badge-success">启用</span>' : '<span class="badge badge-danger">禁用</span>'],
                ['当前角色', `<div style="display:flex;flex-wrap:wrap;gap:6px;">${rolesHtml}</div>`],
                ['最后登录', formatDateTime(user.lastLoginAt)],
                ['创建时间', formatDateTime(user.createdAt)],
                ['更新时间', formatDateTime(user.updatedAt)]
            ];
            document.getElementById('userDetailContent').innerHTML =
                '<div style="display:grid;grid-template-columns:110px 1fr;gap:10px 16px;align-items:start;">' +
                rows.map(([k, v]) => `<strong style="color:var(--text-secondary);padding-top:6px;">${k}</strong><span>${v != null ? v : '-'}</span>`).join('') +
                `<strong style="color:var(--text-secondary);padding-top:6px;">权限 <small style="font-weight:normal;opacity:.7;">(${perms.length})</small></strong><span>${permHtml}</span>` +
                '</div>';
            openModal('userDetailModal');
        } catch (error) {
            Toast.error('加载用户详情失败: ' + error.message);
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
        const roleIds = Array.from(checkboxes).map(cb => cb.value);

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
                        <button class="btn btn-sm btn-outline" onclick='App.editRole(${quoteJsString(role.id)})'>编辑</button>
                        <button class="btn btn-sm btn-secondary" onclick='App.rolePermissions(${quoteJsString(role.id)})'>权限</button>
                        <button class="btn btn-sm btn-danger" onclick='App.deleteRole(${quoteJsString(role.id)})'>删除</button>
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
        const permissionIds = Array.from(checkboxes).map(cb => cb.value);

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
                    <button class="btn btn-sm btn-danger" onclick='App.deletePermission(${quoteJsString(p.id)})'>删除</button>
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
                <td>${t.currentUserCount || 0} / ${t.maxUsers || '-'}</td>
                <td>${t.expiredAt ? new Date(t.expiredAt).toLocaleDateString() : '-'}</td>
                <td>
                    <div class="action-buttons">
                        <button class="btn btn-sm btn-outline" onclick='App.editTenant(${quoteJsString(t.id)})'>编辑</button>
                        ${!isPlatformTenantId(t.id) ? `<button class="btn btn-sm btn-danger" onclick='App.deleteTenant(${quoteJsString(t.id)})'>删除</button>` : ''}
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
        const tenant = tenantsData.find(t => String(t.id) === String(id));
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
            `该操作将同时删除该租户下的所有数据，包括：\n` +
            `- 全部用户\n` +
            `- 全部角色\n` +
            `- 全部权限\n` +
            `- 用户-角色关联\n` +
            `- 角色-权限关联\n\n` +
            `此操作不可撤销。`;

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
    function formatGender(g) {
        if (g === 1) return '男';
        if (g === 2) return '女';
        return '未知';
    }

    /** 格式化日期（兼容字符串 "2000-01-01" 或数组 [2000,1,1]） */
    function formatDate(value) {
        if (!value) return '-';
        if (Array.isArray(value)) {
            return `${value[0]}-${String(value[1]).padStart(2, '0')}-${String(value[2]).padStart(2, '0')}`;
        }
        return String(value).slice(0, 10);
    }

    /** 格式化日期时间（兼容字符串或数组） */
    function formatDateTime(value) {
        if (!value) return '-';
        let d;
        if (Array.isArray(value)) {
            d = new Date(value[0], (value[1] || 1) - 1, value[2] || 1, value[3] || 0, value[4] || 0, value[5] || 0);
        } else {
            d = new Date(value);
        }
        return isNaN(d.getTime()) ? String(value) : d.toLocaleString();
    }

    /** 验证状态徽章 */
    function formatVerified(v) {
        return v ? '<span class="badge badge-success">已验证</span>'
                 : '<span class="badge badge-danger">未验证</span>';
    }

    /** 邮箱/手机验证状态徽章（已验证绿/未验证红） */
    function verifiedBadge(v) {
        return v ? ' <span class="badge badge-success">已验证</span>'
                 : ' <span class="badge badge-danger">未验证</span>';
    }

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
                success: 'OK',
                error: 'X',
                warning: '!',
                info: 'i'
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
        showUserDetail,
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



