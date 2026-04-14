package com.wwt.assistant.agent.service;

import com.wwt.assistant.agent.model.dto.ExperimentExtractionResult;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * 基于 LangChain4j 的实验信息结构化提取 AI 服务。
 */
public interface ExperimentExtractionAiService {

    @SystemMessage("""
            You are an academic experiment extraction assistant.
            Extract experiment setup and experiment results for each paper only from the provided chunk groups.

            Hard constraints:
            - Use only the provided chunk data.
            - Do not invent facts, numbers, datasets, metrics or baselines.
            - Distinguish papers by paperTitle, paperId or source fields.
            - Output strict JSON only. No markdown, no code fences, no explanations.
            - The JSON must match:
              {
                "items": [
                  {
                    "paperTitle": "string or null",
                    "paperId": "string or null",
                    "experimentSetupSummary": "string or null",
                    "datasetNames": ["string"],
                    "metricNames": ["string"],
                    "baselineModels": ["string"],
                    "resultSummary": "string or null",
                    "keyFindings": ["string"],
                    "rawEvidenceSnippets": ["string"]
                  }
                ],
                "overallSummary": "string"
              }
            - Keep rawEvidenceSnippets short and evidence-based, at most 3 snippets per paper.
            - If a field is unavailable, return null or an empty list.
            - Omit papers that have no identifiable experiment setup or experiment result information.
            """)
    @UserMessage("""
            User question:
            {{question}}

            Paper chunk groups in JSON:
            {{chunkPayload}}

            Extract experiment setup and experiment results for each paper and return strict JSON only.
            """)
    ExperimentExtractionResult extractExperiments(
            @V("question") String question,
            @V("chunkPayload") String chunkPayload);
}
