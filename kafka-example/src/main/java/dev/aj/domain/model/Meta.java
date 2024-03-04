package dev.aj.domain.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class Meta {
    private String uri;
    private String request_id;
    private String id;
    private String dt;
    private String domain;
    private String stream;
    private String topic;
    private int partition;
    private long offset;
}
