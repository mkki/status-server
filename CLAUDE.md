# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
# 빌드
./gradlew build

# 컴파일만
./gradlew compileJava

# 테스트 전체
./gradlew test

# 단일 테스트 클래스
./gradlew test --tests "com.statoverflow.status.SomeTest"

# 단일 테스트 메서드
./gradlew test --tests "com.statoverflow.status.SomeTest.methodName"
```

## Architecture

Spring Boot 3.3 / Java 17 / PostgreSQL / Redis / Flyway

### 패키지 구조

```
com.statoverflow.status
├── domain/
│   ├── auth/       # OAuth 로그인, JWT 발급
│   ├── quest/      # 퀘스트 핵심 도메인
│   ├── attribute/  # 능력치 경험치 처리
│   ├── users/      # 사용자 관리
│   ├── upload/     # 이미지 업로드 (OCI Object Storage)
│   ├── master/     # 정적 마스터 데이터 (Attribute, TierLevel 등)
│   └── config/     # QuestScheduler (스케줄링)
├── infrastructure/
│   └── storage/    # S3Client Bean, StorageService
└── global/
    ├── jwt/        # JwtService, JwtAuthenticationFilter
    ├── config/     # Security, CORS, Redis, Swagger
    ├── annotation/ # @CurrentUser
    ├── error/      # ErrorType enum
    ├── exception/  # CustomException, GlobalExceptionHandler
    ├── response/   # ApiResponse<T>
    └── event/      # LevelUpListener (현재 미사용, 주석 처리됨)
```

### 인증

JWT를 **쿠키**(`access_token`)로 전달한다. `JwtAuthenticationFilter`가 쿠키에서 토큰을 파싱하여 `BasicUsersDto`를 Security Context에 저장한다. 컨트롤러에서는 `@CurrentUser BasicUsersDto user`로 현재 사용자를 주입받는다.

로그아웃 시 `TokenBlacklistService`(Redis)에 토큰을 등록하며, 필터에서 블랙리스트 여부를 확인한다.

### OAuth 전략 패턴

`AuthCodeProcessor` 인터페이스를 Google/Kakao/Apple이 각각 구현한다. `OAuthServiceImpl`이 `List<AuthCodeProcessor>`를 주입받아 `supports(ProviderType)`으로 적절한 구현체를 선택한다.

### 퀘스트 도메인

정적 마스터 데이터(Theme → MainQuest → SubQuest)와 사용자 데이터(UsersMainQuest → UsersSubQuest → UsersSubQuestLog)가 분리되어 있다.

- `FrequencyType`: DAILY, WEEKLY_1~6, MONTHLY_1~4 — 서브퀘스트 반복 주기
- `QuestStatus`: ACTIVE / ACCOMPLISHED / WEEKLY_ACCOMPLISHED / COMPLETED / FAILED / DELETED
- `QuestScheduler`: 매일 자정에 상태 전이 실행 (ACCOMPLISHED→ACTIVE, 만료된 퀘스트→FAILED)

서브퀘스트 완료(`doSubQuest`) 흐름: 로그 생성 → 능력치 경험치 지급(`AttributeService.addExp`) → 상태 업데이트 → 메인퀘스트 완료 여부 확인.

### DB 마이그레이션

Flyway 사용. `src/main/resources/db/migration/` 아래 버전 순으로 실행된다. 마이그레이션 파일명은 `V{version}__{description}.sql`.

### 오류 처리

`ErrorType` enum에 HTTP 상태, 에러 코드, 메시지를 정의한다. 에러 코드 prefix: `00` global, `01` auth, `02` users, `03` attribute, `04` mainquest, `05` subquest, `06` upload.

`CustomException(ErrorType)`을 throw하면 `GlobalExceptionHandler`가 `ApiResponse.error()`로 일관된 형태로 응답한다.

### 스토리지

OCI Object Storage를 AWS SDK v2 S3 호환 모드로 사용한다. 엔드포인트, 버킷, 인증 정보는 `oci.storage.*` 프로퍼티로 주입된다. S3 호환 엔드포인트 형식: `https://{namespace}.compat.objectstorage.{region}.oraclecloud.com`.
