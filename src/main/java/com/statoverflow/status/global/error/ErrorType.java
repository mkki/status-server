package com.statoverflow.status.global.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorType {

    // global 에러 (00)
    DEFAULT_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "00-001", "현재 앱에 문제가 발생했으니 관리자에게 문의해주세요."),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "00-002", "요청한 리소스를 찾을 수 없습니다."),
    INVALID_FIELD(HttpStatus.BAD_REQUEST, "00-003", "유효성 검사 실패. 상세: "),

    // 소셜 로그인 에러 (01)
    UNSUPPORTED_OAUTH_PROVIDER(HttpStatus.BAD_REQUEST, "01-001", "잘못된 소셜 식별자입니다."),
    USER_NOT_FOUND(HttpStatus.UNAUTHORIZED, "01-002", "해당 소셜 계정으로 가입된 사용자를 찾을 수 없습니다."),
    SOCIAL_ALREADY_CONNECTED(HttpStatus.BAD_REQUEST, "01-003", "해당 소셜 아이디로 연동된 계정이 이미 존재합니다."),

    NICKNAME_NOT_CHANGED(HttpStatus.CONFLICT, "02-001", "닉네임이 이전과 동일합니다."),

    INVALID_ATTRIBUTES(HttpStatus.BAD_REQUEST, "03-001", "한 개나 두 개의 능력치를 선택해야 합니다."),

    // 메인 퀘스트 에러 (02)
    MAINQUEST_NOT_FOUND(HttpStatus.NOT_FOUND, "04-001", "해당하는 메인 퀘스트가 존재하지 않습니다."),
    INVALID_MAINQUEST(HttpStatus.BAD_REQUEST, "04-002", "삭제할 수 없는 메인 퀘스트입니다."),

    // 서브 퀘스트 에러
    INVALID_SUBQUEST_SELECTED(HttpStatus.BAD_REQUEST, "05-001", "선택된 서브퀘스트의 갯수가 잘못되었습니다."),
    COMPLETED_SUBQUEST(HttpStatus.BAD_REQUEST, "05-002", "이미 완료한 서브퀘스트 입니다."),
    SUBQUEST_NOT_FOUND(HttpStatus.NOT_FOUND, "05-003", "해당하는 서브 퀘스트가 존재하지 않습니다."),
    SUBQUESTLOG_NOT_FOUND(HttpStatus.NOT_FOUND, "05-004", "해당하는 서브 퀘스트 완료 내역이 존재하지 않습니다."),

    // 업로드 에러 (06)
    UPLOAD_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "06-001", "파일 업로드에 실패했습니다."),
    INVALID_FILE_TYPE(HttpStatus.BAD_REQUEST, "06-002", "이미지 파일만 업로드할 수 있습니다."),
    INVALID_FILE_COUNT(HttpStatus.BAD_REQUEST, "06-003", "이미지는 최대 5개까지 업로드할 수 있습니다.");

    private final HttpStatus status;
    private final String errorCode;
    private final String message;
    }