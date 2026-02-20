package cn.wanyj.auth.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Permission Response - 权限响应
 * @author wanyj
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PermissionResponse {

    private Long id;
    private String code;
    private String name;
    private String resource;
    private String action;
    private String description;
    private LocalDateTime createdAt;
}
