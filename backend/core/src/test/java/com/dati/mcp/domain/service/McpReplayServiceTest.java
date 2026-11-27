package com.dati.mcp.domain.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("McpReplayService read-only guard tests")
class McpReplayServiceTest {

    @Test
    @DisplayName("accepts plain selects, with-queries, describe, and comment-prefixed reads")
    void acceptsReadOnlyStatements() {
        assertThat(McpReplayService.isReadOnlySql("SELECT 1")).isTrue();
        assertThat(McpReplayService.isReadOnlySql("with x as (select 1) select * from x")).isTrue();
        assertThat(McpReplayService.isReadOnlySql("DESCRIBE t_mes_station")).isTrue();
        assertThat(McpReplayService.isReadOnlySql("-- report query\nSELECT * FROM t")).isTrue();
        assertThat(McpReplayService.isReadOnlySql("/* audit */ SELECT * FROM t")).isTrue();
        assertThat(McpReplayService.isReadOnlySql("SELECT 1; SELECT 2;")).isTrue();
    }

    @Test
    @DisplayName("rejects writes even when hidden behind a leading read or comment")
    void rejectsWrites() {
        assertThat(McpReplayService.isReadOnlySql("DELETE FROM t")).isFalse();
        assertThat(McpReplayService.isReadOnlySql("SELECT 1; DELETE FROM t")).isFalse();
        assertThat(McpReplayService.isReadOnlySql("/* x */ INSERT INTO t VALUES (1)")).isFalse();
        assertThat(McpReplayService.isReadOnlySql("select 1; update t set a = 1")).isFalse();
        assertThat(McpReplayService.isReadOnlySql("WITH x AS (DELETE FROM t) SELECT 1")).isFalse();
        assertThat(McpReplayService.isReadOnlySql("-- c\nwith d as (select 1) delete from t")).isFalse();
    }

    @Test
    @DisplayName("write-looking column names do not false-positive")
    void writeLookingColumnNamesPass() {
        assertThat(McpReplayService.isReadOnlySql("SELECT update_time, created_by FROM t")).isTrue();
    }
}
