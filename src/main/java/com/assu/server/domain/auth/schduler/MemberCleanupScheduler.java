package com.assu.server.domain.auth.schduler;

import com.assu.server.domain.auth.service.MemberHardDeleteService;
import com.assu.server.domain.member.entity.Member;
import com.assu.server.domain.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MemberCleanupScheduler {

    private final MemberRepository memberRepository;
    private final MemberHardDeleteService memberHardDeleteService;

    @Scheduled(cron = "0 0 2 * * ?") // 매일 오전 2시
    public void cleanupDeletedMembers() {
        log.info("탈퇴 회원 완전 삭제 작업 시작");

        LocalDateTime oneMonthAgo = LocalDateTime.now().minusMonths(1);
        List<Member> membersToDelete = memberRepository.findByDeletedAtBefore(oneMonthAgo);

        if (membersToDelete.isEmpty()) {
            log.info("완전 삭제할 탈퇴 회원이 없습니다.");
            return;
        }

        log.info("완전 삭제 대상 탈퇴 회원 수: {}", membersToDelete.size());

        int successCount = 0;
        int skipCount = 0;
        int failCount = 0;

        for (Member member : membersToDelete) {
            Long memberId = member.getId();
            try {
                if (memberHardDeleteService.hardDeleteMember(memberId)) {
                    successCount++;
                } else {
                    skipCount++;
                }
            } catch (Exception e) {
                failCount++;
                log.error("탈퇴 회원 완전 삭제 실패: memberId={}", memberId, e);
            }
        }

        log.info("탈퇴 회원 완전 삭제 작업 완료: 삭제 {}명, 스킵 {}명, 실패 {}명", successCount, skipCount, failCount);
    }
}
