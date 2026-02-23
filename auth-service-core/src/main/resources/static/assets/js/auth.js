/**
 * Authentication Module
 * Handles JWT token management and user authentication state
 */

const Auth = (function() {
    const TOKEN_KEY = 'auth_access_token';
    const REFRESH_TOKEN_KEY = 'auth_refresh_token';
    const USER_KEY = 'auth_user';

    /**
     * Check if user is authenticated
     */
    function isAuthenticated() {
        return !!localStorage.getItem(TOKEN_KEY);
    }

    /**
     * Get access token
     */
    function getAccessToken() {
        return localStorage.getItem(TOKEN_KEY);
    }

    /**
     * Get refresh token
     */
    function getRefreshToken() {
        return localStorage.getItem(REFRESH_TOKEN_KEY);
    }

    /**
     * Get current user info
     */
    function getCurrentUser() {
        const userStr = localStorage.getItem(USER_KEY);
        return userStr ? JSON.parse(userStr) : null;
    }

    /**
     * Check if current user has a specific role
     */
    function hasRole(role) {
        const user = getCurrentUser();
        if (!user || !user.roles) return false;
        return user.roles.includes('ROLE_' + role.toUpperCase());
    }

    /**
     * Check if current user is platform admin
     */
    function isPlatformAdmin() {
        const user = getCurrentUser();
        if (!user || !user.roles) return false;
        return user.roles.includes('ROLE_PLATFORM_ADMIN');
    }

    /**
     * Check if current user is from platform tenant (tenantId === 0)
     */
    function isPlatformTenant() {
        const user = getCurrentUser();
        if (!user || user.tenantId === null || user.tenantId === undefined) return false;
        return user.tenantId === 0;
    }

    /**
     * Check if current user is admin
     */
    function isAdmin() {
        return hasRole('ADMIN');
    }

    /**
     * Save auth data after successful login/register
     */
    function saveAuthData(tokenResponse, userResponse) {
        localStorage.setItem(TOKEN_KEY, tokenResponse.accessToken);
        localStorage.setItem(REFRESH_TOKEN_KEY, tokenResponse.refreshToken);
        localStorage.setItem(USER_KEY, JSON.stringify(userResponse));
    }

    /**
     * Update user info
     */
    function updateUserInfo(user) {
        localStorage.setItem(USER_KEY, JSON.stringify(user));
    }

    /**
     * Update tokens
     */
    function updateTokens(accessToken, refreshToken) {
        localStorage.setItem(TOKEN_KEY, accessToken);
        if (refreshToken) {
            localStorage.setItem(REFRESH_TOKEN_KEY, refreshToken);
        }
    }

    /**
     * Clear auth data (logout)
     */
    function clearAuthData() {
        localStorage.removeItem(TOKEN_KEY);
        localStorage.removeItem(REFRESH_TOKEN_KEY);
        localStorage.removeItem(USER_KEY);
    }

    /**
     * Redirect based on authentication status
     */
    function checkAuthAndRedirect() {
        if (!isAuthenticated()) {
            if (window.location.pathname !== '/login.html') {
                window.location.href = '/login.html';
            }
            return false;
        }
        return true;
    }

    /**
     * Get user avatar text (first letter of username)
     */
    function getUserAvatar() {
        const user = getCurrentUser();
        if (!user || !user.username) return 'U';
        return user.username.charAt(0).toUpperCase();
    }

    /**
     * Get user display name
     */
    function getUserDisplayName() {
        const user = getCurrentUser();
        if (!user) return '-';
        return user.nickname || user.username || '-';
    }

    /**
     * Get user roles for display
     */
    function getUserRolesDisplay() {
        const user = getCurrentUser();
        if (!user || !user.roles) return [];
        return user.roles.map(r => r.replace('ROLE_', ''));
    }

    /**
     * Refresh access token using refresh token
     */
    async function refreshToken() {
        const refreshTokenValue = getRefreshToken();
        if (!refreshTokenValue) {
            throw new Error('No refresh token available');
        }

        try {
            const response = await fetch('/api/auth/refresh', {
                method: 'POST',
                headers: {
                    'Authorization': `Bearer ${refreshTokenValue}`
                }
            });

            if (!response.ok) {
                throw new Error('Token refresh failed');
            }

            const result = await response.json();
            if (result.code === 200) {
                updateTokens(result.data.accessToken, result.data.refreshToken);
                return result.data.accessToken;
            }
            throw new Error('Invalid refresh response');
        } catch (error) {
            clearAuthData();
            window.location.href = '/login.html';
            throw error;
        }
    }

    // Public API
    return {
        isAuthenticated,
        getAccessToken,
        getRefreshToken,
        getCurrentUser,
        hasRole,
        isPlatformAdmin,
        isPlatformTenant,
        isAdmin,
        saveAuthData,
        updateUserInfo,
        updateTokens,
        clearAuthData,
        checkAuthAndRedirect,
        getUserAvatar,
        getUserDisplayName,
        getUserRolesDisplay,
        refreshToken
    };
})();
