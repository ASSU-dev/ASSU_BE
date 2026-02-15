package com.assu.server.global.apiPayload.code.status;

import com.assu.server.global.apiPayload.code.BaseCode;
import com.assu.server.global.apiPayload.code.ReasonDTO;
import lombok.AllArgsConstructor;
import lombok.Getter;

import org.springframework.boot.autoconfigure.graphql.GraphQlProperties;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum SuccessStatus implements BaseCode {
    _OK(HttpStatus.OK, "COMMON200", "성공입니다."),
    _CREATED(HttpStatus.CREATED, "COMMON201", "요청 성공 및 리소스 생성됨"),

    //멤버 성공
    MEMBER_SUCCESS(HttpStatus.OK, "MEMBER_200", "성공적으로 조회되었습니다."),
    MEMBER_CREATED(HttpStatus.CREATED, "MEMBER_201", "성공적으로 생성되었습니다."),

    //인증 관련 성공
    SEND_AUTH_NUMBER_SUCCESS(HttpStatus.OK, "AUTH_200", "성공적으로 전송되었습니다."),
    VERIFY_AUTH_NUMBER_SUCCESS(HttpStatus.OK, "AUTH_201", "성공적으로 생성되었습니다."),

    //신고 성공
    REPORT_SUCCESS(HttpStatus.CREATED, "REPORT_201", "대상을 성공적으로 신고했습니다."),

    // 제휴 성공
    PAPER_STORE_HISTORY_SUCCESS(HttpStatus.OK, "PAPER201", "가게 별 제휴 내용이 성공적으로 조회되었습니다."),
    USER_PAPER_REQUEST_SUCCESS(HttpStatus.OK, "PAPER202", "제휴 요청이 성공적으로 처리되었습니다."),

    PARTNERSHIP_HISTORY_SUCCESS(HttpStatus.OK, "PARTNERSHIP202", "월 별 제휴 사용내역이 성공적으로 조회되었습니다."),
    UNREVIEWED_HISTORY_SUCCESS(HttpStatus.OK, "PARTNERSHIP203", "리뷰 되지 않은 제휴 사용내역이 성공적으로 조회되었습니다."),
    TEMPORARY_QR_DATA_SUCCESS(HttpStatus.OK, "TEMPORARY200", "임시 QR 데이터가 성공적으로 조회되었습니다."),

    // 그룹 인증
    GROUP_SESSION_CREATE(HttpStatus.OK, "GROUP201", "인증 세션 생성 및 대표자 구독이 완료되었습니다."),
    GROUP_CERTIFICATION_SUCCESS(HttpStatus.OK, "GROUP202", "그룹 인증 세션에 대한 인증이 완료되었습니다."),


    // 개인 인증
    PERSONAL_CERTIFICATION_SUCCESS(HttpStatus.OK, "PERSONAL201", "개인 인증이 완료 되었습니다."),

    // 베스트 조회
    BEST_STORE_SUCCESS(HttpStatus.OK, "STORE205", "베스트 매장 조회에 성공하였습니다")
    ;

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;

    @Override
    public ReasonDTO getReasonHttpStatus() {
        return ReasonDTO.builder()
                .message(message)
                .code(code)
                .isSuccess(true)
                .httpStatus(httpStatus)
                .build();
    }
}
