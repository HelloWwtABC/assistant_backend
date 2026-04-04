package com.wwt.assistant.dto.tool.response;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ServiceHealthResponse {
    private String serviceName;
    private String healthStatus;
    private Integer instanceCount;
    private Integer averageResponseTime;
    private String checkedAt;
    private String remark;
}
