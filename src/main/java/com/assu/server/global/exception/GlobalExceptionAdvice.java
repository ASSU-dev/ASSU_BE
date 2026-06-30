package com.assu.server.global.exception;


import com.assu.server.global.apiPayload.BaseResponse;
import com.assu.server.global.apiPayload.code.ErrorReasonDTO;
import com.assu.server.global.apiPayload.code.status.ErrorStatus;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
@RestControllerAdvice(annotations = {RestController.class})
public class GlobalExceptionAdvice extends ResponseEntityExceptionHandler {

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(
            TypeMismatchException e,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        String errorMessage = e.getPropertyName() + ": 올바른 값이 아닙니다.";

        return handleExceptionInternalMessage(e, headers, request, errorMessage);
    }

    @Override
    protected ResponseEntity<Object> handleMissingServletRequestParameter(
            MissingServletRequestParameterException e,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        String errorMessage = e.getParameterName() + ": 올바른 값이 아닙니다.";

        return handleExceptionInternalMessage(e, headers, request, errorMessage);
    }

    @ExceptionHandler
    public ResponseEntity<Object> validation(ConstraintViolationException e, WebRequest request) {
        String errorMessage =
                e.getConstraintViolations().stream()
                        .map(constraintViolation -> constraintViolation.getMessage())
                        .findFirst()
                        .orElseThrow(
                                () ->
                                        new RuntimeException(
                                                "ConstraintViolationException 추출 도중 에러 발생"));

        return handleExceptionInternalConstraint(
                e, ErrorStatus.valueOf(errorMessage), HttpHeaders.EMPTY, request);
    }

    @Override
    public ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException e,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        Map<String, String> errors = new LinkedHashMap<>();

        e.getBindingResult().getFieldErrors().stream()
                .forEach(
                        fieldError -> {
                            String fieldName = fieldError.getField();
                            String errorMessage;
                            try {
                                errorMessage = Optional.ofNullable(ErrorStatus.valueOf(fieldError.getDefaultMessage()).getMessage()).orElse("");
                            } catch (IllegalArgumentException ex) {
                                errorMessage = Optional.ofNullable(fieldError.getDefaultMessage()).orElse("");
                            }
                            errors.merge(
                                    fieldName,
                                    errorMessage,
                                    (existingErrorMessage, newErrorMessage) ->
                                            existingErrorMessage + ", " + newErrorMessage);
                        });

        return handleExceptionInternalArgs(
                e, HttpHeaders.EMPTY, ErrorStatus.valueOf("_BAD_REQUEST"), request, errors);
    }

    @ExceptionHandler
    public ResponseEntity<Object> exception(Exception e, WebRequest request) {
        e.printStackTrace();

        return handleExceptionInternalFalse(
                e,
                ErrorStatus._INTERNAL_SERVER_ERROR,
                HttpHeaders.EMPTY,
                ErrorStatus._INTERNAL_SERVER_ERROR.getHttpStatus(),
                request,
                e.getMessage());
    }

    @ExceptionHandler(value = GeneralException.class)
    public ResponseEntity onThrowException(
            GeneralException generalException, HttpServletRequest request) {
        ErrorReasonDTO errorReasonHttpStatus = generalException.getErrorReasonHttpStatus();
        return handleExceptionInternal(generalException, errorReasonHttpStatus, null, request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Object> handleAccessDeniedException(
            AccessDeniedException exception, HttpServletRequest request) {
        ErrorReasonDTO reason = ErrorStatus._FORBIDDEN.getReasonHttpStatus();
        return handleExceptionInternal(exception, reason, null, request);
    }

    private ResponseEntity<Object> handleExceptionInternal(
            Exception e, ErrorReasonDTO reason, HttpHeaders headers, HttpServletRequest request) {

        BaseResponse<Object> body =
                BaseResponse.onFailure(reason.getCode(), reason.getMessage(), null);

        WebRequest webRequest = new ServletWebRequest(request);
        return super.handleExceptionInternal(e, body, headers, reason.getHttpStatus(), webRequest);
    }

    private ResponseEntity<Object> handleExceptionInternalFalse(
            Exception e,
            ErrorStatus errorCommonStatus,
            HttpHeaders headers,
            HttpStatus status,
            WebRequest request,
            String errorPoint) {
        BaseResponse<Object> body =
                BaseResponse.onFailure(
                        errorCommonStatus.getCode(), errorCommonStatus.getMessage(), errorPoint);
        return super.handleExceptionInternal(e, body, headers, status, request);
    }

    private ResponseEntity<Object> handleExceptionInternalArgs(
            Exception e,
            HttpHeaders headers,
            ErrorStatus errorCommonStatus,
            WebRequest request,
            Map<String, String> errorArgs) {
        BaseResponse<Object> body =
                BaseResponse.onFailure(
                        errorCommonStatus.getCode(), errorCommonStatus.getMessage(), errorArgs);
        return super.handleExceptionInternal(
                e, body, headers, errorCommonStatus.getHttpStatus(), request);
    }

    private ResponseEntity<Object> handleExceptionInternalConstraint(
            Exception e, ErrorStatus errorCommonStatus, HttpHeaders headers, WebRequest request) {
        BaseResponse<Object> body =
                BaseResponse.onFailure(
                        errorCommonStatus.getCode(), errorCommonStatus.getMessage(), null);
        return super.handleExceptionInternal(
                e, body, headers, errorCommonStatus.getHttpStatus(), request);
    }

    private ResponseEntity<Object> handleExceptionInternalMessage(
            Exception e, HttpHeaders headers, WebRequest request, String errorMessage) {
        ErrorStatus errorStatus = ErrorStatus._BAD_REQUEST;
        BaseResponse<String> body =
                BaseResponse.onFailure(
                        errorStatus.getCode(), errorStatus.getMessage(), errorMessage);

        return super.handleExceptionInternal(
                e, body, headers, errorStatus.getHttpStatus(), request);
    }
}
