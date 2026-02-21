package cn.wanyj.auth.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * UserRole Entity - 用户角色关联实体
 * @author wanyj
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserRole {

    private Long id;

    private Long userId;

    private Long roleId;

    private LocalDateTime createdAt;
}
