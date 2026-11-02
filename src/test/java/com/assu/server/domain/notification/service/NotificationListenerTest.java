package com.assu.server.domain.notification.service;

import com.assu.server.domain.notification.dto.NotificationMessageDTO;
import com.assu.server.infra.firebase.FcmClient;
import com.rabbitmq.client.Channel;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationListenerTest {

    private static final long TAG = 7L;
    private static final Long OUTBOX_ID = 1L;
    private static final Long RECEIVER_ID = 10L;

    private FcmClient fcmClient;
    private OutboxStatusService outboxStatus;
    private Channel channel;
    private NotificationListener listener;

    @BeforeEach
    void setUp() {
        fcmClient = mock(FcmClient.class);
        outboxStatus = mock(OutboxStatusService.class);
        channel = mock(Channel.class);
        listener = new NotificationListener(fcmClient, outboxStatus, new SimpleMeterRegistry());
        when(outboxStatus.claimForSending(OUTBOX_ID)).thenReturn(true);
    }

    @Test
    void onMessage_whenAllTokensFailWithRetryableErrors_thenSchedulesRetry() throws Exception {
        when(fcmClient.sendToMemberId(anyLong(), any(), any(), any()))
                .thenReturn(new FcmClient.FcmResult(0, 2, 2, List.of()));

        listener.onMessage(message(), channel, TAG);

        verify(outboxStatus).scheduleRetry(OUTBOX_ID);
        verify(outboxStatus, never()).markSent(OUTBOX_ID);
        verify(channel).basicAck(TAG, false);
    }

    @Test
    void onMessage_whenSomeTokensSucceed_thenMarksSentWithoutRetry() throws Exception {
        when(fcmClient.sendToMemberId(anyLong(), any(), any(), any()))
                .thenReturn(new FcmClient.FcmResult(1, 1, 1, List.of()));

        listener.onMessage(message(), channel, TAG);

        verify(outboxStatus).markSent(OUTBOX_ID);
        verify(outboxStatus, never()).scheduleRetry(OUTBOX_ID);
    }

    @Test
    void onMessage_whenAlreadyClaimedByOtherConsumer_thenAcksWithoutSending() throws Exception {
        when(outboxStatus.claimForSending(OUTBOX_ID)).thenReturn(false);

        listener.onMessage(message(), channel, TAG);

        verify(fcmClient, never()).sendToMemberId(anyLong(), any(), any(), any());
        verify(channel).basicAck(TAG, false);
    }

    private NotificationMessageDTO message() {
        return new NotificationMessageDTO(String.valueOf(OUTBOX_ID), RECEIVER_ID, "title", "body", Map.of());
    }
}
