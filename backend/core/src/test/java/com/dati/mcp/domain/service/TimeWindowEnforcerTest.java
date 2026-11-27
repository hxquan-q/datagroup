package com.dati.mcp.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TimeWindowEnforcerTest {

    private static final Map<String, TimeWindowEnforcer.Rule> RULES = Map.of(
        "t_barcode", new TimeWindowEnforcer.Rule("t_barcode", "FCreateTime", 31));

    @Test
    @DisplayName("rejects unfiltered queries on rule-marked tables")
    void rejectsUnfiltered() {
        List<TimeWindowEnforcer.Violation> violations =
            TimeWindowEnforcer.check("SELECT FType, COUNT(*) FROM t_barcode GROUP BY FType", RULES);
        assertThat(violations).hasSize(1);
        assertThat(violations.get(0).timeColumn()).isEqualTo("FCreateTime");
    }

    @Test
    @DisplayName("rejects one-sided time filters")
    void rejectsOneSided() {
        assertThat(TimeWindowEnforcer.check(
            "SELECT * FROM t_barcode WHERE FCreateTime >= '2025-09-01'", RULES)).hasSize(1);
    }

    @Test
    @DisplayName("accepts bounded literal ranges within the limit")
    void acceptsBoundedRange() {
        assertThat(TimeWindowEnforcer.check(
            "SELECT * FROM t_barcode WHERE FCreateTime >= '2025-09-01' AND FCreateTime < '2025-09-20'", RULES))
            .isEmpty();
    }

    @Test
    @DisplayName("rejects ranges wider than the configured maximum")
    void rejectsWideRange() {
        assertThat(TimeWindowEnforcer.check(
            "SELECT * FROM t_barcode WHERE FCreateTime >= '2025-01-01' AND FCreateTime < '2025-09-01'", RULES))
            .hasSize(1);
    }

    @Test
    @DisplayName("accepts BETWEEN bounds")
    void acceptsBetween() {
        assertThat(TimeWindowEnforcer.check(
            "SELECT * FROM t_barcode WHERE FCreateTime BETWEEN '2025-09-01' AND '2025-09-10'", RULES))
            .isEmpty();
    }

    @Test
    @DisplayName("accepts bounded filters inside subqueries")
    void acceptsSubqueryFilter() {
        assertThat(TimeWindowEnforcer.check(
            "SELECT * FROM (SELECT * FROM t_barcode WHERE FCreateTime >= '2025-09-01' AND FCreateTime < '2025-09-05') t",
            RULES)).isEmpty();
    }

    @Test
    @DisplayName("passes non-literal bounds without width validation")
    void passesNonLiteralBounds() {
        assertThat(TimeWindowEnforcer.check(
            "SELECT * FROM t_barcode WHERE FCreateTime >= DATE_SUB(NOW(), INTERVAL 1 DAY) AND FCreateTime < NOW()",
            RULES)).isEmpty();
    }

    @Test
    @DisplayName("rejects bounds hidden behind OR branches")
    void rejectsOrBranches() {
        assertThat(TimeWindowEnforcer.check(
            "SELECT * FROM t_barcode WHERE FType = 'X' OR (FCreateTime >= '2025-09-01' AND FCreateTime < '2025-09-05')",
            RULES)).hasSize(1);
    }

    @Test
    @DisplayName("rejects NOT BETWEEN as it admits rows outside the range")
    void rejectsNotBetween() {
        assertThat(TimeWindowEnforcer.check(
            "SELECT * FROM t_barcode WHERE FCreateTime NOT BETWEEN '2025-09-01' AND '2025-09-10'", RULES))
            .hasSize(1);
    }

    @Test
    @DisplayName("credits bounded filters on UPDATE statements")
    void creditsUpdateWhere() {
        assertThat(TimeWindowEnforcer.check(
            "UPDATE t_barcode SET FType = 'X' WHERE FCreateTime >= '2025-09-01' AND FCreateTime < '2025-09-05'",
            RULES)).isEmpty();
    }

    @Test
    @DisplayName("rejects unfiltered DELETE statements")
    void rejectsUnfilteredDelete() {
        assertThat(TimeWindowEnforcer.check("DELETE FROM t_barcode WHERE FType = 'X'", RULES)).hasSize(1);
    }

    @Test
    @DisplayName("inline bind values substitute placeholders outside literals")
    void inlineBindValues() {
        String inlined = ExecuteSqlExecutor.inlineBindValues(
            "SELECT * FROM t WHERE a >= ? AND b < ? AND c = '?not-param'",
            java.util.List.of("2025-09-01", 5));
        org.assertj.core.api.Assertions.assertThat(inlined)
            .isEqualTo("SELECT * FROM t WHERE a >= '2025-09-01' AND b < 5 AND c = '?not-param'");
    }

    @Test
    @DisplayName("rejects wide ranges even when bounds arrive as inlined parameters")
    void rejectsWideInlinedRange() {
        String sql = ExecuteSqlExecutor.inlineBindValues(
            "SELECT * FROM t_barcode WHERE FCreateTime >= ? AND FCreateTime < ?",
            java.util.List.of("2025-01-01", "2025-12-01"));
        assertThat(TimeWindowEnforcer.check(sql, RULES)).hasSize(1);
    }

    @Test
    @DisplayName("escapes single quotes in inlined string parameters")
    void escapesQuotes() {
        String inlined = ExecuteSqlExecutor.inlineBindValues("SELECT * FROM t WHERE a = ?",
            java.util.List.of("x'; DROP TABLE u; --"));
        org.assertj.core.api.Assertions.assertThat(inlined).contains("'x''; DROP TABLE u; --'");
    }

    @Test
    @DisplayName("passes queries when no rules configured")
    void passesWithoutRules() {
        assertThat(TimeWindowEnforcer.check("SELECT * FROM t_barcode", Map.of())).isEmpty();
    }
}
