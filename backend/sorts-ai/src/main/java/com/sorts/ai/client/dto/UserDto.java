package com.sorts.ai.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户信息契约（对应 sorts-user 的 UserVO，不含敏感字段）。
 *
 * @author sorts
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserDto {

    private Long id;

    private String username;

    private String nickname;

    private String email;

    private String phone;

    private String avatarUrl;

    /** 光阴砂余额 */
    private Integer points;

    private String activeSkin;

    private String activeAvatarFrame;

    private LocalDateTime createdAt;
}
