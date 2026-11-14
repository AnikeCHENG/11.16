package com.example.campussecondhand.service;

import com.example.campussecondhand.dto.CreditLogView;
import com.example.campussecondhand.enums.CreditEvent;
import com.example.campussecondhand.enums.CreditLevel;
import com.example.campussecondhand.repository.CreditRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 信用分服务。
 *
 * <p>规则：初始 100，范围 0~150；认证 +10、成交 +2、好评 +1、差评 -3、
 * 超时未支付 -5、举报成立 -15。分值定义见 {@link CreditEvent}，
 * 等级划分见 {@link CreditLevel}。</p>
 *
 * <p><b>本类的所有方法都不向外抛异常：</b>信用分是附属能力，记分失败
 * （典型是迁移脚本还没执行，表/字段不存在）不能拖垮下单、支付、
 * 确认收货这些主流程——尤其 {@code OrderService#payOrder} 明确要求
 * "业务失败不抛异常"，一旦这里冒泡出异常，超时取消与商品释放会
 * 连同事务一起回滚，商品被永久锁死。查询失败时按"未受限"处理
 * （fail-open），宁可漏罚也不能误伤正常用户。</p>
 *
 * <p><b>幂等：</b>先写流水（靠 {@code uk_user_event_biz} 唯一键），
 * 写成功才改分。同一事件被重复触发时第二次拿到 0 行，不会重复加分。</p>
 */
@Service
public class CreditService {

    private static final Logger log = LoggerFactory.getLogger(CreditService.class);

    public static final int INITIAL_SCORE = 100;
    public static final int MIN_SCORE = 0;
    public static final int MAX_SCORE = 150;

    /** 个人中心展示的流水条数上限 */
    private static final int RECENT_LOG_LIMIT = 20;

    private final CreditRepository creditRepository;

    public CreditService(CreditRepository creditRepository) {
        this.creditRepository = creditRepository;
    }

    /**
     * 记录一次信用事件。
     *
     * @param userId 被加/扣分的用户
     * @param event  事件类型，决定加减分值
     * @param bizId  业务ID（订单ID/举报ID/用户ID），与 event+userId 构成幂等键
     * @return true 表示本次确实记了一笔；重复事件或失败返回 false
     */
    public boolean record(Long userId, CreditEvent event, Long bizId, String remark) {
        if (userId == null || event == null || bizId == null) {
            return false;
        }
        try {
            int inserted = creditRepository.insertLogIgnore(
                    userId, event.name(), bizId, event.getDelta(), clip(remark));
            if (inserted == 0) {
                // 唯一键挡下：同一事件已经记过分，不再改动分值
                return false;
            }
            if (creditRepository.adjust(userId, event.getDelta(), MIN_SCORE, MAX_SCORE) == 0) {
                log.warn("信用分流水已写入但用户不存在: userId={}, event={}", userId, event);
            }
            return true;
        } catch (RuntimeException e) {
            log.warn("信用分记录失败（不影响主业务）: userId={}, event={}, 原因={}",
                    userId, event, e.getMessage());
            return false;
        }
    }

    /** 当前信用分；读不到（含迁移未执行）按初始分处理 */
    public int getScore(Long userId) {
        if (userId == null) {
            return INITIAL_SCORE;
        }
        try {
            Integer score = creditRepository.scoreOf(userId);
            return score == null ? INITIAL_SCORE : score;
        } catch (RuntimeException e) {
            log.warn("读取信用分失败，按初始分处理: userId={}, 原因={}", userId, e.getMessage());
            return INITIAL_SCORE;
        }
    }

    /** 是否受限（不能下单、不能发布商品） */
    public boolean isRestricted(Long userId) {
        return CreditLevel.of(getScore(userId)) == CreditLevel.RESTRICTED;
    }

    /** 公开信息：只有分数和等级，供商品详情页展示卖家信用 */
    public Map<String, Object> summaryOf(Long userId) {
        int score = getScore(userId);
        CreditLevel level = CreditLevel.of(score);
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("score", score);
        summary.put("maxScore", MAX_SCORE);
        summary.put("level", level.name());
        summary.put("levelLabel", level.getLabel());
        summary.put("restricted", level == CreditLevel.RESTRICTED);
        return summary;
    }

    /** 本人可见：在摘要基础上附最近的变动记录 */
    public Map<String, Object> detailOf(Long userId) {
        Map<String, Object> detail = summaryOf(userId);
        List<Map<String, Object>> logs = new ArrayList<>();
        try {
            for (CreditLogView view : creditRepository.recentLogs(userId, RECENT_LOG_LIMIT)) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("event", view.getEventType());
                row.put("label", CreditEvent.labelOf(view.getEventType()));
                row.put("delta", view.getDelta());
                row.put("remark", view.getRemark());
                row.put("createTime", view.getCreateTime());
                logs.add(row);
            }
        } catch (RuntimeException e) {
            // 摘要照常返回，只是没有明细
            log.warn("读取信用记录失败: userId={}, 原因={}", userId, e.getMessage());
        }
        detail.put("logs", logs);
        return detail;
    }

    /** 备注列上限 200，超长会被数据库截断成半句话，这里先裁 */
    private static String clip(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        return trimmed.length() > 200 ? trimmed.substring(0, 200) : trimmed;
    }
}
