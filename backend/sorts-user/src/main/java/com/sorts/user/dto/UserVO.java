package com.sorts.user.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 对外返回的用户信息（不含密码等敏感字段）。
 *
 * @author sorts
 */
@Data
public class UserVO {

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

    /** 实体转 VO，避免把密码等字段带出去 */
    public static UserVO from(com.sorts.user.entity.User user) {
        if (user == null) {
            return null;
        }
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setEmail(user.getEmail());
        vo.setPhone(user.getPhone());
        vo.setAvatarUrl(user.getAvatarUrl());
        vo.setPoints(user.getPoints());
        vo.setActiveSkin(user.getActiveSkin());
        vo.setActiveAvatarFrame(user.getActiveAvatarFrame());
        vo.setCreatedAt(user.getCreatedAt());
        return vo;
    }
}
