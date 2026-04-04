package com.wwt.assistant.common;

import java.util.Collections;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 通用分页返回结构。
 *
 * @param <T> 列表项类型
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageResponse<T> {

    /**
     * 当前页数据。
     */
    private List<T> list;

    /**
     * 总记录数。
     */
    private long total;

    /**
     * 当前页码。
     */
    private long page;

    /**
     * 每页条数。
     */
    private long pageSize;

    /**
     * 构建分页结果。
     */
    public static <T> PageResponse<T> of(List<T> list, long total, long page, long pageSize) {
        return new PageResponse<>(list, total, page, pageSize);
    }

    /**
     * 构建空分页结果。
     */
    public static <T> PageResponse<T> empty(long page, long pageSize) {
        return new PageResponse<>(Collections.emptyList(), 0L, page, pageSize);
    }
}
