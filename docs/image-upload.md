# 이미지 업로드 기능 구현

## 배경

퀘스트 완료 시 인증 사진을 첨부할 수 있어야 한다. 앱(React Native)에서는 이미지 선택/리사이즈/WebView 브릿지 흐름이 완성되어 있고, 웹 프론트에서는 UI까지 완성된 상태다. 백엔드에 스토리지 연동, 업로드 엔드포인트, SubQuestLog 이미지 필드가 없어 이를 구현한다.

## 아키텍처

### 전체 이미지 업로드 흐름

```
[앱] 이미지 선택 (expo-media-library)
  → 리사이즈: 풀사이즈 1600px/0.7q, 썸네일 400px/0.5q (expo-image-manipulator)
  → WebView 브릿지: IMAGES_SELECTED (썸네일 base64 전송 → 웹 미리보기)
  → 퀘스트 완료 시 UPLOAD_PHOTOS 브릿지 메시지
  → [앱] multipart/form-data로 백엔드 업로드
  → IMAGES_UPLOADED (URL 목록 반환)
  → [웹] imageUrls 포함하여 POST /api/v1/user-quest/sub 호출
```

### 스토리지 결정 사항

| 항목 | 결정 | 이유 |
|------|------|------|
| 스토리지 | OCI Object Storage (S3 호환) | Free Tier 활용 |
| 썸네일 별도 저장 | 하지 않음 | 400px 썸네일은 UI 미리보기 전용(base64). 풀사이즈(1600px)만 저장 |
| 웹 브라우저 리사이즈 | Canvas API (프론트엔드) 권장 | 앱과 동일 스펙(1600px, 0.7q) 유지, 서버 부하 없음 |

## 구현 내용 (백엔드)

### 신규 파일

| 파일 | 설명 |
|------|------|
| `infrastructure/storage/StorageProperties.java` | OCI 설정 프로퍼티 (`oci.storage.*`) |
| `infrastructure/storage/StorageConfig.java` | S3Client Bean (OCI S3 호환 endpoint override) |
| `infrastructure/storage/StorageService.java` | 파일 업로드 → OCI URL 반환 |
| `domain/upload/controller/UploadController.java` | `POST /api/v1/upload/images` |
| `domain/upload/service/UploadService.java` | 파일 검증 + 업로드 오케스트레이션 |
| `domain/upload/dto/UploadResponseDto.java` | `{ "urls": [...] }` 응답 DTO |
| `resources/db/migration/V1_6__add_image_urls_to_sub_quest_log.sql` | `users_sub_quest_log_image_urls` 테이블 생성 |

### 수정 파일

| 파일 | 변경 내용 |
|------|----------|
| `build.gradle` | AWS SDK v2 BOM + S3 의존성 추가 |
| `global/error/ErrorType.java` | UPLOAD_FAILED / INVALID_FILE_TYPE / INVALID_FILE_COUNT 추가 |
| `domain/quest/entity/UsersSubQuestLog.java` | `imageUrls` 필드 (`@ElementCollection`) 추가 |
| `domain/quest/dto/SubQuestLogDto.java` | `imageUrls` 필드 추가 (nullable, 하위 호환) |
| `domain/quest/service/UsersSubQuestServiceImpl.java` | create/update/convert에 imageUrls 처리 |

## API 명세

### POST /api/v1/upload/images

이미지를 OCI Object Storage에 업로드하고 URL 목록을 반환한다.

- **인증**: JWT 필수
- **Content-Type**: `multipart/form-data`
- **제한**: 최대 5개, 파일당 10MB, `image/*` 타입만 허용

**Request**
```
files: MultipartFile[] (최대 5개)
```

**Response** `201 Created`
```json
{
  "status": 201,
  "data": {
    "urls": [
      "https://{namespace}.compat.objectstorage.{region}.oraclecloud.com/{bucket}/uploads/quests/{uuid}.jpg"
    ]
  }
}
```

### POST /api/v1/user-quest/sub (변경)

`imageUrls` 필드 추가. 기존 클라이언트는 해당 필드 없이도 동작한다.

**Request**
```json
{
  "id": 1,
  "difficulty": "NORMAL",
  "memo": "오늘도 완료!",
  "imageUrls": ["https://..."]
}
```

## 환경변수

```properties
oci.storage.endpoint=https://{namespace}.compat.objectstorage.{region}.oraclecloud.com
oci.storage.region={region}
oci.storage.bucket={bucket}
oci.storage.access-key={customer-secret-key-id}
oci.storage.secret-key={customer-secret-key}
```

OCI Customer Secret Key는 OCI 콘솔 → 사용자 설정 → Customer Secret Keys에서 발급.

## 진행 상황

- [x] 백엔드 설계 및 플랜 수립
- [x] AWS SDK v2 (S3 호환) 의존성 추가
- [x] StorageService, StorageConfig, StorageProperties 구현
- [x] UploadController, UploadService, UploadResponseDto 구현
- [x] DB 마이그레이션 (`V1_6`) 작성
- [x] UsersSubQuestLog 엔티티 imageUrls 필드 추가
- [x] SubQuestLogDto imageUrls 필드 추가
- [x] UsersSubQuestServiceImpl imageUrls 처리
- [ ] 빌드 확인 (Gradle 배포판 다운로드 이슈로 보류 중)
- [ ] OCI 환경변수 설정 및 실제 업로드 검증
- [ ] 웹 프론트 연동 (IMAGES_UPLOADED → imageUrls payload 포함)
- [ ] 웹 브라우저 fallback Canvas 리사이즈 구현

## 잔여 작업

### 앱 (status-app)
- `uploadPhotos()` 실제 구현: `UPLOAD_PHOTOS` 브릿지 수신 시 `POST /api/v1/upload/images` 호출
- 업로드 결과를 `IMAGES_UPLOADED` 메시지로 웹에 전달

### 웹 프론트 (status-front)
- `use-webview-image-upload.ts`: `IMAGES_UPLOADED` 수신 시 URL 저장
- 퀘스트 완료 payload에 `imageUrls` 포함 (`SubQuestLogDTO` 업데이트)
- `use-browser-image-upload.ts`: Canvas API 리사이즈 추가 (1600px max, JPEG 0.7)
