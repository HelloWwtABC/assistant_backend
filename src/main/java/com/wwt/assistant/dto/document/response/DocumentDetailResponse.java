package com.wwt.assistant.dto.document.response;

import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class DocumentDetailResponse extends DocumentListItem {
    private List<DocumentChunkItem> chunks;
}
