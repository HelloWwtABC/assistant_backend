package com.wwt.assistant.dto.session.request;

import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class BatchDeleteSessionsRequest {
    private List<String> sessionIds;
}
