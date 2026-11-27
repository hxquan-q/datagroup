package com.dati.mcp.domain.service;

import com.dati.mcp.domain.model.McpToolType;
import com.dati.mcp.server.pojo.BatchExecution;
import com.dati.mcp.server.pojo.SqlExecution;
import com.dati.mcp.server.pojo.StatementResult;
import com.dati.mcp.server.pojo.TableListData;
import com.dati.mcp.server.pojo.ToolTestData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

class ToolResultCacheTest {

    private static final Map<String, Object> SQL_ARGS = Map.of("data_source_id", "ds1", "sql", "select 1");

    private final AtomicLong now = new AtomicLong();
    private final ToolResultCache cache = new ToolResultCache(now::get, Runnable::run);

    @Test
    @DisplayName("Cache key is independent of argument ordering")
    void keyIsOrderIndependent() {
        assertEquals(cache.key("svc", 3, "u1", "query_metric", orderedMap()),
            cache.key("svc", 3, "u1", "query_metric", reversedMap()));
    }

    @Test
    @DisplayName("Cache key changes with service, snapshot version and caller")
    void keyIsolatesServiceVersionAndCaller() {
        String base = cache.key("svc-a", 1, "u1", "execute_sql", SQL_ARGS);
        assertNotEquals(base, cache.key("svc-b", 1, "u1", "execute_sql", SQL_ARGS));
        assertNotEquals(base, cache.key("svc-a", 2, "u1", "execute_sql", SQL_ARGS));
        assertNotEquals(base, cache.key("svc-a", 1, "u2", "execute_sql", SQL_ARGS));
    }

    @Test
    @DisplayName("Services sharing a version number and SQL do not share results")
    void servicesDoNotShareResults() {
        ToolTestData a = select(1);
        ToolTestData b = select(2);
        assertSame(a, run("svc-a", "u1", McpToolType.EXECUTE_SQL, SQL_ARGS, () -> a));
        assertSame(b, run("svc-b", "u1", McpToolType.EXECUTE_SQL, SQL_ARGS, () -> b));
    }

    @Test
    @DisplayName("Small successful SELECT results are served from cache")
    void smallSelectCached() {
        AtomicInteger calls = new AtomicInteger();
        ToolTestData data = select(1);
        run("svc", "u1", McpToolType.EXECUTE_SQL, SQL_ARGS, counting(calls, data));
        assertSame(data, run("svc", "u1", McpToolType.EXECUTE_SQL, SQL_ARGS, counting(calls, data)));
        assertEquals(1, calls.get());
    }

    @Test
    @DisplayName("SQL results at or above the row cap are not cached")
    void sqlRowCap() {
        AtomicInteger calls = new AtomicInteger();
        ToolTestData big = new SqlExecution("select 1", List.of(StatementResult.select(
            List.of("c"), Collections.nCopies(500, List.<Object>of(1)), false)), null);
        run("svc", "u1", McpToolType.EXECUTE_SQL, SQL_ARGS, counting(calls, big));
        run("svc", "u1", McpToolType.EXECUTE_SQL, SQL_ARGS, counting(calls, big));
        assertEquals(2, calls.get());
    }

    @Test
    @DisplayName("Write results are never cached, so a repeated write executes again")
    void writesNotCached() {
        AtomicInteger calls = new AtomicInteger();
        Map<String, Object> args = Map.of("data_source_id", "ds1", "sql", "insert into t values (1)");
        ToolTestData write = new SqlExecution("insert", List.of(StatementResult.write(1)), null);
        run("svc", "u1", McpToolType.EXECUTE_SQL, args, counting(calls, write));
        run("svc", "u1", McpToolType.EXECUTE_SQL, args, counting(calls, write));
        assertEquals(2, calls.get());
    }

    @Test
    @DisplayName("A write clears cached SQL results so the next read sees fresh data")
    void writeInvalidatesSqlResults() {
        AtomicInteger calls = new AtomicInteger();
        run("svc", "u1", McpToolType.EXECUTE_SQL, SQL_ARGS, counting(calls, select(1)));
        run("svc", "u1", McpToolType.EXECUTE_SQL, Map.of("sql", "update t set c = 2"),
            () -> new SqlExecution("update", List.of(StatementResult.write(1)), null));
        ToolTestData fresh = select(2);
        assertSame(fresh, run("svc", "u1", McpToolType.EXECUTE_SQL, SQL_ARGS, counting(calls, fresh)));
        assertEquals(2, calls.get());
    }

