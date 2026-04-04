package com.wwt.assistant.dto.document.response;

import com.wwt.assistant.common.PageResponse;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class DocumentPageResponse extends PageResponse<DocumentListItem> {
    private List<OptionItem> knowledgeBaseOptions;
}
