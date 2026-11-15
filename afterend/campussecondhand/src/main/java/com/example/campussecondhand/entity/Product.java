package com.example.campussecondhand.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.example.campussecondhand.enums.ConditionLevel;
import com.example.campussecondhand.enums.ProductReviewStatus;
import com.example.campussecondhand.enums.ProductStatus;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("products")
public class Product {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("title")
    private String title;

    @TableField("description")
    private String description;

    @TableField("price")
    private BigDecimal price;

    @TableField("original_price")
    private BigDecimal originalPrice;

    @TableField("category")
    private String category;

    @TableField("condition_level")
    private Integer conditionLevel;

    @TableField("flaw_description")
    private String flawDescription;

    @TableField("images")
    private String images;

    @TableField("view_count")
    private Integer viewCount = 0;

    /**
     * 商品状态，取值语义见 {@link com.example.campussecondhand.enums.ProductStatus}。
     * 禁止在业务代码中直接使用字面量 0/1/2，请统一引用该枚举。
     */
    @TableField("status")
    private Integer status = ProductStatus.ON_SALE.getCode();

    /**
     * 人工审核状态，取值语义见 {@link ProductReviewStatus}。
     *
     * <p>默认值必须与数据库列默认值一致（1=已通过）：迁移前的存量商品全部视为已通过，
     * 否则上线当天所有商品会被当成待审核而从大厅消失。新建入口
     * {@code ProductService#create} 会显式改写为「待审核」。</p>
     */
    @TableField("review_status")
    private Integer reviewStatus = ProductReviewStatus.APPROVED.getCode();

    /**
     * 拒绝理由。审核通过或卖家重新提交后要清空，因此必须允许写入 null——
     * MyBatis-Plus 默认的 {@code NOT_NULL} 策略会忽略 null 字段，导致旧理由残留。
     */
    @TableField(value = "review_reason", updateStrategy = FieldStrategy.IGNORED)
    private String reviewReason;

    /** 审核人（管理员）ID。重新提交后清空，用 IGNORED 策略保证 null 能落库 */
    @TableField(value = "reviewed_by", updateStrategy = FieldStrategy.IGNORED)
    private Long reviewedBy;

    /** 审核时间。重新提交后清空，用 IGNORED 策略保证 null 能落库 */
    @TableField(value = "reviewed_time", updateStrategy = FieldStrategy.IGNORED)
    private LocalDateTime reviewedTime;

    @TableField("created_time")
    private LocalDateTime createdTime;

    @TableField("updated_time")
    private LocalDateTime updatedTime;

    /**
     * 成交时间。
     *
     * <p>MyBatis-Plus 默认忽略 null 字段（NOT_NULL 策略），订单取消时
     * {@code setSoldTime(null)} 不会落库，会导致商品已恢复在售却仍残留成交时间。
     * 因此这里必须显式改为 IGNORED，使 null 也能写入。</p>
     */
    @TableField(value = "sold_time", updateStrategy = FieldStrategy.IGNORED)
    private LocalDateTime soldTime;

    @TableField(exist = false)
    private User seller;

    public Product() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }

    public BigDecimal getOriginalPrice() { return originalPrice; }
    public void setOriginalPrice(BigDecimal originalPrice) { this.originalPrice = originalPrice; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public Integer getConditionLevel() { return conditionLevel; }
    public void setConditionLevel(Integer conditionLevel) { this.conditionLevel = conditionLevel; }

    public String getFlawDescription() { return flawDescription; }
    public void setFlawDescription(String flawDescription) { this.flawDescription = flawDescription; }

    /**
     * 成色的中文标签，供不需要引入枚举的调用方直接展示。
     *
     * <p>刻意不在实体里存标签字符串：那是冗余数据，改枚举忘了同步就会自相矛盾。
     * 需要展示时调 {@link ConditionLevel#fromCode(Integer)} 现算。</p>
     */
    public String getConditionLabel() {
        ConditionLevel level = ConditionLevel.fromCode(conditionLevel);
        return level != null ? level.getLabel() : "未标注成色";
    }

    /** 是否声明无瑕疵：瑕疵说明为空即视为卖家承诺无明显瑕疵 */
    public boolean isFlawFree() {
        return flawDescription == null || flawDescription.isBlank();
    }

    public String getImages() { return images; }
    public void setImages(String images) { this.images = images; }

    public Integer getViewCount() { return viewCount; }
    public void setViewCount(Integer viewCount) { this.viewCount = viewCount; }

    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }

    public Integer getReviewStatus() { return reviewStatus; }
    public void setReviewStatus(Integer reviewStatus) { this.reviewStatus = reviewStatus; }

    public String getReviewReason() { return reviewReason; }
    public void setReviewReason(String reviewReason) { this.reviewReason = reviewReason; }

    public Long getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(Long reviewedBy) { this.reviewedBy = reviewedBy; }

    public LocalDateTime getReviewedTime() { return reviewedTime; }
    public void setReviewedTime(LocalDateTime reviewedTime) { this.reviewedTime = reviewedTime; }

    /** 审核状态中文标签，供管理端/用户端直接展示 */
    public String getReviewStatusLabel() {
        ProductReviewStatus review = ProductReviewStatus.fromCode(reviewStatus);
        return review != null ? review.getLabel() : "未知";
    }

    public LocalDateTime getCreatedTime() { return createdTime; }
    public void setCreatedTime(LocalDateTime createdTime) { this.createdTime = createdTime; }

    public LocalDateTime getUpdatedTime() { return updatedTime; }
    public void setUpdatedTime(LocalDateTime updatedTime) { this.updatedTime = updatedTime; }

    public LocalDateTime getSoldTime() { return soldTime; }
    public void setSoldTime(LocalDateTime soldTime) { this.soldTime = soldTime; }

    @JsonIgnore
    public User getSeller() { return seller; }
    public void setSeller(User seller) { this.seller = seller; }
}
