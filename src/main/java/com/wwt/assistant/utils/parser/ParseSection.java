package com.wwt.assistant.utils.parser;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ParseSection {

    /**
     * 页码
     */
    private Integer pageNo;

    /**
     * 小节标题
     */
    private String sectionTitle;

    /**
     * 内容
     */
    private String text;
}
