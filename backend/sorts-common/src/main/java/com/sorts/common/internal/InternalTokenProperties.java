package com.sorts.common.internal;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 服务间调用凭证配置（前缀 sorts.internal）。
 *
 * <p>生产环境务必通过环境变量 {@code INTERNAL_TOKEN} 注入，各服务保持一致。</p>
 *
 * @author sorts
 */
@Data
@ConfigurationProperties(prefix = "sorts.internal")
public class InternalTokenProperties {

    /** 是否启用内部接口校验；关闭后 @InternalApi 接口将直接放行（仅限本地排障） */
    private boolean enabled = true;

    /** 服务间共享凭证 */
    private String token = "sorts-internal-dev-token";

    /**
     * Feign 出站时允许携带内部凭证的路径前缀。
     *
     * <p>刻意做成白名单而不是「全部请求都带」：一旦某个服务被注入恶意的
     * 下游地址，凭证也不会被顺手送出去。</p>
     */
    private List<String> paths = new ArrayList<>(List.of(
            "/internal/",
            "/api/v1/users/points/change"));
}
