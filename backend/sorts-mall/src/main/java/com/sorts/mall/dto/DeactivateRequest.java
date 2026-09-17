package com.sorts.mall.dto;

import lombok.Data;

/**
 * 卸下装扮请求：type 为空表示卸下全部类型（含恢复默认皮肤）。
 *
 * @author sorts
 */
@Data
public class DeactivateRequest {

    /** 要卸下的类型：SKIN / AVATAR / BADGE；空则全部卸下 */
    private String type;
}
