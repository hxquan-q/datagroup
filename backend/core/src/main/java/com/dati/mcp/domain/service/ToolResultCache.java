package com.dati.mcp.domain.service;

import com.dati.mcp.domain.model.McpToolType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/**
 * Three-tier in-process result cache for MCP tool executions.
 *
 * L1 metadata (table schemas, semantics, metric inspection): 15-minute TTL.
 * Entries are keyed by the service's active snapshot version, so publishing a
 * new version naturally invalidates every stale entry without explicit eviction.
 *
 * L2 governed metric results: hot/cold TTL by time coverage — results whose
 * time range ends before today (closed historical data) live 6 hours; results
 * covering today live 5 minutes.
 *
 * L3 free SQL / parameterized tools: 90-second TTL, small result sets only
 * (fewer than 500 rows), absorbing repeated probe queries from agents.
 *
 * Concurrent identical calls are coalesced through Caffeine's atomic per-key
 * loading: only the first caller hits the database, the rest share its result.
 * Entries past 80% of their TTL are served stale while a background thread
 * refreshes them, so TTL expiry never stampedes the database.
 */
@Slf4j
@Component
public class ToolResultCache {

    private static final int SQL_CACHE_ROW_CAP = 500;

    private static final Set<McpToolType> METADATA_TOOLS = Set.of(
        McpToolType.GET_TABLE_INFO, McpToolType.LIST_TABLES, McpToolType.SEARCH_METADATA,
        McpToolType.SEARCH_SEMANTICS, McpToolType.INSPECT_METRIC);

    private static final Set<McpToolType> SQL_TOOLS = Set.of(
        McpToolType.EXECUTE_SQL, McpToolType.EXECUTE_QUERY_BATCH, McpToolType.PARAMETERIZED_SQL);

    private static final Duration METADATA_TTL = Duration.ofMinutes(15);
    private static final Duration METRIC_HOT_TTL = Duration.ofMinutes(5);
    private static final Duration METRIC_COLD_TTL = Duration.ofHours(6);
    private static final Duration SQL_TTL = Duration.ofSeconds(90);
    private static final double REFRESH_AFTER_FRACTION = 0.8;

    private final Cache<String, Object> metadataCache = buildCache(2_000, METADATA_TTL);
    private final Cache<String, Object> metricHotCache = buildCache(2_000, METRIC_HOT_TTL);
    private final Cache<String, Object> metricColdCache = buildCache(5_000, METRIC_COLD_TTL);
    private final Cache<String, Object> sqlCache = buildCache(2_000, SQL_TTL);

