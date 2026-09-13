package com.spectrace.validation.infrastructure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

final class JdbcValueMapping {

    private JdbcValueMapping() {
    }

    static boolean readFlag(ResultSet resultSet, String column) throws SQLException {
        String value = resultSet.getString(column);
        if ("Y".equals(value)) {
            return true;
        }
        if ("N".equals(value)) {
            return false;
        }
        throw new IllegalStateException("Unsupported Y/N flag in " + column + ": " + value);
    }

    static String writeFlag(boolean value) {
        return value ? "Y" : "N";
    }

    static LocalDate readDate(ResultSet resultSet, String column) throws SQLException {
        java.sql.Date value = resultSet.getDate(column);
        return value == null ? null : value.toLocalDate();
    }

    static Instant readUtcDateTime(ResultSet resultSet, String column) throws SQLException {
        LocalDateTime value = resultSet.getObject(column, LocalDateTime.class);
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }

    static LocalDateTime writeUtcDateTime(Instant value) {
        return LocalDateTime.ofInstant(value, ZoneOffset.UTC);
    }
}
