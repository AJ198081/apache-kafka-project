package dev.aj.domain.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class WikiModel {
    @JsonProperty(value = "$schema")
    private String schema;
    private Meta meta;
    private long id;
    private String type;
    private int namespace;
    private String title;
    private String title_url;
    private String comment;
    private long timestamp;
    private String user;
    private boolean bot;
    private String notify_url;
    private boolean minor;
    private boolean patrolled;
    private Length length;
    private Revision revision;
    private String server_url;
    private String server_name;
    private String server_script_path;
    private String wiki;
    private String parsedcomment;
}
