package cn.wanyj.auth.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * User Response - 用户响应
 * @author wanyj
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserResponse {

    private Long id;
    private String username;
    private String email;
    private String phone;
    private String nickname;
    private String avatar;
    private Integer status;
    private Boolean emailVerified;
    private LocalDateTime lastLoginAt;
    private LocalDateTime createdAt;
    private Set<String> roles;
    private Set<String> permissions;
}
