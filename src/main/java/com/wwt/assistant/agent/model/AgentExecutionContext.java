package com.wwt.assistant.agent.model;

import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 表示Agent执行过程中的上下文数据容器。
 */
@Data
@NoArgsConstructor
public class AgentExecutionContext {

    /** 用户原始问题。 */
    private String originalQuestion;

    /** 改写后的问题。 */
    private String rewrittenQuestion;

    /** 可用知识库ID列表。 */
    private List<Long> knowledgeBaseIds;

    /** 检索到的分片结果。 */
    private List<Object> retrievedChunks;

    /** 中间过程结果。 */
    private Map<String, Object> intermediateResults;

    /** 每个步骤的执行结果。 */
    private List<AgentStepResult> stepResults;

    /** 当前已执行的检索轮数。 */
    private Integer retrievalRoundCount;

    /** 当前步骤编号。 */
    private Integer currentStepNo;

    /** 执行日志。 */
    private List<String> executionLogs;

    /** 最终草稿答案。 */
    private String finalDraftAnswer;

    /** 执行过程中的错误信息。 */
    private List<String> errorMessages;
}
