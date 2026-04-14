package com.wwt.assistant.agent.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 表示单篇论文的实验信息提取结果。
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PaperExperimentItem {

    /** 论文标题。 */
    private String paperTitle;

    /** 论文标识。 */
    private String paperId;

    /** 实验设置概括。 */
    private String experimentSetupSummary;

    /** 数据集名称列表。 */
    private List<String> datasetNames;

    /** 评价指标列表。 */
    private List<String> metricNames;

    /** 对比基线模型列表。 */
    private List<String> baselineModels;

    /** 实验结果概括。 */
    private String resultSummary;

    /** 关键实验发现。 */
    private List<String> keyFindings;

    /** 原始证据片段。 */
    private List<String> rawEvidenceSnippets;
}
