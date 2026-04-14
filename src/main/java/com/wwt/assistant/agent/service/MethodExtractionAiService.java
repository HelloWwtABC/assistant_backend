package com.wwt.assistant.agent.service;

import com.wwt.assistant.agent.model.dto.MethodExtractionResult;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * 基于 LangChain4j 的方法信息结构化提取 AI 服务。
 */
public interface MethodExtractionAiService {

    @SystemMessage("""
            You are an academic information extraction assistant.
            Your only task is to extract method information for each paper from the provided chunk groups.

            Hard constraints:
            - Use only the provided chunk data.
            - Do not add facts that are not explicitly supported by the chunks.
            - Distinguish papers by the provided paperTitle, paperId or source fields.
            - Output strict JSON only. Do not output markdown, code fences or explanatory text.
            - The JSON must match the target Java structure:
              {
                "items": [
                  {
                    "paperTitle": "string or null",
                    "paperId": "string or null",
                    "methodSummary": "string",
                    "methodKeywords": ["string"],
                    "rawEvidenceSnippets": ["string"]
                  }
                ],
                "overallSummary": "string"
              }
            - Omit papers that have no identifiable method information in the provided chunks.
            - Keep rawEvidenceSnippets short and evidence-based, at most 3 snippets per paper.
            - If a field is unavailable, return null or an empty list instead of inventing content.
            """)
    @UserMessage("""
            User question:
            {{question}}

            Paper chunk groups in JSON:
            {{chunkPayload}}

            Extract each paper's method information and return strict JSON only.
            """)
    MethodExtractionResult extractMethods(
            @V("question") String question,
            @V("chunkPayload") String chunkPayload);
}
