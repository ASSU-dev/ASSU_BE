package com.assu.server.backoffice;

import com.assu.server.domain.backoffice.aop.BackofficeAuditAspect;
import com.assu.server.domain.backoffice.entity.BackofficeAuditLog;
import com.assu.server.domain.backoffice.entity.enums.BackofficeAuditStatus;
import com.assu.server.domain.backoffice.service.BackofficeAuditLogService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BackofficeAuditAspectTest {

    @InjectMocks
    private BackofficeAuditAspect backofficeAuditAspect;

    @Mock
    private BackofficeAuditLogService backofficeAuditLogService;

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Mock
    private MethodSignature methodSignature;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    @DisplayName("성공 시 SUCCESS 감사 로그를 저장한다")
    void savesSuccessAuditLog() throws Throwable {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/backoffice/students/sync");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("1", null)
        );

        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getDeclaringTypeName()).thenReturn("com.assu.server.domain.backoffice.controller.BackofficeStudentController");
        when(methodSignature.getName()).thenReturn("syncAllStudentsNow");
        when(joinPoint.proceed()).thenReturn("ok");

        var annotation = TestController.class.getDeclaredMethod("sync").getAnnotation(
                com.assu.server.domain.backoffice.annotation.BackofficeAudited.class
        );

        Object result = backofficeAuditAspect.audit(joinPoint, annotation);

        assertThat(result).isEqualTo("ok");
        ArgumentCaptor<BackofficeAuditLog> captor = ArgumentCaptor.forClass(BackofficeAuditLog.class);
        verify(backofficeAuditLogService).save(captor.capture());
        BackofficeAuditLog saved = captor.getValue();
        assertThat(saved.getAction()).isEqualTo("STUDENT_SYNC");
        assertThat(saved.getStatus()).isEqualTo(BackofficeAuditStatus.SUCCESS);
        assertThat(saved.getHttpMethod()).isEqualTo("POST");
        assertThat(saved.getRequestUri()).isEqualTo("/backoffice/students/sync");
    }

    @Test
    @DisplayName("실패 시 FAILURE 감사 로그를 저장하고 예외를 재던진다")
    void savesFailureAuditLogAndRethrows() throws Throwable {
        MockHttpServletRequest request = new MockHttpServletRequest("PATCH", "/backoffice/inquiries/10/answer");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getDeclaringTypeName()).thenReturn("com.assu.server.domain.backoffice.controller.BackofficeInquiryController");
        when(methodSignature.getName()).thenReturn("answerInquiry");
        when(joinPoint.proceed()).thenThrow(new IllegalStateException("boom"));

        var annotation = TestController.class.getDeclaredMethod("answer").getAnnotation(
                com.assu.server.domain.backoffice.annotation.BackofficeAudited.class
        );

        assertThatThrownBy(() -> backofficeAuditAspect.audit(joinPoint, annotation))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("boom");

        verify(backofficeAuditLogService).saveFailure(
                any(),
                org.mockito.ArgumentMatchers.eq("PATCH"),
                org.mockito.ArgumentMatchers.eq("/backoffice/inquiries/10/answer"),
                org.mockito.ArgumentMatchers.contains("BackofficeInquiryController.answerInquiry"),
                org.mockito.ArgumentMatchers.eq("INQUIRY_ANSWER"),
                any(),
                any(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.eq("boom")
        );
    }

    static class TestController {
        @com.assu.server.domain.backoffice.annotation.BackofficeAudited(action = "STUDENT_SYNC")
        void sync() {
        }

        @com.assu.server.domain.backoffice.annotation.BackofficeAudited(action = "INQUIRY_ANSWER", targetId = "#inquiryId")
        void answer() {
        }
    }
}
