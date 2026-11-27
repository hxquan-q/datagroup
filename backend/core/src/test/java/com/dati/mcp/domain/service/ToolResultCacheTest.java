package com.dati.mcp.domain.service;

import com.dati.mcp.domain.model.McpToolType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ToolResultCacheTest {

    private final ToolResultCache cache = new ToolResultCache();

    @Test
    @DisplayName("Cache key is independent of argument ordering")
    void keyIsOrderIndependent() {
        String key1 = cache.key(3, "query_metric", orderedMap());
        String key2 = cache.key(3, "query_metric", reversedMap());
        assertEquals(key1, key2);
    }

    @Test
    @DisplayName("Cache key changes with snapshot version")
    void keyChangesWithVersion() {
        assertNotEquals(cache.key(3, "query_metric", orderedMap()), cache.key(4, "query_metric", orderedMap()));
    }

    @Test
    @DisplayName("Metadata tool result round-trips through cache")
    void metadataRoundTrip() {
        Map<String, Object> args = Map.of("table", "t_mo");
        String key = cache.key(1, "get_table_info", args);
        Object data = Map.of("columns", List.of("id"));
        cache.store(McpToolType.GET_TABLE_INFO, args, key, data);
        assertSame(data, cache.get(McpToolType.GET_TABLE_INFO, args, key));
    }

    @Test
    @DisplayName("Uncached tool types are never stored")
    void uncachedToolType() {
        Map<String, Object> args = Map.of();
        String key = cache.key(1, "update_table_info", args);
        cache.store(McpToolType.UPDATE_TABLE_INFO, args, key, Map.of("ok", true));
        assertNull(cache.get(McpToolType.UPDATE_TABLE_INFO, args, key));
    }

    @Test
    @DisplayName("SQL results at or above the row cap are not cached")
    void sqlRowCap() {
        Map<String, Object> args = Map.of("sql", "select 1");
        String key = cache.key(1, "execute_sql", args);
        List<Object> rows = java.util.Collections.nCopies(500, List.of(1));
        Object bigResult = Map.of("results", List.of(Map.of("rows", rows)));
        cache.store(McpToolType.EXECUTE_SQL, args, key, bigResult);
        assertNull(cache.get(McpToolType.EXECUTE_SQL, args, key));
    }

    @Test
    @DisplayName("Small SQL results are cached")
    void smallSqlCached() {
        Map<String, Object> args = Map.of("sql", "select 1");
        String key = cache.key(1, "execute_sql", args);
        Object smallResult = Map.of("results", List.of(Map.of("rows", List.of(List.of(1)))));
        cache.store(McpToolType.EXECUTE_SQL, args, key, smallResult);
        assertSame(smallResult, cache.get(McpToolType.EXECUTE_SQL, args, key));
    }

    @Test
    @DisplayName("Historical metric results use the cold cache")
    void historicalMetricCached() {
        Map<String, Object> args = Map.of("metric", "m1", "end_time", "2020-01-01 00:00:00");
        String key = cache.key(1, "query_metric", args);
        Object data = Map.of("rows", List.of());
        cache.store(McpToolType.QUERY_METRIC, args, key, data);
        assertSame(data, cache.get(McpToolType.QUERY_METRIC, args, key));
    }

    @Test
    @DisplayName("Today-covering metric results use the hot cache")
    void hotMetricCached() {
        String todayEnd = LocalDateTime.now().plusDays(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        Map<String, Object> args = Map.of("metric", "m1", "end_time", todayEnd);
        String key = cache.key(1, "query_metric", args);
        Object data = Map.of("rows", List.of());
        cache.store(McpToolType.QUERY_METRIC, args, key, data);
        assertSame(data, cache.get(McpToolType.QUERY_METRIC, args, key));
    }

    private Map<String, Object> orderedMap() {
        return Map.of("a", "1", "b", "2");
    }

    private Map<String, Object> reversedMap() {
        Map<String, Object> map = new java.util.LinkedHashMap<>();
        map.put("b", "2");
        map.put("a", "1");
        return map;
    }
}
