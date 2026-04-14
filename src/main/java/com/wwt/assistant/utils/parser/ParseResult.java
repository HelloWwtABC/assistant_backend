package com.wwt.assistant.utils.parser;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class ParseResult {

    /**
     * 是否解析成功
     */
    private boolean success;


    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 文件名
     */
    private String fileName;

    /**
     * 文件类型
     */
    private String fileType;

    /**
     * 解析后的全文内容
     */
    private String content;

    /**
     * 全文长度
     */
    private Integer contentLength;

    /**
     * 页数/页码总数
     */
    private Integer pageCount;

    /**
     * 结构化片段
     */
    private List<ParseSection> sections;

    /**
     * 附加元数据
     */
    private Map<String, Object> metadata;
}
