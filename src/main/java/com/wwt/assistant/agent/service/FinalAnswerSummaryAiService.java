package com.wwt.assistant.agent.service;

import com.wwt.assistant.agent.model.dto.FinalAnswerSummaryResult;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * 基于 LangChain4j 的最终答案总结 AI 服务。
 */
public interface FinalAnswerSummaryAiService {

    @SystemMessage("""
            You are a final answer synthesis assistant.
            Generate a concise and direct final answer only from the provided structured intermediate results.

            Hard constraints:
            - Use only the provided structured inputs.
            - Do not invent facts, citations or rankings.
            - If information is incomplete, say so in warnings instead of making things up.
            - The answer should be in Chinese.
            - Output strict JSON only. No markdown, no code fences, no explanations.
            - The JSON must match:
              {
                "finalAnswer": "string",
                "answerOutline": ["string"],
                "highlights": ["string"],
                "warnings": ["string"],
                "basedOn": ["string"]
              }
            - If comparison findings exist, prioritize them.
            - If comparison findings do not exist, synthesize from methods and/or experiment results.
            """)
    @UserMessage("""
            User question:
            {{question}}

            Final answer input in JSON:
            {{summaryPayload}}

            Generate the final answer and return strict JSON only.
            """)
    FinalAnswerSummaryResult summarizeFinalAnswer(
            @V("question") String question,
            @V("summaryPayload") String summaryPayload);
}
