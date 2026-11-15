package com.example.campussecondhand.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("messages")
public class Message {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("sender_id")
    private Long senderId;

    @TableField("receiver_id")
    private Long receiverId;

    @TableField("product_id")
    private Long productId;

    @TableField("content")
    private String content;

    /**
     * 消息类型：0=文本（默认），1=图片。
     *
     * <p>图片消息的 {@link #content} 复用为图片路径（形如
     * {@code /uploads/2026/11/<32位hex>.png}），由上传接口产生；
     * 配文放 {@link #caption}。存量消息经迁移自动归为文本。</p>
     */
    @TableField("msg_type")
    private Integer msgType = 0;

    /** 图片消息的配文（选填，≤200 字；文本消息恒为 null） */
    @TableField("caption")
    private String caption;

    @TableField("is_read")
    private Integer isRead = 0;

    @TableField("created_time")
    private LocalDateTime createdTime;

    @TableField(exist = false)
    private User sender;

    @TableField(exist = false)
    private User receiver;

    @TableField(exist = false)
    private Product product;

    public Message() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getSenderId() { return senderId; }
    public void setSenderId(Long senderId) { this.senderId = senderId; }

    public Long getReceiverId() { return receiverId; }
    public void setReceiverId(Long receiverId) { this.receiverId = receiverId; }

    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public Integer getMsgType() { return msgType; }
    public void setMsgType(Integer msgType) { this.msgType = msgType; }

    public String getCaption() { return caption; }
    public void setCaption(String caption) { this.caption = caption; }

    public Integer getIsRead() { return isRead; }
    public void setIsRead(Integer isRead) { this.isRead = isRead; }

    public LocalDateTime getCreatedTime() { return createdTime; }
    public void setCreatedTime(LocalDateTime createdTime) { this.createdTime = createdTime; }

    public User getSender() { return sender; }
    public void setSender(User sender) { this.sender = sender; }

    public User getReceiver() { return receiver; }
    public void setReceiver(User receiver) { this.receiver = receiver; }

    public Product getProduct() { return product; }
    public void setProduct(Product product) { this.product = product; }
}
