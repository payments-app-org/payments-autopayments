package com.paymentsapp.autopayments.common;

import java.sql.ResultSet;
import java.sql.SQLException;

// Чтение enum-колонок: значение хранится именем константы, null — не заполнено
public final class Columns {

    private Columns() {
    }

    public static <E extends Enum<E>> E enumOrNull(ResultSet rs, String column, Class<E> type) throws SQLException {
        String value = rs.getString(column);
        return value == null ? null : Enum.valueOf(type, value);
    }
}