    @Test
    @DisplayName("Failed statements and failed batch steps are not cached")
    void failuresNotCached() {
        AtomicInteger calls = new AtomicInteger();
        ToolTestData failed = new SqlExecution("select 1", List.of(StatementResult.selectFailure("boom")), null);
        run("svc", "u1", McpToolType.EXECUTE_SQL, SQL_ARGS, counting(calls, failed));
        run("svc", "u1", McpToolType.EXECUTE_SQL, SQL_ARGS, counting(calls, failed));
        assertEquals(2, calls.get());

        Map<String, Object> batchArgs = Map.of("statements", List.of("select 1", "select x"));
        ToolTestData batch = new BatchExecution(List.of(
            new BatchExecution.StepResult("select 1", true, (SqlExecution) select(1), null),
            new BatchExecution.StepResult("select x", false, null, "bad column")));
        run("svc", "u1", McpToolType.EXECUTE_QUERY_BATCH, batchArgs, counting(calls, batch));
        run("svc", "u1", McpToolType.EXECUTE_QUERY_BATCH, batchArgs, counting(calls, batch));
        assertEquals(4, calls.get());
    }

    @Test
    @DisplayName("Metadata update tools clear the metadata tier")
    void metadataUpdateInvalidatesMetadata() {
        AtomicInteger calls = new AtomicInteger();
        Map<String, Object> args = Map.of("table", "t_mo");
        ToolTestData listing = new TableListData(List.of(), List.of());
        run("svc", "u1", McpToolType.LIST_TABLES, args, counting(calls, listing));
        run("svc", "u1", McpToolType.LIST_TABLES, args, counting(calls, listing));
        assertEquals(1, calls.get());

        run("svc", "u1", McpToolType.UPDATE_TABLE_INFO, args, () -> null);
        run("svc", "u1", McpToolType.LIST_TABLES, args, counting(calls, listing));
        assertEquals(2, calls.get());
    }

    @Test
    @DisplayName("Uncached tool types always execute")
    void uncachedToolType() {
        AtomicInteger calls = new AtomicInteger();
        run("svc", "u1", McpToolType.UPSERT_TERM, Map.of(), counting(calls, null));
        run("svc", "u1", McpToolType.UPSERT_TERM, Map.of(), counting(calls, null));
        assertEquals(2, calls.get());
    }

