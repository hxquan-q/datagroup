package com.dati.mcp.domain.service;

import net.sf.jsqlparser.expression.BinaryExpression;
import net.sf.jsqlparser.expression.DateValue;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.StringValue;
import net.sf.jsqlparser.expression.TimestampValue;
import net.sf.jsqlparser.expression.operators.relational.Between;
import net.sf.jsqlparser.expression.operators.relational.ComparisonOperator;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.Statements;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Time-window enforcement for large tables: a SELECT touching a rule-marked table must
 * filter its configured time column with a bounded range in the same statement's WHERE clause.
 */
public final class TimeWindowEnforcer {

    public record Rule(String tableName, String timeColumn, Integer maxRangeDays) {
    }

    public record Violation(String table, String timeColumn, String detail) {
    }

    private TimeWindowEnforcer() {
    }

    /** Returns violations for each rule-marked table whose query lacks a bounded time filter. */
    public static List<Violation> check(String sql, Map<String, Rule> rulesByTable) {
        List<Violation> violations = new ArrayList<>();
        if (rulesByTable == null || rulesByTable.isEmpty()) {
            return violations;
        }
        List<Expression> wheres = new ArrayList<>();
        try {
            Statements statements = CCJSqlParserUtil.parseStatements(sql);
            for (Statement statement : statements.getStatements()) {
                collectWheres(statement, wheres);
            }
        } catch (Exception e) {
            return violations;
        }
        for (Rule rule : rulesByTable.values()) {
            if (!hasBoundedFilter(wheres, rule)) {
                String detail = rule.maxRangeDays() != null
                    ? "a bounded range of at most " + rule.maxRangeDays() + " days"
                    : "a bounded range (both lower and upper bounds)";
                violations.add(new Violation(rule.tableName(), rule.timeColumn(), detail));
            }
        }
        return violations;
    }

    private static void collectWheres(Statement statement, List<Expression> out) {
        if (statement instanceof net.sf.jsqlparser.statement.update.Update update) {
            if (update.getWhere() != null) {
                out.add(update.getWhere());
            }
            return;
        }
        if (statement instanceof net.sf.jsqlparser.statement.delete.Delete delete) {
            if (delete.getWhere() != null) {
                out.add(delete.getWhere());
            }
            return;
        }
        if (statement instanceof PlainSelect plain) {
            if (plain.getWhere() != null) {
                out.add(plain.getWhere());
            }
            if (plain.getFromItem() instanceof Select sub) {
                collectWheres(sub, out);
            }
            if (plain.getJoins() != null) {
                for (net.sf.jsqlparser.statement.select.Join join : plain.getJoins()) {
                    if (join.getRightItem() instanceof Select sub) {
                        collectWheres(sub, out);
                    }
                }
            }
        } else if (statement instanceof net.sf.jsqlparser.statement.select.ParenthesedSelect parenthesed) {
            collectWheres(parenthesed.getSelect(), out);
        } else if (statement instanceof net.sf.jsqlparser.statement.select.SetOperationList composite) {
            for (Select component : composite.getSelects()) {
                collectWheres(component, out);
            }
        }
        if (statement instanceof Select select && select.getWithItemsList() != null) {
            for (net.sf.jsqlparser.statement.select.WithItem<?> withItem : select.getWithItemsList()) {
                if (withItem.getSelect() != null) {
                    collectWheres(withItem.getSelect(), out);
                }
            }
        }
    }

    private static boolean hasBoundedFilter(List<Expression> wheres, Rule rule) {
        String timeColumn = rule.timeColumn();
        boolean hasLower = false;
        boolean hasUpper = false;
        long lowerSec = Long.MIN_VALUE;
        long upperSec = Long.MIN_VALUE;
        for (Expression where : wheres) {
            long[] found = scan(where, timeColumn);
            if (found[0] == 1) {
                hasLower = true;
                if (found[2] != Long.MIN_VALUE) {
                    lowerSec = lowerSec == Long.MIN_VALUE ? found[2] : Math.max(lowerSec, found[2]);
                }
            }
            if (found[1] == 1) {
                hasUpper = true;
                if (found[3] != Long.MIN_VALUE) {
                    upperSec = upperSec == Long.MIN_VALUE ? found[3] : Math.min(upperSec, found[3]);
                }
            }
        }
        if (!hasLower || !hasUpper) {
            return false;
        }
        if (rule.maxRangeDays() == null || lowerSec == Long.MIN_VALUE || upperSec == Long.MIN_VALUE) {
            return true;
        }
        long days = (upperSec - lowerSec) / 86400;
        return days <= rule.maxRangeDays();
    }

