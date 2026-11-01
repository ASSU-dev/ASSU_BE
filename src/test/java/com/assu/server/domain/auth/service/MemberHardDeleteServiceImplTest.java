package com.assu.server.domain.auth.service;

import com.assu.server.domain.member.entity.Member;
import com.assu.server.domain.member.repository.MemberRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemberHardDeleteServiceImplTest {

    private static final Long MEMBER_ID = 1L;
    private static final LocalDateTime CUTOFF = LocalDateTime.of(2026, 10, 1, 2, 0);

    @Mock
    private MemberRepository memberRepository;

    @InjectMocks
    private MemberHardDeleteServiceImpl memberHardDeleteService;

    @Test
    void hardDeleteMember_whenRestoredAfterTargetSelection_thenSkips() {
        // given
        Member member = givenLockedMember(null);

        // when
        boolean deleted = memberHardDeleteService.hardDeleteMember(MEMBER_ID, CUTOFF);

        // then
        assertThat(deleted).isFalse();
        verify(memberRepository, never()).delete(any());
    }

    @Test
    void hardDeleteMember_whenWithdrawnAgainAfterCutoff_thenSkips() {
        // given
        Member member = givenLockedMember(CUTOFF.plusDays(1));

        // when
        boolean deleted = memberHardDeleteService.hardDeleteMember(MEMBER_ID, CUTOFF);

        // then
        assertThat(deleted).isFalse();
        verify(memberRepository, never()).delete(member);
    }

    private Member givenLockedMember(LocalDateTime deletedAt) {
        Member member = mock(Member.class);
        lenient().when(member.getId()).thenReturn(MEMBER_ID);
        when(member.isWithdrawn()).thenReturn(deletedAt != null);
        lenient().when(member.getDeletedAt()).thenReturn(deletedAt);
        when(memberRepository.findWithLockById(MEMBER_ID)).thenReturn(Optional.of(member));
        return member;
    }
}