    private final Map<String, Long> writeTimes = new ConcurrentHashMap<>();
    private final Set<String> refreshing = ConcurrentHashMap.newKeySet();
    private final ExecutorService refreshExecutor = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "mcp-cache-refresh");
        thread.setDaemon(true);
        return thread;
    });

    private Cache<String, Object> buildCache(long maxSize, Duration ttl) {
        return Caffeine.newBuilder().maximumSize(maxSize).expireAfterWrite(ttl)
            .removalListener((key, value, cause) -> writeTimes.remove(key))
            .build();
    }

    private final ObjectMapper mapper = new ObjectMapper();

    private Cache<String, Object> cacheFor(McpToolType type, Map<String, Object> arguments) {
        if (METADATA_TOOLS.contains(type)) {
            return metadataCache;
        }
        if (type == McpToolType.QUERY_METRIC) {
            return isHistorical(arguments) ? metricColdCache : metricHotCache;
        }
        if (SQL_TOOLS.contains(type)) {
            return sqlCache;
        }
        return null;
    }

    private Duration ttlFor(McpToolType type, Map<String, Object> arguments) {
        if (METADATA_TOOLS.contains(type)) {
            return METADATA_TTL;
        }
        if (type == McpToolType.QUERY_METRIC) {
            return isHistorical(arguments) ? METRIC_COLD_TTL : METRIC_HOT_TTL;
        }
        if (SQL_TOOLS.contains(type)) {
            return SQL_TTL;
        }
        return null;
    }

    /**
     * Returns the cached value or loads it atomically: concurrent callers for the
     * same key wait on a single database execution instead of stampeding it.
     * Loader exceptions propagate without caching. Stale-while-revalidate: a hit
     * older than 80% of the tier TTL is served immediately and refreshed in the
     * background. SQL results at or above the row cap are evicted right after the
     * coalesced load completes so they never serve a second caller.
     */
    public Object getOrLoad(McpToolType type, Map<String, Object> arguments, String key, Supplier<Object> loader) {
        Cache<String, Object> cache = cacheFor(type, arguments);
        if (cache == null || key == null) {
            return loader.get();
        }
        Object value = cache.get(key, k -> {
            Object loaded = loader.get();
            writeTimes.put(k, System.currentTimeMillis());
            return loaded;
        });
        if (SQL_TOOLS.contains(type) && type != McpToolType.PARAMETERIZED_SQL && countRows(value) >= SQL_CACHE_ROW_CAP) {
            cache.invalidate(key);
            writeTimes.remove(key);
            return value;
        }
        Duration ttl = ttlFor(type, arguments);
        Long written = writeTimes.get(key);
        if (ttl != null && written != null
            && System.currentTimeMillis() - written > ttl.toMillis() * REFRESH_AFTER_FRACTION
            && refreshing.add(key)) {
            refreshExecutor.submit(() -> {
                try {
                    Object refreshed = loader.get();
                    cache.put(key, refreshed);
                    writeTimes.put(key, System.currentTimeMillis());
                } catch (Exception e) {
                    log.debug("Background cache refresh failed for {}: {}", key, e.getMessage());
                } finally {
                    refreshing.remove(key);
                }
            });
        }
        return value;
    }

    /** Keyed by snapshot version: publishing naturally invalidates all entries. */
    public String key(Integer versionNumber, String toolName, Map<String, Object> arguments) {
        try {
            return versionNumber + "|" + toolName + "|" + mapper.writeValueAsString(canonicalize(arguments));
        } catch (Exception e) {
            return null;
        }
    }

    public Object get(McpToolType type, Map<String, Object> arguments, String key) {
        Cache<String, Object> cache = cacheFor(type, arguments);
        return cache == null || key == null ? null : cache.getIfPresent(key);
    }

    public void store(McpToolType type, Map<String, Object> arguments, String key, Object data) {
        Cache<String, Object> cache = cacheFor(type, arguments);
        if (cache == null || key == null || data == null) {
            return;
        }
        if (SQL_TOOLS.contains(type) && type != McpToolType.PARAMETERIZED_SQL && countRows(data) >= SQL_CACHE_ROW_CAP) {
            return;
        }
        cache.put(key, data);
    }

    /** Historical when end_time falls before the start of today in Asia/Shanghai. */
    private boolean isHistorical(Map<String, Object> arguments) {
        Object end = arguments.get("end_time");
        if (!(end instanceof String text) || text.isBlank()) {
            return false;
        }
        try {
            LocalDateTime endTime = parseDateTime(text.trim());
            if (endTime == null) {
                return false;
            }
            return endTime.isBefore(LocalDate.now(ZoneId.of("Asia/Shanghai")).atStartOfDay());
        } catch (Exception e) {
            return false;
        }
    }

    private LocalDateTime parseDateTime(String text) {
        for (DateTimeFormatter fmt : List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"))) {
            try {
                return LocalDateTime.parse(text, fmt);
            } catch (Exception ignored) {
            }
        }
        try {
            return LocalDate.parse(text, DateTimeFormatter.ofPattern("yyyy-MM-dd")).atStartOfDay();
        } catch (Exception ignored) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private int countRows(Object data) {
        try {
            Map<String, Object> map = mapper.convertValue(data, Map.class);
            Object results = map.get("results");
            if (!(results instanceof List<?> items)) {
                return 0;
            }
            int total = 0;
            for (Object item : items) {
                if (item instanceof Map<?, ?> itemMap) {
                    Object rows = itemMap.get("rows");
                    if (rows instanceof List<?> rowList) {
                        total += rowList.size();
                    }
                }
            }
            return total;
        } catch (Exception e) {
            return Integer.MAX_VALUE;
        }
    }

    @SuppressWarnings("unchecked")
    private Object canonicalize(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> sorted = new TreeMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                sorted.put(String.valueOf(entry.getKey()), canonicalize(entry.getValue()));
            }
            return sorted;
        }
        if (value instanceof List<?> list) {
            return list.stream().map(this::canonicalize).toList();
        }
        return value;
    }
}
