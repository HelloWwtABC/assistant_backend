package com.wwt.assistant.agent.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 表示单篇论文在对比视角下的结构化结果。
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PaperComparisonItem {

    /** 论文标题。 */
    private String paperTitle;

    /** 论文标识。 */
    private String paperId;

    /** 方法侧亮点。 */
    private List<String> methodHighlights;

    /** 实验侧亮点。 */
    private List<String> experimentHighlights;

    /** 优势概括。 */
    private List<String> strengths;

    /** 局限概括。 */
    private List<String> limitations;

    /** 与其他论文相比的显著差异。 */
    private List<String> notableDifferences;
}
