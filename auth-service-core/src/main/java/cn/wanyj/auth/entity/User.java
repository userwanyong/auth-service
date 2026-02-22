package cn.wanyj.auth.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * User Entity - 用户实体
 * @author wanyj
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    private Long id;

    /**
     * 租户ID
     */
    private Long tenantId;

    private String username;

    private String password;

    private String email;

    private String phone;

    private String nickname;

    private String avatar;

    private Integer status;

    private Boolean emailVerified;

    private LocalDateTime lastLoginAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @Builder.Default
    private Set<Role> roles = new HashSet<>();
}
