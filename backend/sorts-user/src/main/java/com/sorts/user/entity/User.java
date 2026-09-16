package com.sorts.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户实体（对应 sorts_user 库 t_user 表）。
 *
 * @author sorts
 */
@Data
@TableName("t_user")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户名，唯一 */
    private String username;

    /** BCrypt 密文，永不明文存储 */
    private String password;

    private String nickname;

    private String email;

    private String phone;

    private String avatarUrl;

    /** 光阴砂余额 */
    private Integer points;

    /** 当前生效皮肤（主题：织锦） */
    private String activeSkin;

    /** 当前生效头像挂件 */
    private String activeAvatarFrame;

    /** 状态：1 正常 / 0 禁用 */
    private Integer status;

    /** 软删除标记，配合 MyBatis-Plus 逻辑删除 */
    private Integer deleted;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
