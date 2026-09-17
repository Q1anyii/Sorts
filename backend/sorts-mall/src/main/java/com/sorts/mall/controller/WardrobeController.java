package com.sorts.mall.controller;

import com.sorts.common.constant.AuthConstants;
import com.sorts.common.result.Result;
import com.sorts.mall.dto.ActivateRequest;
import com.sorts.mall.dto.DeactivateRequest;
import com.sorts.mall.dto.WardrobeItemVO;
import com.sorts.mall.service.WardrobeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 装扮仓库接口（api-spec.json 的 装扮管理 分组）。
 *
 * <p>路径挂在 {@code /api/v1/users/wardrobe} 下（契约如此），但与用户服务同前缀，
 * 必须依赖网关把该路径优先路由到商城服务（见 gateway 配置中的顺序说明）。</p>
 *
 * @author sorts
 */
@RestController
@RequestMapping("/api/v1/users/wardrobe")
@RequiredArgsConstructor
public class WardrobeController {

    private final WardrobeService wardrobeService;

    /** 装扮仓库：已拥有的全部装扮 */
    @GetMapping
    public Result<List<WardrobeItemVO>> list(@RequestHeader(AuthConstants.HEADER_USER_ID) Long userId) {
        return Result.success(wardrobeService.list(userId));
    }

    /** 切换当前使用的装扮（同类型自动互斥） */
    @PutMapping("/active")
    public Result<Void> activate(@RequestHeader(AuthConstants.HEADER_USER_ID) Long userId,
                                 @Valid @RequestBody ActivateRequest request) {
        wardrobeService.activate(userId, request.getItemId(), request.getType());
        return Result.success();
    }

    /** 卸下装扮：恢复该类型默认外观（type 为空则全部卸下，含恢复默认皮肤） */
    @PutMapping("/deactivate")
    public Result<Void> deactivate(@RequestHeader(AuthConstants.HEADER_USER_ID) Long userId,
                                   @RequestBody(required = false) DeactivateRequest request) {
        wardrobeService.deactivate(userId, request == null ? null : request.getType());
        return Result.success();
    }
}
