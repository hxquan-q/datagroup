package com.dati.mcp.domain.service;

import com.dati.mcp.domain.model.McpToolType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

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
    @DisplayName("Second call for the same key is served without reloading")
    void secondCallHitsCache() {
        Map<String, Object> args = Map.of("table", "t_mo");
        String key = cache.key(1, "get_table_info", args);
        AtomicInteger loads = new AtomicInteger();
        Object data = Map.of("columns", List.of("id"));
        Object first = cache.getOrLoad(McpToolType.GET_TABLE_INFO, args, key, () -> {
            loads.incrementAndGet();
            return data;
        });
        Object second = cache.getOrLoad(McpToolType.GET_TABLE_INFO, args, key, () -> {
            loads.incrementAndGet();
            return Map.of("columns", List.of("other"));
        });
        assertSame(data, first);
        assertSame(data, second);
        assertEquals(1, loads.get());
    }

    @Test
    @DisplayName("Uncached tool types always reload")
    void uncachedToolType() {
        Map<String, Object> args = Map.of();
        String key = cache.key(1, "update_table_info", args);
        AtomicInteger loads = new AtomicInteger();
        cache.getOrLoad(McpToolType.UPDATE_TABLE_INFO, args, key, () -> {
            loads.incrementAndGet();
            return Map.of("ok", true);
        });
        cache.getOrLoad(McpToolType.UPDATE_TABLE_INFO, args, key, () -> {
            loads.incrementAndGet();
            return Map.of("ok", true);
        });
        assertEquals(2, loads.get());
    }

    @Test
    @DisplayName("SQL results at or above the row cap are evicted after the coalesced load")
    void sqlRowCap() {
        Map<String, Object> args = Map.of("sql", "select 1");
        String key = cache.key(1, "execute_sql", args);
        List<Object> rows = java.util.Collections.nCopies(500, List.of(1));
        Object bigResult = Map.of("results", List.of(Map.of("rows", rows)));
        AtomicInteger loads = new AtomicInteger();
        cache.getOrLoad(McpToolType.EXECUTE_SQL, args, key, () -> {
            loads.incrementAndGet();
            return bigResult;
        });
        cache.getOrLoad(McpToolType.EXECUTE_SQL, args, key, () -> {
            loads.incrementAndGet();
            return bigResult;
        });
        assertEquals(2, loads.get());
    }

    @Test
    @DisplayName("Small SQL results are cached")
    void smallSqlCached() {
        Map<String, Object> args = Map.of("sql", "select 1");
        String key = cache.key(1, "execute_sql", args);
        Object smallResult = Map.of("results", List.of(Map.of("rows", List.of(List.of(1)))));
        AtomicInteger loads = new AtomicInteger();
        cache.getOrLoad(McpToolType.EXECUTE_SQL, args, key, () -> {
            loads.incrementAndGet();
            return smallResult;
        });
        Object hit = cache.getOrLoad(McpToolType.EXECUTE_SQL, args, key, () -> {
            loads.incrementAndGet();
            return Map.of();
        });
        assertSame(smallResult, hit);
        assertEquals(1, loads.get());
    }

    @Test
    @DisplayName("Historical metric results use the cold cache")
    void historicalMetricCached() {
        Map<String, Object> args = Map.of("metric", "m1", "end_time", "2020-01-01 00:00:00");
        String key = cache.key(1, "query_metric", args);
        Object data = Map.of("rows", List.of());
        cache.getOrLoad(McpToolType.QUERY_METRIC, args, key, () -> data);
        AtomicInteger loads = new AtomicInteger();
        Object hit = cache.getOrLoad(McpToolType.QUERY_METRIC, args, key, () -> {
            loads.incrementAndGet();
            return Map.of();
        });
        assertSame(data, hit);
        assertEquals(0, loads.get());
    }

    @Test
    @DisplayName("Today-covering metric results use the hot cache")
    void hotMetricCached() {
        String todayEnd = LocalDateTime.now().plusDays(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        Map<String, Object> args = Map.of("metric", "m1", "end_time", todayEnd);
        String key = cache.key(1, "query_metric", args);
        Object data = Map.of("rows", List.of());
        cache.getOrLoad(McpToolType.QUERY_METRIC, args, key, () -> data);
        AtomicInteger loads = new AtomicInteger();
        Object hit = cache.getOrLoad(McpToolType.QUERY_METRIC, args, key, () -> {
            loads.incrementAndGet();
            return Map.of();
        });
        assertSame(data, hit);
        assertEquals(0, loads.get());
    }

    @Test
    @DisplayName("Loader exceptions propagate and are never cached")
    void loaderExceptionNotCached() {
        Map<String, Object> args = Map.of("table", "t_mo");
        String key = cache.key(1, "get_table_info", args);
        AtomicInteger loads = new AtomicInteger();
        assertThrows(RuntimeException.class, () -> cache.getOrLoad(McpToolType.GET_TABLE_INFO, args, key, () -> {
            loads.incrementAndGet();
            throw new RuntimeException("db down");
        }));
        Object data = Map.of("columns", List.of());
        Object loaded = cache.getOrLoad(McpToolType.GET_TABLE_INFO, args, key, () -> {
            loads.incrementAndGet();
            return data;
        });
        assertSame(data, loaded);
        assertEquals(2, loads.get());
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
