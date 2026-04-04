package com.wwt.assistant.dto.observability.response;

import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class RetrievalQualitySummaryResponse {
    private Double avgHitChunks;
    private Double avgCitationCount;
    private Double highQualityRate;
    private Double lowQualityRate;
    private List<QuestionCategoryItem> commonQuestionCategories;
}
