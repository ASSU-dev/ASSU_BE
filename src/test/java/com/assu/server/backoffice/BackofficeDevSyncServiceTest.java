package com.assu.server.backoffice;

import com.assu.server.domain.backoffice.service.BackofficeDevSyncService;
import com.assu.server.global.apiPayload.code.status.ErrorStatus;
import com.assu.server.global.exception.GeneralException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BackofficeDevSyncServiceTest {

    @InjectMocks
    private BackofficeDevSyncService backofficeDevSyncService;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @ParameterizedTest
    @ValueSource(strings = {"", "assu_db"})
    void syncToDev_whenTargetIsBlankOrSameAsSource_thenThrowsWithoutTouchingSchema(String targetSchema) {
        // given
        ReflectionTestUtils.setField(backofficeDevSyncService, "targetSchema", targetSchema);
        when(jdbcTemplate.queryForObject("SELECT DATABASE()", String.class)).thenReturn("assu_db");

        // when & then
        assertThatThrownBy(() -> backofficeDevSyncService.syncToDev())
                .isInstanceOf(GeneralException.class)
                .extracting("code")
                .isEqualTo(ErrorStatus.INVALID_DEV_SYNC_TARGET);
        verify(jdbcTemplate, never()).execute(any(ConnectionCallback.class));
    }

    @Test
    void syncToDev_whenAlreadySyncing_thenThrowsWithoutTouchingSchema() {
        // given
        ReflectionTestUtils.setField(backofficeDevSyncService, "targetSchema", "assu_dev_db");
        ReflectionTestUtils.setField(backofficeDevSyncService, "syncing", new AtomicBoolean(true));
        when(jdbcTemplate.queryForObject("SELECT DATABASE()", String.class)).thenReturn("assu_db");

        // when & then
        assertThatThrownBy(() -> backofficeDevSyncService.syncToDev())
                .isInstanceOf(GeneralException.class)
                .extracting("code")
                .isEqualTo(ErrorStatus.DEV_SYNC_IN_PROGRESS);
        verify(jdbcTemplate, never()).execute(any(ConnectionCallback.class));
    }
}
