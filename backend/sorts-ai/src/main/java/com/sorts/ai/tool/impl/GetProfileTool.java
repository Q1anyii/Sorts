package com.sorts.ai.tool.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.sorts.ai.client.UserClient;
import com.sorts.ai.client.dto.UserDto;
import com.sorts.ai.tool.ToolLevel;
import com.sorts.ai.tool.support.AbstractAiTool;
import com.sorts.ai.tool.support.ToolJsonCodec;
import com.sorts.ai.tool.support.Results;
import com.sorts.ai.tool.support.Schemas;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 读取当前用户资料。
 *
 * <p>存在的主要理由不是「AI 需要知道用户叫什么」，而是「让 AI 说话有上下文」：
 * 知道用户昵称、装扮与光阴砂余额后，语气与建议才贴合个人，而不是通用的模板腔。</p>
 *
 * @author sorts
 */
@Component
public class GetProfileTool extends AbstractAiTool {

    private final UserClient userClient;

    public GetProfileTool(ToolJsonCodec jsonCodec, UserClient userClient) {
        super(jsonCodec);
        this.userClient = userClient;
    }

    @Override
    public String name() {
        return "getProfile";
    }

    @Override
    public String description() {
        return "获取当前登录用户的资料：昵称、邮箱、光阴砂余额与已启用的皮肤/头像框。"
                + "当用户询问「我的账号信息」「我还有多少光阴砂」，或需要以昵称称呼用户时调用。";
    }

    @Override
    public ToolLevel level() {
        return ToolLevel.READ;
    }

    @Override
    public Map<String, Object> parameters() {
        return Schemas.object(Schemas.props());
    }

    @Override
    public String execute(Long userId, JsonNode args) {
        UserDto user = Results.unwrap("查询用户资料", userClient.me(userId));
        Map<String, Object> data = result();
        data.put("nickname", user.getNickname() == null ? user.getUsername() : user.getNickname());
        data.put("username", user.getUsername());
        data.put("email", user.getEmail());
        data.put("points", user.getPoints());
        data.put("activeSkin", user.getActiveSkin());
        data.put("activeAvatarFrame", user.getActiveAvatarFrame());
        data.put("registeredAt", user.getCreatedAt());
        return json(data);
    }
}
