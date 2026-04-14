package com.wwt.assistant.agent.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 表示方法提取工具的结构化输出结果。
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class MethodExtractionResult {

    /** 按论文拆分的方法提取结果列表。 */
    private List<PaperMethodItem> items;

    /** 本轮方法提取的整体概括。 */
    private String overallSummary;
}
