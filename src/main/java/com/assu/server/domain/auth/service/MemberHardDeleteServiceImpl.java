package com.assu.server.domain.auth.service;

import com.assu.server.domain.admin.repository.StudentAdminRepository;
import com.assu.server.domain.appreview.repository.AppReviewRepository;
import com.assu.server.domain.certification.repository.AssociateCertificationRepository;
import com.assu.server.domain.certification.repository.QRCertificationRepository;
import com.assu.server.domain.chat.repository.BlockRepository;
import com.assu.server.domain.chat.repository.ChatRepository;
import com.assu.server.domain.chat.repository.MessageRepository;
import com.assu.server.domain.common.enums.UserRole;
import com.assu.server.domain.deviceToken.repository.DeviceTokenRepository;
import com.assu.server.domain.inquiry.repository.InquiryRepository;
import com.assu.server.domain.member.entity.Member;
import com.assu.server.domain.member.repository.MemberRepository;
import com.assu.server.domain.notification.repository.NotificationOutboxRepository;
import com.assu.server.domain.notification.repository.NotificationRepository;
import com.assu.server.domain.notification.repository.NotificationSettingRepository;
import com.assu.server.domain.partnership.repository.PaperRepository;
import com.assu.server.domain.report.repository.ReportRepository;
import com.assu.server.domain.review.repository.ReviewRepository;
import com.assu.server.domain.store.repository.StoreRepository;
import com.assu.server.domain.student.repository.PartnershipUsageRepository;
import com.assu.server.domain.student.repository.StampEventApplicantRepository;
import com.assu.server.domain.student.repository.UserPaperRepository;
import com.assu.server.domain.suggestion.repository.SuggestionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class MemberHardDeleteServiceImpl implements MemberHardDeleteService {

    private final MemberRepository memberRepository;
    private final StoreRepository storeRepository;
    private final PaperRepository paperRepository;
    private final ChatRepository chatRepository;
    private final AppReviewRepository appReviewRepository;
    private final DeviceTokenRepository deviceTokenRepository;
    private final InquiryRepository inquiryRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationOutboxRepository notificationOutboxRepository;
    private final NotificationSettingRepository notificationSettingRepository;
    private final ReportRepository reportRepository;
    private final MessageRepository messageRepository;
    private final BlockRepository blockRepository;
    private final ReviewRepository reviewRepository;
    private final UserPaperRepository userPaperRepository;
    private final PartnershipUsageRepository partnershipUsageRepository;
    private final QRCertificationRepository qrCertificationRepository;
    private final AssociateCertificationRepository associateCertificationRepository;
    private final StudentAdminRepository studentAdminRepository;
    private final SuggestionRepository suggestionRepository;
    private final StampEventApplicantRepository stampEventApplicantRepository;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean hardDeleteMember(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalStateException("삭제 대상 회원을 찾을 수 없습니다. memberId=" + memberId));

        if (hasUndeletableCoreResource(member)) {
            log.warn("탈퇴 회원 하드 삭제 스킵: 핵심 리소스(Store/Paper/ChattingRoom) 보유. memberId={}, role={}",
                    memberId, member.getRole());
            return false;
        }

        anonymizeMemberScopedRecords(memberId);

        if (member.getRole() == UserRole.STUDENT) {
            anonymizeAndDeleteStudentScopedRecords(memberId);
        } else if (member.getRole() == UserRole.ADMIN) {
            deleteAdminScopedRecords(memberId);
        }

        memberRepository.delete(member);
        log.info("탈퇴 회원 하드 삭제 완료: memberId={}, role={}", memberId, member.getRole());
        return true;
    }

    private boolean hasUndeletableCoreResource(Member member) {
        if (member.getRole() == UserRole.PARTNER) {
            return storeRepository.findByPartnerId(member.getId()).isPresent()
                    || paperRepository.existsByPartner_Id(member.getId())
                    || chatRepository.existsByPartner_Id(member.getId())
                    || reviewRepository.existsByPartnerId(member.getId());
        }
        if (member.getRole() == UserRole.ADMIN) {
            return paperRepository.existsByAdmin_Id(member.getId())
                    || chatRepository.existsByAdmin_Id(member.getId());
        }
        return false;
    }

    private void anonymizeMemberScopedRecords(Long memberId) {
        appReviewRepository.deleteAllByMemberId(memberId);
        inquiryRepository.deleteAllByMemberId(memberId);
        notificationOutboxRepository.deleteAllByReceiverId(memberId);
        notificationRepository.deleteAllByReceiverId(memberId);
        notificationSettingRepository.deleteAllByMemberId(memberId);
        deviceTokenRepository.deleteAllByMemberId(memberId);

        reportRepository.anonymizeReporterByMemberId(memberId);
        reportRepository.anonymizeReportedByMemberId(memberId);
        messageRepository.anonymizeSenderByMemberId(memberId);
        messageRepository.anonymizeReceiverByMemberId(memberId);
        blockRepository.anonymizeBlockerByMemberId(memberId);
        blockRepository.anonymizeBlockedByMemberId(memberId);
    }

    private void anonymizeAndDeleteStudentScopedRecords(Long studentId) {
        reviewRepository.anonymizeStudentByStudentId(studentId);
        partnershipUsageRepository.anonymizeStudentByStudentId(studentId);
        qrCertificationRepository.anonymizeStudentByStudentId(studentId);
        associateCertificationRepository.anonymizeStudentByStudentId(studentId);
        studentAdminRepository.anonymizeStudentByStudentId(studentId);

        userPaperRepository.deleteAllByStudentId(studentId);
        suggestionRepository.deleteAllByStudentId(studentId);
        stampEventApplicantRepository.deleteAllByStudentId(studentId);
    }

    private void deleteAdminScopedRecords(Long adminId) {
        studentAdminRepository.deleteAllByAdminId(adminId);
        suggestionRepository.deleteAllByAdminId(adminId);
    }
}
