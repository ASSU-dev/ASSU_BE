package com.assu.server.domain.backoffice.controller;

import com.assu.server.domain.backoffice.annotation.BackofficeAudited;
import com.assu.server.domain.student.service.StudentService;
import com.assu.server.global.apiPayload.BaseResponse;
import com.assu.server.global.apiPayload.code.status.SuccessStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Backoffice", description = "백오피스 운영 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/backoffice/students")
@PreAuthorize("hasRole('BACKOFFICE')")
public class BackofficeStudentController {

    private final StudentService studentService;

    @BackofficeAudited(action = "STUDENT_SYNC")
    @Operation(
            summary = "전체 학생 user_paper 동기화 API",
            description = "# [v1.0 (2026-03-16)](https://clumsy-seeder-416.notion.site/3251197c19ed8066885cece9ffc455f6?source=copy_link)\n" +
                    "- 모든 학생의 user_paper 데이터를 동기화합니다.\n" +
                    "- `BACKOFFICE` 역할 및 `aud=backoffice` JWT가 필요합니다.\n" +
                    "- 시스템 전체에 영향을 주는 작업이므로 주의해서 사용해야 합니다.\n\n" +
                    "**주의사항:**\n" +
                    "- 대량의 데이터 처리로 인해 시간이 오래 걸릴 수 있음\n" +
                    "- 실행 중에는 다른 제휴 관련 작업에 영향을 줄 수 있음\n\n" +
                    "**Response:**\n" +
                    "- 성공 시 200(OK)과 동기화 완료 메시지 반환\n" +
                    "- 401(UNAUTHORIZED): 인증되지 않았거나 audience 불일치\n" +
                    "- 403(FORBIDDEN): BACKOFFICE 권한 없음\n" +
                    "- 500(INTERNAL_SERVER_ERROR): 동기화 작업 실패"
    )
    @PostMapping("/sync")
    public BaseResponse<String> syncAllStudentsNow() {
        studentService.syncUserPapersForAllStudents();
        return BaseResponse.onSuccess(SuccessStatus._OK, "전체 학생 user_paper 동기화 완료");
    }
}
