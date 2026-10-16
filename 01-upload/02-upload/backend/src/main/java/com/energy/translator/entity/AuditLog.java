package com.energy.translator.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "audit_logs")
public class AuditLog {

    @Id
    private String id;

    private LocalDateTime timestamp;
    private String type;
    private String source;
    private String target;
    private String detail;
    private String result;
    private String userId;
    private String username;
    private LocalDateTime createdAt;

    public AuditLog() {
        this.timestamp = LocalDateTime.now();
        this.createdAt = LocalDateTime.now();
    }

    public AuditLog(String type, String source, String target, String detail, String result, String userId, String username) {
        this();
        this.type = type;
        this.source = source;
        this.target = target;
        this.detail = detail;
        this.result = result;
        this.userId = userId;
        this.username = username;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getTarget() { return target; }
    public void setTarget(String target) { this.target = target; }

    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }

    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
