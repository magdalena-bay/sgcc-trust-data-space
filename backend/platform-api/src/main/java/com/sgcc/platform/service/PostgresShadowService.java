package com.sgcc.platform.service;

import com.sgcc.platform.config.AppProperties;
import com.sgcc.platform.entity.DataResourceEntity;
import jakarta.annotation.PostConstruct;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PostgresShadowService {

    private final AppProperties appProperties;

    @PostConstruct
    public void init() {
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     create table if not exists shadow_audit_log (
                       id bigserial primary key,
                       event_type varchar(32) not null,
                       data_id varchar(128) not null,
                       region varchar(64),
                       detail text,
                       created_at timestamp not null
                     )
                     """)) {
            statement.execute();
        } catch (SQLException ex) {
            throw new IllegalStateException("failed to initialize postgres shadow table", ex);
        }
    }

    public void logUpload(DataResourceEntity resource, String detail) {
        insert("UPLOAD", resource.getDataId(), resource.getRegion(), detail);
    }

    public void logAccess(String dataId, String region, String detail) {
        insert("ACCESS", dataId, region, detail);
    }

    private void insert(String eventType, String dataId, String region, String detail) {
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     insert into shadow_audit_log(event_type, data_id, region, detail, created_at)
                     values (?, ?, ?, ?, ?)
                     """)) {
            statement.setString(1, eventType);
            statement.setString(2, dataId);
            statement.setString(3, region);
            statement.setString(4, detail);
            statement.setObject(5, LocalDateTime.now());
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("failed to write postgres shadow log", ex);
        }
    }

    private Connection openConnection() throws SQLException {
        return DriverManager.getConnection(
                appProperties.getPostgres().getUrl(),
                appProperties.getPostgres().getUsername(),
                appProperties.getPostgres().getPassword()
        );
    }
}
