package com.dati.mcp.domain.service;

import com.dati.auth.authentication.User;
import com.dati.base.RequestContext;
import com.dati.mcp.domain.model.McpToolType;
import com.dati.mcp.server.pojo.BatchExecution;
import com.dati.mcp.server.pojo.MetricExecution;
import com.dati.mcp.server.pojo.SqlExecution;
import com.dati.mcp.server.pojo.StatementResult;
import com.dati.mcp.server.pojo.ToolTestData;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.AsyncCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/**
 * Three-tier in-process result cache for MCP tool executions.
 *
 * L1 metadata (table schemas, semantics, metric inspection): 15-minute TTL.
 * L2 governed metric results: hot/cold TTL by time coverage — results whose
 * time range ends before today (closed historical data) live 6 hours; results
 * covering today live 5 minutes.
 * L3 free SQL / parameterized tools: 90-second TTL, small result sets only
 * (fewer than 500 rows), absorbing repeated probe queries from agents.
 *
 * Keys combine service id, active snapshot version, caller id, tool name and
 * canonical arguments: services and callers never share entries, and publishing
 * a new version makes every older entry unreachable.
 *
 * Only fully successful, read-only results are stored. A result that contains a
 * write clears every SQL and metric result tier, because the written rows may
 * back any cached query. A metadata update tool clears the metadata tier.
 *
 * Concurrent identical calls share one in-flight execution. The loader runs on
 * the caller's thread; a caller that joins an execution whose result is not
 * cacheable (a write, a failure, or an exception) runs its own loader.
 *
 * Stale-while-revalidate: a hit older than 80% of its tier TTL is returned
 * immediately and reloaded on a background thread that carries the caller's
 * request user. The reload replaces the entry only if it is still the entry
 * that was served, so a refresh never restores data cleared by a write.
 */
@Slf4j
@Component
public class ToolResultCache {

    private static final int SQL_CACHE_ROW_CAP = 500;
    private static final double REFRESH_AFTER_FRACTION = 0.8;

    private static final Set<McpToolType> METADATA_TOOLS = Set.of(
        McpToolType.GET_TABLE_INFO, McpToolType.LIST_TABLES, McpToolType.SEARCH_METADATA,
        McpToolType.SEARCH_SEMANTICS, McpToolType.INSPECT_METRIC);

    private static final Set<McpToolType> SQL_TOOLS = Set.of(
        McpToolType.EXECUTE_SQL, McpToolType.EXECUTE_QUERY_BATCH, McpToolType.PARAMETERIZED_SQL);

    private static final Set<McpToolType> METADATA_WRITE_TOOLS = Set.of(
        McpToolType.UPDATE_TABLE_INFO, McpToolType.UPDATE_COLUMN_INFO, McpToolType.UPSERT_TERM);

    private record Entry(ToolTestData data, long writtenAt) {
    }

    private record Tier(AsyncCache<String, Entry> cache, Duration ttl) {
    }

    private final Ticker ticker;
    private final Executor refreshExecutor;
    private final Tier metadataTier;
    private final Tier metricHotTier;
    private final Tier metricColdTier;
    private final Tier sqlTier;
    private final Set<String> refreshing = ConcurrentHashMap.newKeySet();
    private final ObjectMapper mapper = new ObjectMapper();

    public ToolResultCache() {
        this(Ticker.systemTicker(), Executors.newFixedThreadPool(2, runnable -> {
            Thread thread = new Thread(runnable, "mcp-cache-refresh");
            thread.setDaemon(true);
            return thread;
        }));
    }

    ToolResultCache(Ticker ticker, Executor refreshExecutor) {
        this.ticker = ticker;
        this.refreshExecutor = refreshExecutor;
        this.metadataTier = tier(2_000, Duration.ofMinutes(15));
        this.metricHotTier = tier(2_000, Duration.ofMinutes(5));
        this.metricColdTier = tier(5_000, Duration.ofHours(6));
        this.sqlTier = tier(2_000, Duration.ofSeconds(90));
    }

    private Tier tier(long maxSize, Duration ttl) {
        return new Tier(Caffeine.newBuilder().maximumSize(maxSize).expireAfterWrite(ttl)
            .ticker(ticker).buildAsync(), ttl);
    }

    /**
     * Returns the cached result for this call, or runs {@code loader} and caches
     * its result when it is a fully successful read.
     *
     * @param userId caller id from the request context; null for anonymous calls
     */
    public ToolTestData getOrExecute(String serviceId, Integer versionNumber, String userId,
                                     McpToolType type, String toolName, Map<String, Object> arguments,
                                     Supplier<ToolTestData> loader) {
        Tier tier = tierFor(type, arguments);
        String key = tier == null ? null : key(serviceId, versionNumber, userId, toolName, arguments);
        if (key == null) {
            return execute(type, loader);
        }

        CompletableFuture<Entry> pending = new CompletableFuture<>();
        CompletableFuture<Entry> existing = tier.cache().asMap().putIfAbsent(key, pending);
        if (existing != null) {
            Entry shared = existing.join();
            if (shared == null) {
                return execute(type, loader);
            }
            refreshIfStale(tier, type, key, existing, shared, loader);
            return shared.data();
        }

        ToolTestData data = null;
        try {
            data = execute(type, loader);
            return data;
        } finally {
            // Completing with null makes Caffeine drop the entry; a throwing loader
            // leaves data null, so waiters run their own loader.
            pending.complete(isCacheable(type, data) ? new Entry(data, ticker.read()) : null);
        }
    }

