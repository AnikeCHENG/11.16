package com.example.campussecondhand.task;

import com.example.campussecondhand.service.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 发货后自动确认收货任务。
 *
 * <p><b>为什么需要它：</b>卖家发货后只能等买家手动点「确认收货」，
 * 买家不点，卖家的货款就永远停在平台侧。二手交易没有物流签收数据，
 * 无法按真实签收时间兜底，只能以卖家发货时间（{@code orders.shipped_time}）
 * 为起点计算。</p>
 *
 * <p>扫描间隔与天数均可配置；{@code order.auto-confirm-days <= 0} 时
 * 任务直接跳过，不产生任何查询。</p>
 */
@Component
public class AutoConfirmTask {

    private static final Logger log = LoggerFactory.getLogger(AutoConfirmTask.class);

    private final OrderService orderService;
    private final int autoConfirmDays;

    public AutoConfirmTask(OrderService orderService,
                           @Value("${order.auto-confirm-days:7}") int autoConfirmDays) {
        this.orderService = orderService;
        this.autoConfirmDays = autoConfirmDays;
    }

    /**
     * 用 {@code fixedDelay} 避免两次扫描重叠；异常必须兜住，
     * 否则一次失败会让后续调度全部取消。
     */
    @Scheduled(
            fixedDelayString = "${order.auto-confirm-scan-interval-ms:3600000}",
            initialDelayString = "${order.auto-confirm-initial-delay-ms:60000}")
    public void autoConfirmReceipts() {
        try {
            int completed = orderService.autoConfirmReceiptBatch(autoConfirmDays);
            if (completed > 0) {
                log.info("自动确认收货完成，共 {} 笔", completed);
            }
        } catch (Exception e) {
            log.error("自动确认收货任务执行异常", e);
        }
    }
}
