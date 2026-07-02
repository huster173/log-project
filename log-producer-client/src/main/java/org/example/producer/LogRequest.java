package org.example.producer;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LogRequest {
    private long timestamp;
    private String ip;
    private String method;
    private String path;
    private int status;
}
