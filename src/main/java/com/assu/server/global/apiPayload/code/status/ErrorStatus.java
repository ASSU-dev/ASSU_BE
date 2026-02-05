package com.assu.server.global.apiPayload.code.status;

import com.assu.server.global.apiPayload.code.BaseErrorCode;
import com.assu.server.global.apiPayload.code.ErrorReasonDTO;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ErrorStatus implements BaseErrorCode {
    // 기본 에러
    _INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON500", "서버 에러, 관리자에게 문의 바랍니다."),
    _BAD_REQUEST(HttpStatus.BAD_REQUEST, "COMMON400", "잘못된 요청입니다."),
    _UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "COMMON401", "인증이 필요합니다."),
    _FORBIDDEN(HttpStatus.FORBIDDEN, "COMMON403", "금지된 요청입니다."),

    // 인가 관련 에러
    AUTHORIZATION_EXCEPTION(HttpStatus.UNAUTHORIZED, "AUTH4001", "인증에 실패하였습니다."),
    JWT_ACCESS_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "AUTH4002", "AccessToken이 만료되었습니다."),
    JWT_REFRESH_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "AUTH4003", "RefreshToken이 만료되었습니다."),
    LOGOUT_USER(HttpStatus.UNAUTHORIZED, "AUTH4004", "로그아웃된 유저입니다."),
    JWT_TOKEN_NOT_RECEIVED(HttpStatus.UNAUTHORIZED, "AUTH4005", "JWT 토큰이 전달되지 않았습니다."),
    JWT_TOKEN_OUT_OF_FORM(HttpStatus.UNAUTHORIZED, "AUTH4006", "JWT 토큰의 형식이 올바르지 않습니다."),
    REFRESH_TOKEN_NOT_EQUAL(HttpStatus.UNAUTHORIZED, "AUTH4007", "Refreash 토큰이 일치하지 않습니다."),

    // 숭실대 관련 에러
    SSU_SAINT_SSO_FAILED(HttpStatus.UNAUTHORIZED, "SSU4000", "숭실대학교 유세인트 SSO 로그인에 실패했습니다."),
    SSU_SAINT_PORTAL_FAILED(HttpStatus.UNAUTHORIZED, "SSU4001", "숭실대학교 유세인트 포털 접근에 실패했습니다."),
    SSU_SAINT_PARSE_FAILED(HttpStatus.UNAUTHORIZED, "SSU4002", "숭실대학교 유세인트 포털 크롤링 파싱에 실패했습니다."),
    SSU_SAINT_UNSUPPORTED_MAJOR(HttpStatus.UNAUTHORIZED, "SSU4003", "지원하는 학과가 아닙니다."),

    // 알리고 SMS 전송 관련 에러
    FAILED_TO_SEND_SMS(HttpStatus.INTERNAL_SERVER_ERROR, "ALIGO500", "알리고 SMS 전송에 실패했습니다."),
    FAILED_TO_PARSE_ALIGO(HttpStatus.INTERNAL_SERVER_ERROR, "ALIGO500", "알리고 SMS 파싱에 실패했습니다."),

    // 인증 에러
    NOT_VERIFIED_PHONE_NUMBER(HttpStatus.BAD_REQUEST,"AUTH_4007","전화번호 인증에 실패했습니다."),

    //페이징 에러
    PAGE_UNDER_ONE(HttpStatus.BAD_REQUEST,"PAGE_4001","페이지는 1이상이여야 합니다."),
    PAGE_SIZE_INVALID(HttpStatus.BAD_REQUEST,"PAGE_4002","size는 1~200 사이여야 합니다."),

    // 멤버 에러
    NO_SUCH_MEMBER(HttpStatus.NOT_FOUND,"MEMBER_4001","존재하지 않는 멤버 ID입니다."),
    NO_STUDENT_TYPE(HttpStatus.BAD_REQUEST, "MEMBER4002", "학생 타입이 아닌 멤버입니다."),

    NO_SUCH_ADMIN(HttpStatus.NOT_FOUND,"MEMBER_4002","존재하지 않는 admin ID 입니다."),
    NO_SUCH_PARTNER(HttpStatus.NOT_FOUND,"MEMBER_4003","존재하지 않는 partner ID 입니다."),
    NO_SUCH_STUDENT(HttpStatus.NOT_FOUND,"MEMBER_4004","존재하지 않는 student ID 입니다."),
    NO_SUCH_STORE(HttpStatus.NOT_FOUND, "STORE_4006", "존재하지 않는 스토어 ID입니다."),
    NO_SUCH_USAGE(HttpStatus.NOT_FOUND, "USAGE4001", "존재하지 않는 제휴 사용 내역입니다."),
    NO_PAPER_FOR_STORE(HttpStatus.NOT_FOUND, "ADMIN_4005", "존재하지 않는 paper ID입니다."),
    NO_AVAILABLE_PARTNER(HttpStatus.NOT_FOUND, "MEMBER_4009", "제휴업체를 찾을 수 없습니다."),
    NO_SUCH_STORE_WITH_THAT_PARTNER(HttpStatus.NOT_FOUND,"MEMBER_4006","해당 store ID에 해당하는 partner ID가 존재하지 않습니다."),
    EXISTED_PHONE(HttpStatus.CONFLICT,"MEMBER_4007","이미 존재하는 전화번호입니다."),
    EXISTED_EMAIL(HttpStatus.CONFLICT,"MEMBER_4008","이미 존재하는 이메일입니다."),
    EXISTED_STUDENT(HttpStatus.CONFLICT,"MEMBER_4009","이미 존재하는 학번입니다."),

    MEMBER_ALREADY_WITHDRAWN(HttpStatus.BAD_REQUEST, "MEMBER_4010", "이미 탈퇴된 회원입니다."),

    // 제휴 에러
    NO_SUCH_PAPER(HttpStatus.NOT_FOUND, "PAPER_9001", "제휴를 찾을 수 없습니다."),
    NO_SUCH_CONTENT(HttpStatus.NOT_FOUND, "PAPER_4002", "제휴 내용을 찾을 수 없습니다."),

    // session 에러
    NO_SUCH_SESSION(HttpStatus.NOT_FOUND, "SESSION4001", "존재하지 않는 session ID입니다."),
    SESSION_NOT_OPENED(HttpStatus.BAD_REQUEST, "SESSION4002", "만료되었거나 인증이 완료된 session ID입니다."),
    DOUBLE_CERTIFIED_USER(HttpStatus.BAD_REQUEST, "SESSION4003", "이미 인증된 유저입니다."),

    //리뷰 이미지 에러
    IMAGE_UPLOAD_FAILED(HttpStatus.INTERNAL_SERVER_ERROR,"REVIEW_4001", "리뷰 이미지 업로드에 실패했습니다"),
    IMAGE_NOT_FOUND(HttpStatus.NOT_FOUND,"REVIEW_4001", "존재하지 않는 리뷰이미지 입니다"),
    // 채팅 에러
    NO_SUCH_ROOM(HttpStatus.NOT_FOUND, "CHATTING_5001", "존재하지 않는 채팅방 ID 입니다."),
    NO_MEMBER_IN_THE_ROOM(HttpStatus.NOT_FOUND, "CHATTING_5002", "해당 방에는 해당 사용자가 없습니다."),
    NO_MEMBER(HttpStatus.NOT_FOUND, "CHATTING_5003", "해당 방에는 사용자가 아무도 없습니다."),
    NO_MESSAGE(HttpStatus.NOT_FOUND, "CHATTING_5004", "해당 방에는 메시지가 아무것 없습니다."),

    // 알림(Notification) 에러
    INVALID_NOTIFICATION_STATUS_FILTER(HttpStatus.BAD_REQUEST,"NOTIFICATION_4001","유효하지 않은 알림 status 필터입니다. (all | unread 만 허용)"),
    INVALID_NOTIFICATION_TYPE(HttpStatus.BAD_REQUEST,"NOTIFICATION_4002","지원하지 않는 알림 타입입니다."),
    NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND,"NOTIFICATION_4003","존재하지 않는 알림입니다."),
    NOTIFICATION_ACCESS_DENIED(HttpStatus.FORBIDDEN,"NOTIFICATION_4004","해당 알림에 접근할 권한이 없습니다."),
    MISSING_NOTIFICATION_FIELD(HttpStatus.BAD_REQUEST,"NOTIFICATION_4005","알림 생성에 필요한 필드가 누락되었습니다."),

    // 문의(Inquiry)
    INVALID_INQUIRY_STATUS_FILTER(HttpStatus.BAD_REQUEST,"INQUIRY_4001","status는 [all, waiting, answered] 중 하나여야 합니다."),
    NO_SUCH_INQUIRY(HttpStatus.NOT_FOUND,"INQUIRY_4002","존재하지 않는 문의입니다."),
    FORBIDDEN_INQUIRY(HttpStatus.FORBIDDEN,"INQUIRY_4003","해당 문의에 접근 권한이 없습니다."),
    ALREADY_ANSWERED(HttpStatus.CONFLICT,"INQUIRY_4091","이미 답변 완료된 문의입니다."),

    // 디바이스 토큰(DeviceToken) 에러
    DEVICE_TOKEN_NOT_FOUND(HttpStatus.NOT_FOUND,"DEVICE_4001","존재하지 않는 Device Token 입니다."),
    DEVICE_TOKEN_NOT_OWNED(HttpStatus.FORBIDDEN, "DEVICE_4004","해당 토큰은 본인 소유가 아닙니다."),
    DEVICE_TOKEN_REGISTER_FAILED(HttpStatus.INTERNAL_SERVER_ERROR,"DEVICE_5001","Device Token 등록에 실패했습니다."),

    // 주소 에러
    NO_SUCH_ADDRESS(HttpStatus.NOT_FOUND, "ADDRESS_7001", "주소를 찾을 수 없습니다."),

    // 프로필(Profile) 관련 에러
    PROFILE_IMAGE_UPLOAD_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "PROFILE_5001", "프로필 이미지 업로드에 실패했습니다."),
    PROFILE_IMAGE_DELETE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "PROFILE_5002", "프로필 이미지 삭제에 실패했습니다."),
    PROFILE_IMAGE_NOT_FOUND(HttpStatus.NOT_FOUND, "PROFILE_4001", "존재하지 않는 프로필 이미지입니다."),
    PROFILE_IMAGE_INVALID_FORMAT(HttpStatus.BAD_REQUEST, "PROFILE_4002", "지원하지 않는 이미지 형식입니다."),
    PROFILE_IMAGE_TOO_LARGE(HttpStatus.BAD_REQUEST, "PROFILE_4003", "허용된 크기를 초과한 이미지입니다."),
    FILE_SIZE_EXCEEDED(HttpStatus.BAD_REQUEST, "PROFILE_4004", "파일 크기가 허용 범위를 초과했습니다."),
    INVALID_FILE_TYPE(HttpStatus.BAD_REQUEST, "PROFILE_4005", "허용되지 않는 파일 형식입니다."),
    INVALID_CONTENT_TYPE(HttpStatus.BAD_REQUEST, "PROFILE_4006", "유효하지 않은 Content-Type입니다."),
    INVALID_FILE_NAME(HttpStatus.BAD_REQUEST, "PROFILE_4007", "유효하지 않은 파일명입니다."),


    // Suggestion 관련 에러
    NO_SUCH_SUGGESTION(HttpStatus.NOT_FOUND, "SUGGESTION_4001", "존재하지 않는 건의글입니다."),

    // 신고(Report) 관련 에러
    REPORT_DUPLICATE(HttpStatus.CONFLICT, "REPORT_4001", "이미 신고한 대상입니다."),
    REPORT_SELF_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "REPORT_4002", "자신을 신고할 수 없습니다."),
    REVIEW_REPORT_SELF_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "REPORT_4003", "자신의 리뷰를 신고할 수 없습니다."),
    SUGGESTION_REPORT_SELF_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "REPORT_4004", "자신의 건의글을 신고할 수 없습니다."),
    INVALID_REPORT_TYPE(HttpStatus.BAD_REQUEST, "REPORT_4005", "유효하지 않은 신고 타입입니다."),
    NO_USAGE_DATA(HttpStatus.NOT_FOUND, "ADMIN4001", "해당 관리자의 제휴 이용 내역이 없습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;

    @Override
    public ErrorReasonDTO getReasonHttpStatus() {
        return ErrorReasonDTO.builder()
                .message(message)
                .code(code)
                .isSuccess(false)
                .httpStatus(httpStatus)
                .build();
    }
}
