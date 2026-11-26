package com.dati.db;

public record Column(String name, String type, String comment, boolean primaryKey, boolean uniqueKey) {
    public Column(String name, String type, String comment) {
        this(name, type, comment, false, false);
    }
}
