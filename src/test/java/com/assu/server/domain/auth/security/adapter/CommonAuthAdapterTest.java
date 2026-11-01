package com.assu.server.domain.auth.security.adapter;

import com.assu.server.domain.auth.entity.CommonAuth;
import com.assu.server.domain.auth.repository.CommonAuthRepository;
import com.assu.server.domain.auth.service.WithdrawalService;
import com.assu.server.domain.member.entity.Member;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommonAuthAdapterTest {

    private static final String EMAIL = "partner@assu.com";

    @Mock
    private CommonAuthRepository commonAuthRepository;

    @Mock
    private WithdrawalService withdrawalService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private CommonAuthAdapter commonAuthAdapter;

    @Test
    void loadMember_whenWithdrawn_thenDelegatesRestoreToWithdrawalService() {
        // given
        Member member = givenMember(true);

        // when
        Member result = commonAuthAdapter.loadMember(EMAIL);

        // then
        assertThat(result).isSameAs(member);
        verify(withdrawalService).restoreMember(member);
    }

    @Test
    void loadMember_whenNotWithdrawn_thenDoesNotRestore() {
        // given
        Member member = givenMember(false);

        // when
        commonAuthAdapter.loadMember(EMAIL);

        // then
        verify(withdrawalService, never()).restoreMember(member);
    }

    private Member givenMember(boolean isWithdrawn) {
        CommonAuth commonAuth = mock(CommonAuth.class);
        Member member = mock(Member.class);
        when(commonAuthRepository.findByEmail(EMAIL)).thenReturn(Optional.of(commonAuth));
        when(commonAuth.getMember()).thenReturn(member);
        when(member.getCommonAuth()).thenReturn(commonAuth);
        when(member.isWithdrawn()).thenReturn(isWithdrawn);
        return member;
    }
}
