package com.sorts.mall.client;

import com.sorts.common.constant.AuthConstants;
import com.sorts.common.result.Result;
import com.sorts.mall.client.dto.PointsChangeCommand;
import com.sorts.mall.client.dto.UserPointsDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 调用用户服务（跨模块走 OpenFeign，禁止跨库直连）。
 *
 * @author sorts
 */
@FeignClient(name = "sorts-user", contextId = "mallUserClient", path = "/api/v1/users")
public interface UserClient {

    /**
     * 变更用户光阴砂。
     *
     * <p>路径命中内部凭证白名单，{@code X-Internal-Token} 由拦截器自动附加。</p>
     *
     * @return 变动后的余额
     */
    @PostMapping("/points/change")
    Result<Integer> changePoints(@RequestHeader(AuthConstants.HEADER_USER_ID) Long userId,
                                @RequestBody PointsChangeCommand command);

    /** 查询积分账户（余额 + 最近流水） */
    @GetMapping("/points")
    Result<UserPointsDto> points(@RequestHeader(AuthConstants.HEADER_USER_ID) Long userId,
                                 @RequestParam("limit") int limit);
}
