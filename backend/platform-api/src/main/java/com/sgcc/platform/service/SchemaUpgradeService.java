package com.sgcc.platform.service;

import jakarta.annotation.PostConstruct;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

/**
 * Keeps the demo schema aligned with the current Verkle-oriented implementation.
 * This avoids manual database cleanup when we evolve fields such as HD_i.
 */
@Service
@Order(0)
@RequiredArgsConstructor
public class SchemaUpgradeService {

    private static final String MYSQL_URL =
            "jdbc:mysql://127.0.0.1:3306/sgcc_platform?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai&characterEncoding=utf8";
    private static final String MYSQL_USERNAME = "sgcc_app";
    private static final String MYSQL_PASSWORD = "22b607b72faabc1d52785a3e7a841ac8";

    @PostConstruct
    public void ensureVerkleColumns() {
        try (Connection connection = DriverManager.getConnection(MYSQL_URL, MYSQL_USERNAME, MYSQL_PASSWORD)) {
            ensureHdValueColumn(connection);
            backfillHdValue(connection);
        } catch (SQLException ex) {
            throw new IllegalStateException("failed to upgrade mysql schema for verkle fields", ex);
        }
    }

    private void ensureHdValueColumn(Connection connection) throws SQLException {
        if (columnExists(connection, "data_resource", "hd_value")) {
            return;
        }

        try (PreparedStatement statement = connection.prepareStatement(
                "alter table data_resource add column hd_value varchar(128) null after cid")) {
            statement.execute();
        }
    }

    private void backfillHdValue(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "update data_resource set hd_value = package_hash where hd_value is null or hd_value = ''")) {
            statement.executeUpdate();
        }
    }

    private boolean columnExists(Connection connection, String tableName, String columnName) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                select count(*)
                from information_schema.columns
                where table_schema = database()
                  and table_name = ?
                  and column_name = ?
                """)) {
            statement.setString(1, tableName);
            statement.setString(2, columnName);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1) > 0;
            }
        }
    }
}
