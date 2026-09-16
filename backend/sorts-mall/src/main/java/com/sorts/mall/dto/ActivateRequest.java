package com.sorts.mall.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 切换装扮请求（与 api-spec.json 的 /users/wardrobe/active 入参一致）。
 *
 * @author sorts
 */
@Data
public class ActivateRequest {

    @NotNull(message = "装扮 ID 不能为空")
    private Long itemId;

    /**
     * 同类型切换时，之前生效的将自动取消。
     *
     * <p>由服务端依据仓库条目的类型判定，请求里的 type 仅作契约兼容与校验之用，
     * 不能作为「取消哪一类」的依据——否则前端传错类型就能让多个同类装扮同时生效。</p>
     */
    private String type;
}
