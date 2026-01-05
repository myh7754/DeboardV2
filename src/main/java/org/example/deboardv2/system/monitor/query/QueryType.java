package org.example.deboardv2.system.monitor.query;

public enum QueryType {
    SELECT,
    UPDATE,
    DELETE,
    INSERT,
    UNKNOWN;

    public static QueryType from(String sql) {
        if (sql == null || sql.isBlank()) {
            return UNKNOWN;
        }
        String upperCaseSql = sql.toUpperCase().trim();
        if (upperCaseSql.startsWith(SELECT.name())) return SELECT;
        if (upperCaseSql.startsWith(INSERT.name())) return INSERT;
        if (upperCaseSql.startsWith(UPDATE.name())) return UPDATE;
        if (upperCaseSql.startsWith(DELETE.name())) return DELETE;
        return UNKNOWN;
    }
}
