package com.assu.server.backoffice;

import com.assu.server.domain.auth.exception.CustomAuthException;
import com.assu.server.domain.auth.service.WithdrawalService;
import com.assu.server.domain.backoffice.dto.BackofficeProfileImageResponseDTO;
import com.assu.server.domain.backoffice.service.BackofficeMemberServiceImpl;
import com.assu.server.domain.common.enums.UserRole;
import com.assu.server.domain.member.entity.Member;
import com.assu.server.domain.member.repository.MemberRepository;
import com.assu.server.domain.member.service.ProfileImageService;
import com.assu.server.domain.partner.entity.Partner;
import com.assu.server.domain.store.repository.StoreRepository;
import com.assu.server.global.apiPayload.code.status.ErrorStatus;
import com.assu.server.infra.s3.AmazonS3Manager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BackofficeMemberProfileImageServiceTest {

    private static final Long PARTNER_ID = 10L;

    @InjectMocks
    private BackofficeMemberServiceImpl backofficeMemberService;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private StoreRepository storeRepository;

    @Mock
    private WithdrawalService withdrawalService;

    @Mock
    private AmazonS3Manager amazonS3Manager;

    @Mock
    private ProfileImageService profileImageService;

    private MockMultipartFile image() {
        return new MockMultipartFile("image", "store.png", "image/png", new byte[]{1, 2, 3});
    }

    private void givenPartner() {
        Member member = mock(Member.class);
        Partner partner = mock(Partner.class);
        when(member.getRole()).thenReturn(UserRole.PARTNER);
        when(member.getPartnerProfile()).thenReturn(partner);
        when(partner.getId()).thenReturn(PARTNER_ID);
        when(memberRepository.findPartnerWithProfileById(PARTNER_ID)).thenReturn(Optional.of(member));
    }

    @Test
    @DisplayName("파트너 프로필 이미지를 업로드하면 presigned URL을 반환한다")
    void updatePartnerProfileImage_whenPartner_thenReturnsPresignedUrl() {
        // given
        givenPartner();
        MockMultipartFile image = image();
        when(profileImageService.updateProfileImage(PARTNER_ID, image)).thenReturn("members/10/profile/store.png");
        when(amazonS3Manager.generatePresignedUrl("members/10/profile/store.png")).thenReturn("https://s3/presigned");

        // when
        BackofficeProfileImageResponseDTO result = backofficeMemberService.updatePartnerProfileImage(PARTNER_ID, image);

        // then
        assertThat(result.url()).isEqualTo("https://s3/presigned");
        verify(profileImageService).updateProfileImage(PARTNER_ID, image);
    }

    @Test
    @DisplayName("파트너가 아닌 회원에게 업로드하면 NO_SUCH_PARTNER 예외가 발생한다")
    void updatePartnerProfileImage_whenNotPartner_thenThrows() {
        // given
        when(memberRepository.findPartnerWithProfileById(PARTNER_ID)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> backofficeMemberService.updatePartnerProfileImage(PARTNER_ID, image()))
                .isInstanceOf(CustomAuthException.class)
                .extracting(ex -> ((CustomAuthException) ex).getCode())
                .isEqualTo(ErrorStatus.NO_SUCH_PARTNER);
        verify(profileImageService, never()).updateProfileImage(PARTNER_ID, image());
    }

    @Test
    @DisplayName("파트너 프로필 이미지를 삭제하면 ProfileImageService에 위임한다")
    void deletePartnerProfileImage_whenPartner_thenDelegates() {
        // given
        givenPartner();

        // when
        backofficeMemberService.deletePartnerProfileImage(PARTNER_ID);

        // then
        verify(profileImageService).deleteProfileImage(PARTNER_ID);
    }

    @Test
    @DisplayName("파트너가 아닌 회원의 프로필 이미지를 삭제하면 NO_SUCH_PARTNER 예외가 발생한다")
    void deletePartnerProfileImage_whenNotPartner_thenThrows() {
        // given
        when(memberRepository.findPartnerWithProfileById(PARTNER_ID)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> backofficeMemberService.deletePartnerProfileImage(PARTNER_ID))
                .isInstanceOf(CustomAuthException.class)
                .extracting(ex -> ((CustomAuthException) ex).getCode())
                .isEqualTo(ErrorStatus.NO_SUCH_PARTNER);
        verify(profileImageService, never()).deleteProfileImage(PARTNER_ID);
    }
}
