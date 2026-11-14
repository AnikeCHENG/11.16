package com.example.campussecondhand.dto;

import java.time.LocalDateTime;

/**
 * 信用流水的一行，供个人中心展示。
 *
 * <p>刻意不返回 {@code userId} 与 {@code bizId}：查询本身就限定在
 * 当前登录用户名下，返回内部 ID 只会扩大信息面。中文标签由
 * {@code CreditEvent.labelOf} 在服务层补上。</p>
 */
public class CreditLogView {
    private String eventType;
    private Integer delta;
    private String remark;
    private LocalDateTime createTime;

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public Integer getDelta() {
        return delta;
    }

    public void setDelta(Integer delta) {
        this.delta = delta;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }
}
