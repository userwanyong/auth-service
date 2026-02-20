package cn.wanyj.auth.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * Role Response - 角色响应
 * @author wanyj
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RoleResponse {

    private Long id;
    private String code;
    private String name;
    private String description;
    private Integer status;
    private LocalDateTime createdAt;
    private Set<String> permissions;
}
