package com.example.campussecondhand.service;

import com.example.campussecondhand.enums.CreditEvent;
import com.example.campussecondhand.enums.CreditLevel;
import com.example.campussecondhand.repository.CreditRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 信用分服务单测。
 *
 * <p>重点不在"分数算得对不对"，而在三条工程约束：
 * 幂等（同一事件不重复加分）、fail-open（读不到分时不误伤用户）、
 * fail-safe（记分失败不把主流程带崩）。</p>
 */
class CreditServiceTest {

    private CreditRepository repo;
    private CreditService service;

    @BeforeEach
    void setUp() {
        repo = mock(CreditRepository.class);
        service = new CreditService(repo);
    }

    @Test
    @DisplayName("首次记录：先写流水，成功后才改分，并带上下限")
    void records_log_then_adjusts_with_bounds() {
        when(repo.insertLogIgnore(eq(1L), eq("ORDER_COMPLETED"), eq(9L), eq(2), any())).thenReturn(1);
        when(repo.adjust(1L, 2, CreditService.MIN_SCORE, CreditService.MAX_SCORE)).thenReturn(1);

        assertThat(service.record(1L, CreditEvent.ORDER_COMPLETED, 9L, "完成订单")).isTrue();

        verify(repo).adjust(1L, 2, 0, 150);
    }

    @Test
    @DisplayName("重复事件：流水撞唯一键，不改分")
    void duplicate_event_does_not_change_score() {
        when(repo.insertLogIgnore(any(), any(), any(), anyInt(), any())).thenReturn(0);

        assertThat(service.record(1L, CreditEvent.ORDER_COMPLETED, 9L, "x")).isFalse();

        verify(repo, never()).adjust(any(), anyInt(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("数据库异常（如迁移没跑）：吞掉并返回 false，不影响主流程")
    void repository_failure_is_swallowed() {
        when(repo.insertLogIgnore(any(), any(), any(), anyInt(), any()))
                .thenThrow(new RuntimeException("Unknown column 'credit_score'"));

        assertThat(service.record(1L, CreditEvent.ORDER_TIMEOUT, 9L, "x")).isFalse();
    }

    @Test
    @DisplayName("参数缺失：直接跳过，不产生任何写库")
    void null_arguments_are_ignored() {
        assertThat(service.record(null, CreditEvent.ORDER_COMPLETED, 9L, "x")).isFalse();
        assertThat(service.record(1L, null, 9L, "x")).isFalse();
        assertThat(service.record(1L, CreditEvent.ORDER_COMPLETED, null, "x")).isFalse();

        verify(repo, never()).insertLogIgnore(any(), any(), any(), anyInt(), any());
    }

    @Test
    @DisplayName("受限边界：59 受限，60 不受限")
    void restricted_boundary() {
        when(repo.scoreOf(1L)).thenReturn(59);
        when(repo.scoreOf(2L)).thenReturn(60);

        assertThat(service.isRestricted(1L)).isTrue();
        assertThat(service.isRestricted(2L)).isFalse();
    }

    @Test
    @DisplayName("读分失败按未受限处理，不能误伤用户")
    void score_lookup_failure_is_fail_open() {
        when(repo.scoreOf(1L)).thenThrow(new RuntimeException("boom"));

        assertThat(service.isRestricted(1L)).isFalse();
        assertThat(service.getScore(1L)).isEqualTo(CreditService.INITIAL_SCORE);
    }

    @Test
    @DisplayName("等级分段")
    void level_boundaries() {
        assertThat(CreditLevel.of(150)).isEqualTo(CreditLevel.EXCELLENT);
        assertThat(CreditLevel.of(120)).isEqualTo(CreditLevel.EXCELLENT);
        assertThat(CreditLevel.of(119)).isEqualTo(CreditLevel.GOOD);
        assertThat(CreditLevel.of(80)).isEqualTo(CreditLevel.GOOD);
        assertThat(CreditLevel.of(79)).isEqualTo(CreditLevel.NORMAL);
        assertThat(CreditLevel.of(60)).isEqualTo(CreditLevel.NORMAL);
        assertThat(CreditLevel.of(59)).isEqualTo(CreditLevel.RESTRICTED);
        assertThat(CreditLevel.of(0)).isEqualTo(CreditLevel.RESTRICTED);
    }

    /**
     * 分值定义的规格锁定。
     *
     * <p>这些数字是产品规则，散落在答辩文档与前端文案里。测试钉死它们，
     * 改动必须是一个"有意识"的动作，而不是顺手调参。</p>
     */
    @Test
    @DisplayName("事件分值符合规格")
    void event_deltas_match_spec() {
        assertThat(CreditEvent.STUDENT_VERIFIED.getDelta()).isEqualTo(10);
        assertThat(CreditEvent.ORDER_COMPLETED.getDelta()).isEqualTo(2);
        assertThat(CreditEvent.REVIEW_GOOD.getDelta()).isEqualTo(1);
        assertThat(CreditEvent.REVIEW_BAD.getDelta()).isEqualTo(-3);
        assertThat(CreditEvent.ORDER_TIMEOUT.getDelta()).isEqualTo(-5);
        assertThat(CreditEvent.REPORT_UPHELD.getDelta()).isEqualTo(-15);
        assertThat(CreditService.INITIAL_SCORE).isEqualTo(100);
    }
}
