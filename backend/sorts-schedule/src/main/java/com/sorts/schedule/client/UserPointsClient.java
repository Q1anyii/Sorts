package com.sorts.schedule.client;

import com.sorts.common.constant.AuthConstants;
import com.sorts.common.result.Result;
import com.sorts.schedule.dto.PointsChangeCommand;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

/**
 * 调用用户服务（跨模块走 OpenFeign，禁止跨库直连）。
 *
 * @author sorts
 */
@FeignClient(name = "sorts-user", path = "/api/v1/users")
public interface UserPointsClient {

    /**
     * 变更用户光阴砂。
     *
     * <p>用户身份通过 X-User-Id 头传递，与网关透传约定一致。</p>
     */
    @PostMapping("/points/change")
    Result<Integer> changePoints(@RequestHeader(AuthConstants.HEADER_USER_ID) Long userId,
                                @RequestBody PointsChangeCommand command);
}
