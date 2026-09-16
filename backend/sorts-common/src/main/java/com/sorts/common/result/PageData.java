package com.sorts.common.result;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 分页数据结构（作为 {@link Result} 的 data 载荷）。
 *
 * <pre>
 * { "code": 0, "message": "success",
 *   "data": { "list": [...], "total": 100, "page": 1, "pageSize": 20 } }
 * </pre>
 *
 * @author sorts
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageData<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 当前页数据 */
    private List<T> list;

    /** 总记录数 */
    private long total;

    /** 当前页码，从 1 开始 */
    private long page;

    /** 每页条数 */
    private long pageSize;

    public static <T> PageData<T> of(List<T> list, long total, long page, long pageSize) {
        return new PageData<>(list, total, page, pageSize);
    }
}