    @Test
    @DisplayName("Concurrent identical calls share one execution")
    void concurrentCallsShareExecution() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        CountDownLatch release = new CountDownLatch(1);
        ToolTestData data = select(1);
        Supplier<ToolTestData> slow = () -> {
            calls.incrementAndGet();
            try {
                release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return data;
        };
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            Future<ToolTestData> first = pool.submit(() -> run("svc", "u1", McpToolType.EXECUTE_SQL, SQL_ARGS, slow));
            while (calls.get() == 0) {
                Thread.onSpinWait();
            }
            List<Future<ToolTestData>> others = List.of(
                pool.submit(() -> run("svc", "u1", McpToolType.EXECUTE_SQL, SQL_ARGS, slow)),
                pool.submit(() -> run("svc", "u1", McpToolType.EXECUTE_SQL, SQL_ARGS, slow)));
            Thread.sleep(100);
            release.countDown();
            assertSame(data, first.get(5, TimeUnit.SECONDS));
            for (Future<ToolTestData> other : others) {
                assertSame(data, other.get(5, TimeUnit.SECONDS));
            }
            assertEquals(1, calls.get());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("An exception is rethrown and not cached")
    void exceptionNotCached() {
        assertThrows(IllegalStateException.class, () -> run("svc", "u1", McpToolType.EXECUTE_SQL, SQL_ARGS,
            () -> { throw new IllegalStateException("db down"); }));
        ToolTestData data = select(1);
        assertSame(data, run("svc", "u1", McpToolType.EXECUTE_SQL, SQL_ARGS, () -> data));
    }

    @Test
    @DisplayName("Historical and today-covering metric results are both cached")
    void metricTiersCached() {
        String todayEnd = LocalDateTime.now().plusDays(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        for (String end : List.of("2020-01-01 00:00:00", todayEnd)) {
            AtomicInteger calls = new AtomicInteger();
            Map<String, Object> args = Map.of("metric", "m1", "end_time", end);
            ToolTestData data = new com.dati.mcp.server.pojo.MetricExecution(
                null, Map.of(), List.of(), (SqlExecution) select(1));
            run("svc", "u1", McpToolType.QUERY_METRIC, args, counting(calls, data));
            assertSame(data, run("svc", "u1", McpToolType.QUERY_METRIC, args, counting(calls, data)));
            assertEquals(1, calls.get(), end);
        }
    }

    @Test
    @DisplayName("A hit past 80% of its TTL is served stale and refreshed in the background")
    void staleHitRefreshes() {
        AtomicInteger calls = new AtomicInteger();
        ToolTestData old = select(1);
        ToolTestData fresh = select(2);
        run("svc", "u1", McpToolType.EXECUTE_SQL, SQL_ARGS, counting(calls, old));

        advanceSeconds(60);
        assertSame(old, run("svc", "u1", McpToolType.EXECUTE_SQL, SQL_ARGS, counting(calls, fresh)));
        assertEquals(1, calls.get());

        advanceSeconds(15);
        assertSame(old, run("svc", "u1", McpToolType.EXECUTE_SQL, SQL_ARGS, counting(calls, fresh)));
        assertEquals(2, calls.get());
        assertSame(fresh, run("svc", "u1", McpToolType.EXECUTE_SQL, SQL_ARGS, counting(calls, fresh)));
        assertEquals(2, calls.get());
    }

    @Test
    @DisplayName("A background refresh does not restore an entry cleared by a write")
    void refreshDoesNotResurrectClearedEntry() {
        AtomicInteger calls = new AtomicInteger();
        java.util.List<Runnable> queued = new java.util.ArrayList<>();
        ToolResultCache deferred = new ToolResultCache(now::get, queued::add);
        deferred.getOrExecute("svc", 1, "u1", McpToolType.EXECUTE_SQL, "execute_sql", SQL_ARGS, () -> select(1));
        advanceSeconds(80);
        deferred.getOrExecute("svc", 1, "u1", McpToolType.EXECUTE_SQL, "execute_sql", SQL_ARGS, () -> select(9));
        assertEquals(1, queued.size());

        deferred.getOrExecute("svc", 1, "u1", McpToolType.EXECUTE_SQL, "execute_sql", Map.of("sql", "update t"),
            () -> new SqlExecution("update", List.of(StatementResult.write(1)), null));
        queued.getFirst().run();

        ToolTestData fresh = select(2);
        assertSame(fresh, deferred.getOrExecute("svc", 1, "u1", McpToolType.EXECUTE_SQL, "execute_sql", SQL_ARGS,
            counting(calls, fresh)));
        assertEquals(1, calls.get());
    }

    @Test
    @DisplayName("A background refresh runs with the caller's request user")
    void refreshCarriesRequestUser() {
        com.dati.auth.authentication.User user = new com.dati.auth.authentication.User();
        user.setId("u1");
        java.util.concurrent.atomic.AtomicReference<String> seen = new java.util.concurrent.atomic.AtomicReference<>();
        java.util.List<Runnable> queued = new java.util.ArrayList<>();
        ToolResultCache deferred = new ToolResultCache(now::get, queued::add);
        Supplier<ToolTestData> loader = () -> {
            seen.set(com.dati.base.RequestContext.currentUserId());
            return select(1);
        };
        com.dati.base.RequestContext.setUser(user);
        try {
            deferred.getOrExecute("svc", 1, "u1", McpToolType.EXECUTE_SQL, "execute_sql", SQL_ARGS, loader);
            advanceSeconds(80);
            deferred.getOrExecute("svc", 1, "u1", McpToolType.EXECUTE_SQL, "execute_sql", SQL_ARGS, loader);
        } finally {
            com.dati.base.RequestContext.clear();
        }
        seen.set(null);
        Thread worker = new Thread(queued.getFirst());
        worker.start();
        assertDoesNotThrow(() -> worker.join(5_000));
        assertEquals("u1", seen.get());
    }

    private void advanceSeconds(long seconds) {
        now.addAndGet(TimeUnit.SECONDS.toNanos(seconds));
    }

    private ToolTestData run(String serviceId, String userId, McpToolType type,
                             Map<String, Object> args, Supplier<ToolTestData> loader) {
        return cache.getOrExecute(serviceId, 1, userId, type, type.name().toLowerCase(), args, loader);
    }

    private Supplier<ToolTestData> counting(AtomicInteger calls, ToolTestData data) {
        return () -> {
            calls.incrementAndGet();
            return data;
        };
    }

    private ToolTestData select(int value) {
        return new SqlExecution("select 1", List.of(StatementResult.select(
            List.of("c"), List.of(List.<Object>of(value)), false)), null);
    }

    private Map<String, Object> orderedMap() {
        return Map.of("a", "1", "b", "2");
    }

    private Map<String, Object> reversedMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("b", "2");
        map.put("a", "1");
        return map;
    }
}
