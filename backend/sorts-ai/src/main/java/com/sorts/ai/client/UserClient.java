package com.sorts.ai.client;

import com.sorts.ai.client.dto.PointsDto;
import com.sorts.ai.client.dto.UserDto;
import com.sorts.common.constant.AuthConstants;
import com.sorts.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 用户服务（sorts-user）内部调用客户端，供 AI 读取资料与光阴砂余额。
 *
 * @author sorts
 */
@FeignClient(name = "sorts-user", contextId = "aiUserClient", path = "/api/v1")
public interface UserClient {

    @GetMapping("/users/me")
    Result<UserDto> me(@RequestHeader(AuthConstants.HEADER_USER_ID) Long userId);

    @GetMapping("/users/points")
    Result<PointsDto> points(@RequestHeader(AuthConstants.HEADER_USER_ID) Long userId,
                             @RequestParam("limit") int limit);
}
