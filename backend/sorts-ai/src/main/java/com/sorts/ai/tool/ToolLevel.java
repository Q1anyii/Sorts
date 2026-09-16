package com.sorts.ai.tool;

/**
 * 工具的读写级别。
 *
 * <p>这是工具集权限模型的基础：读工具永远开放；写工具需要「双钥匙」放行——
 * 服务端配置 {@code sorts.ai.tool.allow-write=true} 且本次请求带 {@code allowWrite=true}。
 * 二者缺一，写工具既不会下发给模型，也不会被执行。</p>
 *
 * @author sorts
 */
public enum ToolLevel {

    /** 只读：查询日程 / 统计 / 积分 / 资料 */
    READ,

    /** 写入：创建日程。会造成不可逆的数据变更，需双重确认 */
    WRITE;

    public boolean isWrite() {
        return this == WRITE;
    }
}
