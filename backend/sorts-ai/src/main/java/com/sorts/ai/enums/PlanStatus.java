package com.sorts.ai.enums;

/**
 * 规划状态。
 *
 * @author sorts
 */
public final class PlanStatus {

    /** 已生成、待用户决定 */
    public static final String DRAFT = "DRAFT";

    /** 已采纳（一个规划只允许采纳一次，避免重复创建日程） */
    public static final String ADOPTED = "ADOPTED";

    /** 已过期：规划有强时效，隔天再采纳会与用户实际安排冲突 */
    public static final String EXPIRED = "EXPIRED";

    private PlanStatus() {
    }
}
