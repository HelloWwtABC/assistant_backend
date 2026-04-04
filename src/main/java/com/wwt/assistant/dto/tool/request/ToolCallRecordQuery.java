package com.wwt.assistant.dto.tool.request;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ToolCallRecordQuery {
    private Long page;
    private Long pageSize;
}
