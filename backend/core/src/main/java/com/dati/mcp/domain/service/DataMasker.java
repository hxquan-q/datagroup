package com.dati.mcp.domain.service;

import java.util.List;
import java.util.regex.Pattern;

/** Masks sensitive sample values by column-name pattern. */
public final class DataMasker {

    private record Rule(Pattern pattern, MaskType type) {
    }

    private enum MaskType {
        PHONE, EMAIL, IDCARD, BANKCARD, SECRET, PARTIAL
    }

    private static final List<Rule> RULES = List.of(
        new Rule(Pattern.compile("(?i).*(password|passwd|pwd|secret|token|apikey|api_key).*"), MaskType.SECRET),
        new Rule(Pattern.compile("(?i).*(phone|mobile|tel).*", 0), MaskType.PHONE),
        new Rule(Pattern.compile("(?i).*mail.*"), MaskType.EMAIL),
        new Rule(Pattern.compile("(?i).*(idcard|id_card|identity).*"), MaskType.IDCARD),
        new Rule(Pattern.compile("(?i).*(bankcard|bank_card|card_no).*"), MaskType.BANKCARD),
        new Rule(Pattern.compile("(?i).*(name|contact).*"), MaskType.PARTIAL)
    );

    private DataMasker() {
    }

    /** Whether any masking rule matches the column. */
    public static boolean isSensitive(String columnName) {
        if (columnName == null) {
            return false;
        }
        return RULES.stream().anyMatch(rule -> rule.pattern().matcher(columnName).matches());
    }

    /** Returns the masked value, or the original value when no rule matches or value is null. */
    public static Object mask(String columnName, Object value) {
        if (value == null || columnName == null) {
            return value;
        }
        String text = String.valueOf(value);
        if (text.isEmpty()) {
            return value;
        }
        for (Rule rule : RULES) {
            if (rule.pattern().matcher(columnName).matches()) {
                return apply(rule.type(), text);
            }
        }
        return value;
    }

    private static String apply(MaskType type, String text) {
        return switch (type) {
            case PHONE -> text.length() >= 11
                ? text.substring(0, 3) + "****" + text.substring(text.length() - 4)
                : text.length() >= 5 ? text.substring(0, 2) + "****" : "****";
            case EMAIL -> {
                int at = text.indexOf('@');
                yield at > 0 ? text.charAt(0) + "***" + text.substring(at) : "***";
            }
            case IDCARD -> text.length() >= 11
                ? text.substring(0, 3) + "***********" + text.substring(text.length() - 4) : "******";
            case BANKCARD -> text.length() > 4 ? "************" + text.substring(text.length() - 4) : "******";
            case SECRET -> "******";
            case PARTIAL -> text.length() <= 1 ? "*" : text.charAt(0) + "*".repeat(Math.max(1, text.length() - 1));
        };
    }
}
