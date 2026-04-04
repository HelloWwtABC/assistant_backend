package com.wwt.assistant.dto.session.response;

import com.wwt.assistant.common.PageResponse;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class SessionPageResponse extends PageResponse<SessionRecordItem> {
    private List<OptionItem> knowledgeBaseOptions;
    private Integer todayActiveCount;
}
