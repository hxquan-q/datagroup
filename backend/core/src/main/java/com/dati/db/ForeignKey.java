package com.dati.db;

public record ForeignKey(String name, String column, String refSchema, String refTable, String refColumn,
                         String onUpdate, String onDelete, short keySeq) {
}
