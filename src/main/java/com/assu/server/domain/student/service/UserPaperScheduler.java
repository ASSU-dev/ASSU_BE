package com.assu.server.domain.student.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserPaperScheduler {

    private final StudentServiceImpl studentService; // 또는 StudentService

    /**
     * 매일 새벽 3시에 전체 학생의 user_paper를 동기화
     * cron 형식: 초 분 시 일 월 요일
     * "0 0 3 * * *" → 매일 03:00:00
     */
    @Scheduled(cron = "0 0 3 * * *", zone = "Asia/Seoul")
    public void syncAllStudentsDaily() {
        studentService.syncUserPapersForAllStudents();
    }

}
