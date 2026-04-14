package com.wwt.assistant.agent.service;

import com.wwt.assistant.agent.model.dto.FindingsComparisonResult;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * 基于 LangChain4j 的发现对比 AI 服务。
 */
public interface FindingsComparisonAiService {

    @SystemMessage("""
            You are an academic comparison assistant.
            Compare papers only from the provided structured method extraction results and experiment extraction results.

            Hard constraints:
            - Use only the provided structured inputs.
            - Do not invent content, claims or rankings not supported by the inputs.
            - Output strict JSON only. No markdown, no code fences, no explanations.
            - The JSON must match:
              {
                "items": [
                  {
                    "paperTitle": "string or null",
                    "paperId": "string or null",
                    "methodHighlights": ["string"],
                    "experimentHighlights": ["string"],
                    "strengths": ["string"],
                    "limitations": ["string"],
                    "notableDifferences": ["string"]
                  }
                ],
                "commonPatterns": ["string"],
                "methodDifferences": ["string"],
                "experimentDifferences": ["string"],
                "comparisonBasis": ["string"],
                "overallSummary": "string"
              }
            - If method information is missing, compare only on experiment results.
            - If experiment information is missing, compare only on method information.
            - Keep each list concise and evidence-based.
            """)
    @UserMessage("""
            User question:
            {{question}}

            Comparison input in JSON:
            {{comparisonPayload}}

            Compare the findings and return strict JSON only.
            """)
    FindingsComparisonResult compareFindings(
            @V("question") String question,
            @V("comparisonPayload") String comparisonPayload);
}
