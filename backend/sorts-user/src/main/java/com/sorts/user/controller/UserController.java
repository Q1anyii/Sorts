package com.sorts.user.controller;

import com.sorts.common.constant.AuthConstants;
import com.sorts.common.exception.BizException;
import com.sorts.common.internal.InternalApi;
import com.sorts.common.result.ErrorCode;
import com.sorts.common.result.Result;
import com.sorts.user.dto.PointsChangeRequest;
import com.sorts.user.dto.PointsVO;
import com.sorts.user.dto.UpdateUserRequest;
import com.sorts.user.dto.UserVO;
import com.sorts.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户信息与积分账户接口。
 *
 * <p>用户身份来自网关透传的 X-User-Id 请求头（外部无法伪造，网关会先剥离再写入）。</p>
 *
 * @author sorts
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public Result<UserVO> me(@RequestHeader(AuthConstants.HEADER_USER_ID) Long userId) {
        return Result.success(userService.getProfile(userId));
    }

    @PutMapping("/me")
    public Result<UserVO> updateMe(@RequestHeader(AuthConstants.HEADER_USER_ID) Long userId,
                                   @Valid @RequestBody UpdateUserRequest request) {
        return Result.success(userService.updateProfile(userId, request));
    }

    /** 积分余额 + 最近流水 */
    @GetMapping("/points")
    public Result<PointsVO> points(@RequestHeader(AuthConstants.HEADER_USER_ID) Long userId,
                                   @RequestParam(value = "limit", defaultValue = "20") int limit) {
        return Result.success(userService.getPoints(userId, limit));
    }

    /**
     * 积分变动（内部服务通过 Feign 调用）。
     *
     * <p>标注 {@link InternalApi}：除网关鉴权外，还要求携带服务间凭证
     * {@code X-Internal-Token}，外部请求无法直接触达（网关会剥离伪造凭证）。</p>
     */
    @InternalApi("积分变动：供日程/AI/商城等服务扣发光阴砂")
    @PostMapping("/points/change")
    public Result<Integer> changePoints(@RequestHeader(AuthConstants.HEADER_USER_ID) Long userId,
                                        @Valid @RequestBody PointsChangeRequest request) {
        // 内部调用场景：允许通过 X-Target-User-Id 指定目标用户，缺省则为调用者本人
        return Result.success(userService.changePoints(userId, request));
    }

    /** 参数兜底：用户头缺失时给出明确提示 */
    @GetMapping("/ping")
    public Result<String> ping(@RequestHeader(value = AuthConstants.HEADER_USER_ID, required = false) Long userId) {
        if (userId == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "缺少用户身份信息");
        }
        return Result.success("pong:" + userId);
    }
}
