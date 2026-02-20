package cn.wanyj.auth.security;

import cn.wanyj.auth.entity.Permission;
import cn.wanyj.auth.entity.Role;
import cn.wanyj.auth.entity.User;
import cn.wanyj.auth.mapper.UserMapper;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * User Details Service Implementation - 用户详情服务实现
 * Spring Security 用于加载用户信息的核心接口
 * @author wanyj
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserMapper userMapper;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        log.info("Loading user by username: {}", username);

        // Load user with roles and permissions from database
        User user = userMapper.findByUsernameOrEmailWithRolesAndPermissions(username);

        if (user == null) {
            log.error("User not found: {}", username);
            throw new UsernameNotFoundException("User not found: " + username);
        }

        if (user.getStatus() == 0) {
            log.error("User is disabled: {}", username);
            throw new UsernameNotFoundException("User is disabled: " + username);
        }

        log.info("User loaded successfully: {}", username);

        return new CustomUserDetails(
                user.getId(),
                user.getUsername(),
                user.getPassword(),
                user.getStatus() == 1,
                getAuthorities(user)
        );
    }

    /**
     * Build authorities from user's roles and permissions
     * 从用户的角色和权限构建授权信息
     */
    private Collection<? extends GrantedAuthority> getAuthorities(User user) {
        List<GrantedAuthority> authorities = new ArrayList<>();

        // Add role authorities (ROLE_ prefix is automatically added by Spring Security)
        Set<Role> roles = user.getRoles();
        if (roles != null) {
            for (Role role : roles) {
                authorities.add(new SimpleGrantedAuthority("ROLE_" + role.getCode()));

                // Add permission authorities
                Set<Permission> permissions = role.getPermissions();
                if (permissions != null) {
                    for (Permission permission : permissions) {
                        authorities.add(new SimpleGrantedAuthority(permission.getCode()));
                    }
                }
            }
        }

        log.debug("Authorities for user {}: {}", user.getUsername(), authorities);

        return authorities;
    }

    /**
     * Custom UserDetails implementation
     * 自定义 UserDetails 实现
     */
    public static class CustomUserDetails implements UserDetails {
        @Getter
        private final Long userId;
        private final String username;
        private final String password;
        private final boolean enabled;
        private final Collection<? extends GrantedAuthority> authorities;

        public CustomUserDetails(Long userId, String username, String password,
                                boolean enabled, Collection<? extends GrantedAuthority> authorities) {
            this.userId = userId;
            this.username = username;
            this.password = password;
            this.enabled = enabled;
            this.authorities = authorities;
        }

        @Override
        public Collection<? extends GrantedAuthority> getAuthorities() {
            return authorities;
        }

        @Override
        public String getPassword() {
            return password;
        }

        @Override
        public String getUsername() {
            return username;
        }

        @Override
        public boolean isAccountNonExpired() {
            return true;
        }

        @Override
        public boolean isAccountNonLocked() {
            return true;
        }

        @Override
        public boolean isCredentialsNonExpired() {
            return true;
        }

        @Override
        public boolean isEnabled() {
            return enabled;
        }
    }
}
