package com.sorts.ai.tool;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

/**
 * 可被模型调用的工具。
 *
 * <p>新增一个工具只需实现本接口并注册为 Spring Bean，{@link ToolRegistry} 会自动收集——
 * 不需要改动任何调度代码（开闭原则的实用版本）。</p>
 *
 * @author sorts
 */
public interface AiTool {

    /** 工具名，模型据此发起调用；须全局唯一、蛇形或驼峰稳定不变 */
    String name();

    /** 给模型看的用途说明，写清楚「什么时候用」比「能做什么」更能提升调用准确率 */
    String description();

    /** 读写级别，决定是否受写开关约束 */
    ToolLevel level();

    /** JSON Schema 形式的参数声明 */
    Map<String, Object> parameters();

    /**
     * 执行工具。
     *
     * @param userId 当前登录用户，所有下游调用都以该身份进行
     * @param args   模型给出的参数，已解析为 JSON 树（可能为空对象，不会为 null）
     * @return 回灌给模型的观察结果文本（建议 JSON 字符串，便于模型准确引用）
     */
    String execute(Long userId, JsonNode args);
}
