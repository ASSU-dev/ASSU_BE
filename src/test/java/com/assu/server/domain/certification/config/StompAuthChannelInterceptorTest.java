package com.assu.server.domain.certification.config;

import com.assu.server.domain.auth.exception.CustomAuthException;
import com.assu.server.domain.auth.security.jwt.JwtUtil;
import com.assu.server.global.apiPayload.code.status.ErrorStatus;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.core.Authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StompAuthChannelInterceptorTest {

    private static final String TOKEN = "access-token";
    private static final String BEARER = "Bearer " + TOKEN;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private MessageChannel channel;

    @InjectMocks
    private StompAuthChannelInterceptor interceptor;

    @Test
    void preSend_whenConnectWithoutAuthorization_thenRejects() {
        // given
        Message<byte[]> message = connectMessage(null);

        // when & then
        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(CustomAuthException.class)
                .extracting(ex -> ((CustomAuthException) ex).getCode())
                .isEqualTo(ErrorStatus.JWT_TOKEN_NOT_RECEIVED);
    }

    @Test
    void preSend_whenTokenRevokedByWithdrawal_thenRejects() {
        // given
        Claims claims = mock(Claims.class);
        when(jwtUtil.getTokenFromHeader(BEARER)).thenReturn(TOKEN);
        when(jwtUtil.validateToken(TOKEN)).thenReturn(claims);
        doThrow(new CustomAuthException(ErrorStatus.JWT_ACCESS_TOKEN_REVOKED))
                .when(jwtUtil).assertNotRevoked(claims);

        // when & then
        assertThatThrownBy(() -> interceptor.preSend(connectMessage(BEARER), channel))
                .isInstanceOf(CustomAuthException.class)
                .extracting(ex -> ((CustomAuthException) ex).getCode())
                .isEqualTo(ErrorStatus.JWT_ACCESS_TOKEN_REVOKED);
        verify(jwtUtil, never()).getAuthentication(TOKEN);
    }

    @Test
    void preSend_whenValidToken_thenSetsUser() {
        // given
        Claims claims = mock(Claims.class);
        Authentication authentication = mock(Authentication.class);
        when(jwtUtil.getTokenFromHeader(BEARER)).thenReturn(TOKEN);
        when(jwtUtil.validateToken(TOKEN)).thenReturn(claims);
        when(jwtUtil.getAuthentication(TOKEN)).thenReturn(authentication);

        // when
        Message<?> result = interceptor.preSend(connectMessage(BEARER), channel);

        // then
        verify(jwtUtil).assertNotBlacklisted(TOKEN);
        verify(jwtUtil).assertAudience(claims, JwtUtil.AUD_APP);
        assertThat(StompHeaderAccessor.wrap(result).getUser()).isSameAs(authentication);
    }

    private Message<byte[]> connectMessage(String authorization) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        if (authorization != null) {
            accessor.addNativeHeader("Authorization", authorization);
        }
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
