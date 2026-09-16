package com.sorts.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 积分账户视图：余额 + 最近流水。
 *
 * @author sorts
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PointsVO {

    /** 当前光阴砂余额 */
    private Integer points;

    private List<PointsLogVO> logs;
}
