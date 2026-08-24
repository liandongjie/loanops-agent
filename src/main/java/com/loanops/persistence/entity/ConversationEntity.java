package com.loanops.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("conversation")
public class ConversationEntity {

    @TableId(type = IdType.INPUT)
    private String conversationId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long version;
    private Integer lastMessageSequence;

    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
    public Integer getLastMessageSequence() { return lastMessageSequence; }
    public void setLastMessageSequence(Integer lastMessageSequence) { this.lastMessageSequence = lastMessageSequence; }
}
