package com.wwt.assistant.agent.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 表示单篇论文的方法提取结果。
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PaperMethodItem {

    /** 论文标题，优先用于标识论文。 */
    private String paperTitle;

    /** 论文标识，标题缺失时作为辅助标识。 */
    private String paperId;

    /** 对论文方法的简要总结。 */
    private String methodSummary;

    /** 方法相关关键词。 */
    private List<String> methodKeywords;

    /** 原始证据片段，便于后续追踪和比对。 */
    private List<String> rawEvidenceSnippets;
}
