package com.wwt.assistant.agent.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 表示实验信息提取工具的结构化输出结果。
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExperimentExtractionResult {

    /** 按论文拆分的实验信息列表。 */
    private List<PaperExperimentItem> items;

    /** 本轮实验提取的整体概括。 */
    private String overallSummary;
}
