package com.assu.server.domain.certification.config;

import com.assu.server.domain.auth.exception.CustomAuthException;
import com.assu.server.domain.auth.security.jwt.JwtUtil;
import com.assu.server.domain.chat.repository.ChatRepository;
import com.assu.server.domain.common.enums.UserRole;
import com.assu.server.domain.member.entity.Member;
import com.assu.server.global.apiPayload.code.status.ErrorStatus;
import com.assu.server.global.exception.GeneralException;
import com.assu.server.global.util.PrincipalDetails;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StompAuthChannelInterceptorTest {

    private static final String TOKEN = "access-token";
    private static final String BEARER = "Bearer " + TOKEN;

    @Mock
    private JwtUtil jwtUtil;

    private static final Long MEMBER_ID = 10L;
    private static final Long ROOM_ID = 7L;

    @Mock
    private ChatRepository chatRepository;

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
                .isInstanceOf(GeneralException.class)
                .extracting(ex -> ((GeneralException) ex).getCode())
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
                .isInstanceOf(GeneralException.class)
                .extracting(ex -> ((GeneralException) ex).getCode())
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

    @Test
    void preSend_whenParticipantSubscribesToChatRoom_thenAllows() {
        // given
        when(chatRepository.existsByIdAndParticipantId(ROOM_ID, MEMBER_ID)).thenReturn(true);
        Message<byte[]> message = subscribeMessage("/sub/chat/" + ROOM_ID, authenticationOf(UserRole.ADMIN));

        // when
        Message<?> result = interceptor.preSend(message, channel);

        // then
        assertThat(result).isSameAs(message);
    }

    @Test
    void preSend_whenNonParticipantSubscribesToChatRoom_thenRejects() {
        // given
        when(chatRepository.existsByIdAndParticipantId(ROOM_ID, MEMBER_ID)).thenReturn(false);
        Message<byte[]> message = subscribeMessage("/sub/chat/" + ROOM_ID, authenticationOf(UserRole.PARTNER));

        // when & then
        assertForbidden(message);
    }

    @Test
    void preSend_whenSubscribesToOwnChatList_thenAllows() {
        // given
        Message<byte[]> message = subscribeMessage("/sub/chat/list/" + MEMBER_ID, authenticationOf(UserRole.ADMIN));

        // when
        Message<?> result = interceptor.preSend(message, channel);

        // then
        assertThat(result).isSameAs(message);
        verifyNoInteractions(chatRepository);
    }

    @Test
    void preSend_whenSubscribesToOtherMembersChatList_thenRejects() {
        // given
        Message<byte[]> message = subscribeMessage("/sub/chat/list/999", authenticationOf(UserRole.ADMIN));

        // when & then
        assertForbidden(message);
    }

    @Test
    void preSend_whenStudentSubscribesToCertificationProgress_thenAllows() {
        // given
        Message<byte[]> message = subscribeMessage("/certification/progress/100", authenticationOf(UserRole.STUDENT));

        // when
        Message<?> result = interceptor.preSend(message, channel);

        // then
        assertThat(result).isSameAs(message);
    }

    @Test
    void preSend_whenNonStudentSubscribesToCertificationProgress_thenRejects() {
        // given
        Message<byte[]> message = subscribeMessage("/certification/progress/100", authenticationOf(UserRole.PARTNER));

        // when & then
        assertForbidden(message);
    }

    @Test
    void preSend_whenSubscribesToUserErrorQueue_thenAllows() {
        // given
        Message<byte[]> message = subscribeMessage("/user/queue/errors", authenticationOf(UserRole.ADMIN));

        // when
        Message<?> result = interceptor.preSend(message, channel);

        // then
        assertThat(result).isSameAs(message);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/sub/chat/*", "/sub/**", "/sub/chat/list/*", "/sub/chat/7/extra", "/sub/other/7",
            "/queue/chat/7", "/certification/chat/7", "/certification/progress/*", "/user/queue/other"})
    void preSend_whenSubscribesToPatternOrUnknownDestination_thenRejects(String destination) {
        // given
        Message<byte[]> message = subscribeMessage(destination, authenticationOf(UserRole.STUDENT));

        // when & then
        assertForbidden(message);
        verifyNoInteractions(chatRepository);
    }

    @Test
    void preSend_whenSubscribeWithoutAuthenticatedUser_thenRejects() {
        // given
        Message<byte[]> message = subscribeMessage("/sub/chat/" + ROOM_ID, null);

        // when & then
        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(GeneralException.class)
                .extracting(ex -> ((GeneralException) ex).getCode())
                .isEqualTo(ErrorStatus._UNAUTHORIZED);
        verifyNoInteractions(chatRepository);
    }

    private void assertForbidden(Message<byte[]> message) {
        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(GeneralException.class)
                .extracting(ex -> ((GeneralException) ex).getCode())
                .isEqualTo(ErrorStatus._FORBIDDEN);
    }

    private Authentication authenticationOf(UserRole role) {
        PrincipalDetails principal = PrincipalDetails.builder()
                .role(role)
                .member(Member.builder().id(MEMBER_ID).role(role).build())
                .build();
        return new UsernamePasswordAuthenticationToken(principal, null, List.of());
    }

    private Message<byte[]> subscribeMessage(String destination, Authentication authentication) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination(destination);
        accessor.setSubscriptionId("sub-0");
        accessor.setUser(authentication);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
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
