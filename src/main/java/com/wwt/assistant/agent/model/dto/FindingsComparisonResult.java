package com.wwt.assistant.agent.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 表示方法与实验发现对比后的结构化结果。
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class FindingsComparisonResult {

    /** 分论文的对比结果。 */
    private List<PaperComparisonItem> items;

    /** 论文间共性。 */
    private List<String> commonPatterns;

    /** 方法差异。 */
    private List<String> methodDifferences;

    /** 实验差异。 */
    private List<String> experimentDifferences;

    /** 对比依据。 */
    private List<String> comparisonBasis;

    /** 整体对比概括。 */
    private String overallSummary;
}
