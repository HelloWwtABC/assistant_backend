package com.wwt.assistant.dto.qa.response;

import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class QaSessionDetailResponse extends QaSessionItem {
    private List<QaMessageItem> messages;
}
