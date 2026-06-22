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
    @Operation(summary = "전체 학생 user_paper 동기화 API")
    @PostMapping("/sync")
    public BaseResponse<String> syncAllStudentsNow() {
        studentService.syncUserPapersForAllStudents();
        return BaseResponse.onSuccess(SuccessStatus._OK, "전체 학생 user_paper 동기화 완료");
    }
}