    private void refreshIfStale(Tier tier, McpToolType type, String key, CompletableFuture<Entry> served,
                                Entry entry, Supplier<ToolTestData> loader) {
        long age = ticker.read() - entry.writtenAt();
        if (age <= tier.ttl().toNanos() * REFRESH_AFTER_FRACTION || !refreshing.add(key)) {
            return;
        }
        User user = RequestContext.getUser();
        refreshExecutor.execute(() -> {
            RequestContext.setUser(user);
            try {
                ToolTestData refreshed = execute(type, loader);
                if (isCacheable(type, refreshed)) {
                    tier.cache().asMap().replace(key, served,
                        CompletableFuture.completedFuture(new Entry(refreshed, ticker.read())));
                } else {
                    tier.cache().asMap().remove(key, served);
                }
            } catch (RuntimeException e) {
                log.debug("Background cache refresh failed for {}: {}", key, e.getMessage());
            } finally {
                RequestContext.clear();
                refreshing.remove(key);
            }
        });
    }

    String key(String serviceId, Integer versionNumber, String userId,
               String toolName, Map<String, Object> arguments) {
        try {
            return serviceId + "|" + versionNumber + "|" + userId + "|" + toolName + "|"
                + mapper.writeValueAsString(canonicalize(arguments));
        } catch (Exception e) {
            return null;
        }
    }

    private ToolTestData execute(McpToolType type, Supplier<ToolTestData> loader) {
        ToolTestData data = loader.get();
        if (METADATA_WRITE_TOOLS.contains(type)) {
            metadataTier.cache().synchronous().invalidateAll();
        }
        if (containsWrite(data)) {
            sqlTier.cache().synchronous().invalidateAll();
            metricHotTier.cache().synchronous().invalidateAll();
            metricColdTier.cache().synchronous().invalidateAll();
        }
        return data;
    }

    }

    private Tier tierFor(McpToolType type, Map<String, Object> arguments) {
        if (METADATA_TOOLS.contains(type)) {
            return metadataTier;
        }
        if (type == McpToolType.QUERY_METRIC) {
            return isHistorical(arguments) ? metricColdTier : metricHotTier;
        }
        if (SQL_TOOLS.contains(type)) {
            return sqlTier;
        }
        return null;
    }

    private boolean isCacheable(McpToolType type, ToolTestData data) {
        if (data == null) {
            return false;
        }
        List<StatementResult> statements = statementsOf(data);
        if (statements == null) {
            return !SQL_TOOLS.contains(type) && type != McpToolType.QUERY_METRIC;
        }
        for (StatementResult statement : statements) {
            if (!(statement instanceof StatementResult.SelectResult) || !statement.success()) {
                return false;
            }
        }
        boolean rowCapped = type == McpToolType.EXECUTE_SQL || type == McpToolType.EXECUTE_QUERY_BATCH;
        return !rowCapped || countRows(statements) < SQL_CACHE_ROW_CAP;
    }

    private boolean containsWrite(ToolTestData data) {
        List<StatementResult> statements = statementsOf(data);
        return statements != null
            && statements.stream().anyMatch(StatementResult.WriteResult.class::isInstance);
    }

    /**
     * Flattens the statement results of SQL-backed tool data. Returns null for
     * data that carries no statements, and a list holding a failed placeholder
     * for a batch step that never produced an execution.
     */
    private List<StatementResult> statementsOf(ToolTestData data) {
        if (data instanceof SqlExecution execution) {
            return execution.results() == null ? List.of() : execution.results();
        }
        if (data instanceof MetricExecution metric) {
            return metric.execution() == null ? List.of(StatementResult.selectFailure(null))
                : statementsOf(metric.execution());
        }
        if (data instanceof BatchExecution batch) {
            ArrayList<StatementResult> all = new ArrayList<>();
            for (BatchExecution.StepResult step : batch.steps()) {
                if (!step.success() || step.execution() == null) {
                    all.add(StatementResult.selectFailure(step.error()));
                } else {
                    all.addAll(statementsOf(step.execution()));
                }
            }
            return all;
        }
        return null;
    }

    private int countRows(List<StatementResult> statements) {
        int total = 0;
        for (StatementResult statement : statements) {
            if (statement instanceof StatementResult.SelectResult select && select.rows() != null) {
                total += select.rows().size();
            }
        }
        return total;
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
