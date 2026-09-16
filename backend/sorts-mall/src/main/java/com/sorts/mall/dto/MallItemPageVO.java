package com.sorts.mall.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 商品分页结果。
 *
 * <p>额外带 {@code userPoints}：商城列表页必须显示「我有多少光阴砂」，
 * 否则用户无法判断买得起什么，还要再打一次接口。</p>
 *
 * @author sorts
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MallItemPageVO {

    private List<MallItemVO> list;

    private long total;

    private long page;

    private long pageSize;

    /** 当前用户光阴砂余额；用户服务不可用时为 null（降级为不显示，而不是整个列表失败） */
    private Integer userPoints;
}
