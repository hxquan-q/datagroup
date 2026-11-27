package com.dati.mcp.domain.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ExplainGuardrail plan inspection tests")
class ExplainGuardrailTest {

    @Test
    @DisplayName("full table scan over the row limit is flagged with table and keys")
    void flagsFullScanOverLimit() {
        String plan = """
            {"query_block": {"select_id": 1, "table": {
              "table_name": "t_mes_passstaion_sinter",
              "access_type": "ALL",
              "rows_examined_per_scan": 8234567,
              "possible_keys": ["idx_create_time"]
            }}}
            """;
        List<ExplainGuardrail.Offender> offenders = ExplainGuardrail.inspectMySqlPlan(plan, 5_000_000);
        assertThat(offenders).singleElement().satisfies(offender -> {
            assertThat(offender.table()).isEqualTo("t_mes_passstaion_sinter");
            assertThat(offender.accessType()).isEqualTo("ALL");
            assertThat(offender.estimatedRows()).isEqualTo(8234567L);
            assertThat(offender.possibleKeys()).containsExactly("idx_create_time");
        });
    }

    @Test
    @DisplayName("full scan under the limit passes")
    void passesSmallScan() {
        String plan = """
            {"query_block": {"table": {
              "table_name": "t_small", "access_type": "ALL", "rows_examined_per_scan": 500
            }}}
            """;
        assertThat(ExplainGuardrail.inspectMySqlPlan(plan, 5_000_000)).isEmpty();
    }

    @Test
    @DisplayName("indexed range and ref access pass regardless of estimate")
    void passesIndexedAccess() {
        String plan = """
            {"query_block": {"table": {
              "table_name": "t_big", "access_type": "range", "rows_examined_per_scan": 9000000
            }}}
            """;
        assertThat(ExplainGuardrail.inspectMySqlPlan(plan, 5_000_000)).isEmpty();
    }

    @Test
    @DisplayName("full index scan over the limit is flagged")
    void flagsFullIndexScan() {
        String plan = """
            {"query_block": {"table": {
              "table_name": "t_big", "access_type": "index", "rows_examined_per_scan": 7000000
            }}}
            """;
        assertThat(ExplainGuardrail.inspectMySqlPlan(plan, 5_000_000)).hasSize(1);
    }

    @Test
    @DisplayName("nested join plans are walked recursively and derived tables are skipped")
    void walksNestedPlans() {
        String plan = """
            {"query_block": {
              "nested_loop": [
                {"table": {"table_name": "t_fact", "access_type": "ALL", "rows_examined_per_scan": 8000000}},
                {"table": {"table_name": "<derived2>", "access_type": "ALL", "rows_examined_per_scan": 99999999}},
                {"table": {"table_name": "t_dim", "access_type": "eq_ref", "rows_examined_per_scan": 1}}
              ]
            }}
            """;
        List<ExplainGuardrail.Offender> offenders = ExplainGuardrail.inspectMySqlPlan(plan, 5_000_000);
        assertThat(offenders).singleElement().satisfies(offender ->
            assertThat(offender.table()).isEqualTo("t_fact"));
    }

    @Test
    @DisplayName("invalid plan json yields no offenders")
    void toleratesInvalidJson() {
        assertThat(ExplainGuardrail.inspectMySqlPlan("not json", 5_000_000)).isEmpty();
    }

    @Test
    @DisplayName("detects oversized scans hidden in materialized subqueries")
    void materializedSubquery() {
        String plan = """
            {"query_block": {"select_id": 1, "table": {"table_name": "<subquery2>", "access_type": "ALL",
              "rows_examined_per_scan": 1,
              "materialized_from_subquery": {"query_block": {"table": {
                "table_name": "big_table", "access_type": "ALL", "rows_examined_per_scan": 9000000}}}}}}
            """;
        var offenders = ExplainGuardrail.inspectMySqlPlan(plan, 5000000);
        org.assertj.core.api.Assertions.assertThat(offenders).hasSize(1);
        org.assertj.core.api.Assertions.assertThat(offenders.get(0).table()).isEqualTo("big_table");
    }
}
