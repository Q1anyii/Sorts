package com.sorts.mall.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * 用户积分账户（sorts-user 出参副本，只保留本服务需要的字段）。
 *
 * @author sorts
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserPointsDto {

    /** 当前光阴砂余额 */
    private Integer points;
}
