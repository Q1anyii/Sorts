package com.sorts.user.dto;

import jakarta.validation.constraints.Email;
import lombok.Data;

/**
 * 更新用户信息请求（字段均为可选，仅更新非空项）。
 *
 * @author sorts
 */
@Data
public class UpdateUserRequest {

    private String nickname;

    @Email(message = "邮箱格式不正确")
    private String email;

    private String phone;

    private String avatarUrl;
}
