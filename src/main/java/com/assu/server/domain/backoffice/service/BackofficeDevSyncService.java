package com.assu.server.domain.backoffice.service;

import com.assu.server.domain.backoffice.dto.BackofficeDevSyncResponseDTO;
import com.assu.server.global.apiPayload.code.status.ErrorStatus;
import com.assu.server.global.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

// DDL은 암묵적 커밋이 발생해 트랜잭션으로 묶을 수 없으므로 @Transactional을 적용하지 않는다.
@Service
@RequiredArgsConstructor
public class BackofficeDevSyncService {

    private static final List<String> TABLES_TO_CLEAR = List.of("device_token", "notification_outbox");

    private final JdbcTemplate jdbcTemplate;

    @Value("${dev-sync.target-schema:}")
    private String targetSchema;

    public BackofficeDevSyncResponseDTO syncToDev() {
        String sourceSchema = jdbcTemplate.queryForObject("SELECT DATABASE()", String.class);
        if (targetSchema.isBlank() || targetSchema.equals(sourceSchema)) {
            throw new GeneralException(ErrorStatus.INVALID_DEV_SYNC_TARGET);
        }

        List<String> sourceTables = findObjects(sourceSchema, "BASE TABLE");
        List<String> sourceSequences = findObjects(sourceSchema, "SEQUENCE");
        List<String> targetObjects = new ArrayList<>(findObjects(targetSchema, "BASE TABLE"));
        targetObjects.addAll(findObjects(targetSchema, "SEQUENCE"));

        jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
            try (Statement statement = connection.createStatement()) {
                statement.execute("SET FOREIGN_KEY_CHECKS = 0");
                try {
                    for (String object : targetObjects) {
                        statement.execute("DROP TABLE " + qualify(targetSchema, object));
                    }
                    for (String table : sourceTables) {
                        statement.execute(toTargetCreateTable(statement, sourceSchema, table));
                        statement.execute("INSERT INTO " + qualify(targetSchema, table) + " SELECT * FROM " + qualify(sourceSchema, table));
                    }
                    for (String sequence : sourceSequences) {
                        statement.execute("CREATE TABLE " + qualify(targetSchema, sequence) + " LIKE " + qualify(sourceSchema, sequence));
                        statement.execute("INSERT INTO " + qualify(targetSchema, sequence) + " SELECT * FROM " + qualify(sourceSchema, sequence));
                    }
                    for (String table : TABLES_TO_CLEAR) {
                        if (sourceTables.contains(table)) {
                            statement.execute("TRUNCATE TABLE " + qualify(targetSchema, table));
                        }
                    }
                } finally {
                    statement.execute("SET FOREIGN_KEY_CHECKS = 1");
                }
            }
            return null;
        });

        List<String> copiedObjects = new ArrayList<>(sourceTables);
        copiedObjects.addAll(sourceSequences);
        return new BackofficeDevSyncResponseDTO(sourceSchema, targetSchema, copiedObjects);
    }

    private List<String> findObjects(String schema, String tableType) {
        return jdbcTemplate.queryForList(
                "SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA = ? AND TABLE_TYPE = ?",
                String.class,
                schema,
                tableType
        );
    }

    // CREATE TABLE LIKE는 FK를 복사하지 않으므로 원본 DDL을 사용한다. 스키마 없는 FK 참조는 생성되는 테이블의 스키마로 해석된다.
    private String toTargetCreateTable(Statement statement, String sourceSchema, String table) throws SQLException {
        String ddl;
        try (ResultSet resultSet = statement.executeQuery("SHOW CREATE TABLE " + qualify(sourceSchema, table))) {
            resultSet.next();
            ddl = resultSet.getString(2);
        }
        String header = "CREATE TABLE `" + table + "`";
        if (!ddl.startsWith(header)) {
            throw new GeneralException(ErrorStatus._INTERNAL_SERVER_ERROR);
        }
        return "CREATE TABLE " + qualify(targetSchema, table) + ddl.substring(header.length());
    }

    private String qualify(String schema, String table) {
        return "`" + schema + "`.`" + table + "`";
    }
}