    /**
     * Scans an expression tree for comparisons on the time column.
     * Returns [hasLower(1/0), hasUpper(1/0), lowerEpochSecOrMIN, upperEpochSecOrMIN].
     */
    private static long[] scan(Expression expr, String timeColumn) {
        long[] result = {0, 0, Long.MIN_VALUE, Long.MIN_VALUE};
        scanInto(expr, timeColumn, result);
        return result;
    }

    private static void scanInto(Expression expr, String timeColumn, long[] result) {
        if (expr == null) {
            return;
        }
        if (expr instanceof ComparisonOperator cmp) {
            if (isTimeColumn(cmp.getLeftExpression(), timeColumn)) {
                String op = cmp.getStringExpression();
                Long literal = literalEpochSeconds(cmp.getRightExpression());
                if (">=".equals(op) || ">".equals(op)) {
                    result[0] = 1;
                    if (literal != null) {
                        result[2] = result[2] == Long.MIN_VALUE ? literal : Math.max(result[2], literal);
                    }
                } else if ("<=".equals(op) || "<".equals(op)) {
                    result[1] = 1;
                    if (literal != null) {
                        result[3] = result[3] == Long.MIN_VALUE ? literal : Math.min(result[3], literal);
                    }
                }
            }
        } else if (expr instanceof net.sf.jsqlparser.expression.operators.relational.Between between) {
            if (between.isNot()) {
                return;
            }
            if (isTimeColumn(between.getLeftExpression(), timeColumn)) {
                result[0] = 1;
                result[1] = 1;
                Long lo = literalEpochSeconds(between.getBetweenExpressionStart());
                Long hi = literalEpochSeconds(between.getBetweenExpressionEnd());
                if (lo != null) {
                    result[2] = lo;
                }
                if (hi != null) {
                    result[3] = hi;
                }
            }
        }
        if (expr instanceof net.sf.jsqlparser.expression.operators.relational.ParenthesedExpressionList<?> parens) {
            scanInto(parens.get(0) instanceof Expression e ? e : null, timeColumn, result);
            return;
        }
        if (expr instanceof net.sf.jsqlparser.expression.operators.conditional.OrExpression) {
            // bounds under OR do not constrain every row, so they never qualify
            return;
        }
        if (expr instanceof BinaryExpression binary) {
            scanInto(binary.getLeftExpression(), timeColumn, result);
            scanInto(binary.getRightExpression(), timeColumn, result);
        }
    }

    private static boolean isTimeColumn(Expression expr, String timeColumn) {
        if (!(expr instanceof Column column)) {
            return false;
        }
        String name = column.getColumnName();
        return name != null && name.equalsIgnoreCase(timeColumn);
    }

    /** Epoch seconds for a literal date/datetime constant, or null for non-literals. */
    private static Long literalEpochSeconds(Expression expr) {
        String text = null;
        if (expr instanceof StringValue sv) {
            text = sv.getValue();
        } else if (expr instanceof DateValue dv) {
            return dv.getValue().toLocalDate().atStartOfDay().toEpochSecond(java.time.ZoneOffset.UTC);
        } else if (expr instanceof TimestampValue tv) {
            return tv.getValue().toLocalDateTime().toEpochSecond(java.time.ZoneOffset.UTC);
        } else if (expr instanceof LongValue) {
            return null;
        }
        if (text == null || text.isBlank()) {
            return null;
        }
        text = text.trim();
        try {
            if (text.length() == 10) {
                return LocalDate.parse(text, DateTimeFormatter.ISO_LOCAL_DATE)
                    .atStartOfDay().toEpochSecond(java.time.ZoneOffset.UTC);
            }
            String normalized = text.replace('T', ' ');
            if (normalized.length() >= 19) {
                return LocalDateTime.parse(normalized.substring(0, 19),
                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                    .toEpochSecond(java.time.ZoneOffset.UTC);
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }

    /** Builds the rejection message for violations. */
    public static String rejectionMessage(List<Violation> violations) {
        StringBuilder sb = new StringBuilder("Query rejected by time-window enforcement: ");
        for (int i = 0; i < violations.size(); i++) {
            Violation v = violations.get(i);
            if (i > 0) {
                sb.append("; ");
            }
            sb.append("table ").append(v.table()).append(" requires filtering on ")
                .append(v.timeColumn()).append(" with ").append(v.detail());
        }
        sb.append(". Add a bounded time filter, e.g. WHERE <time_col> >= 'YYYY-MM-DD' AND <time_col> < 'YYYY-MM-DD'.");
        return sb.toString();
    }
}
