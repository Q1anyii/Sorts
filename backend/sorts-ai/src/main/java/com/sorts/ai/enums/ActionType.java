package com.sorts.ai.enums;

/**
 * 建议操作类型（对应 api-spec 的 suggestedActions[].type）。
 *
 * @author sorts
 */
public final class ActionType {

    public static final String CREATE_SCHEDULE = "CREATE_SCHEDULE";

    public static final String VIEW_STATS = "VIEW_STATS";

    public static final String GENERATE_SUMMARY = "GENERATE_SUMMARY";

    private ActionType() {
    }
}
