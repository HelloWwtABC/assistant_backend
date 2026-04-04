package com.wwt.assistant.dto.tool.request;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ServiceHealthQueryRequest {
    private String serviceName;
}
