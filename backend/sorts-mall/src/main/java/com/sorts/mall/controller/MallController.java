package com.sorts.mall.controller;

import com.sorts.common.constant.AuthConstants;
import com.sorts.common.result.Result;
import com.sorts.mall.dto.MallItemDetailVO;
import com.sorts.mall.dto.MallItemPageVO;
import com.sorts.mall.service.MallService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 商城接口（api-spec.json 的 积分商城 分组）。
 *
 * @author sorts
 */
@RestController
@RequestMapping("/api/v1/mall")
@RequiredArgsConstructor
public class MallController {

    private final MallService mallService;

    /** 商品列表（附当前用户光阴砂余额） */
    @GetMapping("/items")
    public Result<MallItemPageVO> items(@RequestHeader(AuthConstants.HEADER_USER_ID) Long userId,
                                        @RequestParam(value = "type", required = false) String type,
                                        @RequestParam(value = "page", defaultValue = "1") int page,
                                        @RequestParam(value = "pageSize", defaultValue = "20") int pageSize) {
        return Result.success(mallService.list(userId, type, page, pageSize));
    }

    /** 商品详情（附是否已拥有与购买人次） */
    @GetMapping("/items/{id}")
    public Result<MallItemDetailVO> item(@RequestHeader(AuthConstants.HEADER_USER_ID) Long userId,
                                        @PathVariable("id") Long id) {
        return Result.success(mallService.detail(userId, id));
    }
}
