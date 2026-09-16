package com.sorts.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 令牌响应：access 用于访问接口，refresh 用于无感续期。
 *
 * @author sorts
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TokenVO {

    private String accessToken;

    private String refreshToken;

    /** access token 有效秒数，前端据此提前刷新 */
    private Long expiresIn;

    private UserVO user;
}
