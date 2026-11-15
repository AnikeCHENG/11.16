package com.example.campussecondhand.service;

import com.example.campussecondhand.entity.Product;
import com.example.campussecondhand.enums.ProductReviewStatus;
import com.example.campussecondhand.enums.ProductStatus;
import com.example.campussecondhand.exception.BadRequestException;
import com.example.campussecondhand.exception.ForbiddenException;
import com.example.campussecondhand.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 商品人工审核。
 *
 * <p>状态机（与 {@link ProductReviewStatus} / {@link ProductStatus} 的组合）：</p>
 * <pre>
 *   新发布         review=PENDING(0)  status=OFF_SHELF(0)
 *   审核通过       review=APPROVED(1) status=ON_SALE(1)
 *   审核拒绝       review=REJECTED(2) status=OFF_SHELF(0)
 *   卖家重新提交   review=PENDING(0)  status=OFF_SHELF(0)
 * </pre>
 *
 * <p>只在这里同时改写 {@code review_status} 与 {@code status}：分开写会出现
 * 「已通过但没上架」或「已拒绝却还在售」的中间态。方法整体加事务。</p>
 */
@Service
public class ProductReviewService {

    private static final Logger log = LoggerFactory.getLogger(ProductReviewService.class);

    /** 拒绝理由上限，与 products.review_reason VARCHAR(255) 对齐 */
    private static final int REASON_MAX_LENGTH = 255;

    private final ProductRepository productRepository;

    @Autowired
    public ProductReviewService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    /**
     * 审核单件商品。
     *
     * @param id         商品 ID
     * @param action     {@code approve} 或 {@code reject}
     * @param reason     拒绝理由；{@code reject} 时必填
     * @param operatorId 管理员 ID，仅用于留痕
     * @return 审核后的商品
     */
    @Transactional
    public Product review(Long id, String action, String reason, Long operatorId) {
        Product product = productRepository.selectById(id);
        if (product == null) {
            throw new BadRequestException("商品不存在");
        }
        ProductReviewStatus current = ProductReviewStatus.fromCode(product.getReviewStatus());
        if (current != ProductReviewStatus.PENDING && current != ProductReviewStatus.REJECTED) {
            throw new BadRequestException("该商品已审核通过，无需重复审核");
        }

        String normalizedAction = action == null ? "" : action.trim().toLowerCase();
        if ("approve".equals(normalizedAction)) {
            product.setReviewStatus(ProductReviewStatus.APPROVED.getCode());
            product.setStatus(ProductStatus.ON_SALE.getCode());
            product.setReviewReason(null);
        } else if ("reject".equals(normalizedAction)) {
            String reasonText = reason == null ? "" : reason.trim();
            if (reasonText.isEmpty()) {
                throw new BadRequestException("请填写拒绝理由");
            }
            if (reasonText.length() > REASON_MAX_LENGTH) {
                throw new BadRequestException("拒绝理由不能超过 " + REASON_MAX_LENGTH + " 个字符");
            }
            product.setReviewStatus(ProductReviewStatus.REJECTED.getCode());
            product.setStatus(ProductStatus.OFF_SHELF.getCode());
            product.setReviewReason(reasonText);
        } else {
            throw new BadRequestException("审核动作不合法，只能是 approve 或 reject");
        }

        product.setReviewedBy(operatorId);
        product.setReviewedTime(LocalDateTime.now());
        product.setUpdatedTime(LocalDateTime.now());
        productRepository.updateById(product);
        log.info("管理员[{}] 审核商品[{}]：{}", operatorId, id, normalizedAction);
        return product;
    }

    /**
     * 批量通过。只做「通过」，拒绝必须逐条填写理由，不做批量。
     *
     * @return {@code approvedCount} 成功数、{@code skippedCount} 跳过数
     */
    @Transactional
    public Map<String, Integer> batchApprove(List<Long> ids, Long operatorId) {
        int approved = 0;
        int skipped = 0;
        if (ids != null) {
            for (Long id : ids) {
                Product product = id == null ? null : productRepository.selectById(id);
                if (product == null) {
                    skipped++;
                    continue;
                }
                ProductReviewStatus current = ProductReviewStatus.fromCode(product.getReviewStatus());
                if (current != ProductReviewStatus.PENDING && current != ProductReviewStatus.REJECTED) {
                    skipped++;
                    continue;
                }
                product.setReviewStatus(ProductReviewStatus.APPROVED.getCode());
                product.setStatus(ProductStatus.ON_SALE.getCode());
                product.setReviewReason(null);
                product.setReviewedBy(operatorId);
                product.setReviewedTime(LocalDateTime.now());
                product.setUpdatedTime(LocalDateTime.now());
                productRepository.updateById(product);
                approved++;
            }
        }
        log.info("管理员[{}] 批量通过商品审核：成功 {} 件，跳过 {} 件", operatorId, approved, skipped);
        return Map.of("approvedCount", approved, "skippedCount", skipped);
    }

    /**
     * 卖家对被拒绝的商品重新提交审核。
     *
     * <p>只能操作自己的、且当前为「未通过」的商品；提交后回到待审核，
     * 清空上次拒绝理由与审核留痕。</p>
     */
    @Transactional
    public Product resubmit(Long id, Long userId) {
        Product product = productRepository.selectById(id);
        if (product == null) {
            throw new BadRequestException("商品不存在");
        }
        if (!Objects.equals(product.getUserId(), userId)) {
            throw new ForbiddenException("无权操作该商品");
        }
        if (ProductReviewStatus.fromCode(product.getReviewStatus()) != ProductReviewStatus.REJECTED) {
            throw new BadRequestException("只有审核未通过的商品可以重新提交");
        }
        product.setReviewStatus(ProductReviewStatus.PENDING.getCode());
        product.setStatus(ProductStatus.OFF_SHELF.getCode());
        product.setReviewReason(null);
        product.setReviewedBy(null);
        product.setReviewedTime(null);
        product.setUpdatedTime(LocalDateTime.now());
        productRepository.updateById(product);
        log.info("卖家[{}] 重新提交商品[{}]审核", userId, id);
        return product;
    }
}
