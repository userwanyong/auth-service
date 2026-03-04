package cn.wanyj.auth.dto.request;

import lombok.Data;

/**
 * @author wanyj
 */
@Data
public class UpdateUserRequest {

    private String username;

    private String password;

    private String email;

    private String phone;

    private String nickname;

    private String avatar;

    private Integer status;
}
