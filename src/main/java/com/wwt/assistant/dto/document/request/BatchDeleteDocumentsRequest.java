package com.wwt.assistant.dto.document.request;

import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class BatchDeleteDocumentsRequest {
    private List<String> documentIds;
}
