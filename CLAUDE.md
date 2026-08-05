# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Instructions

- 모든 답변은 **한국어**로 작성한다.
- 코드 블록 내 주석은 작성하지 않는다.
- import 시 와일드카드(`*`)를 사용하지 않는다.
- 코드 작성 전에 다음 순서를 따른다:
  1. 현재 구조 및 흐름 분석
  2. 불명확하거나 사용자 판단이 필요한 부분은 먼저 질문
  3. 방향 제안 및 트레이드오프 제시
  4. **"개발해줘", "작성해줘", "구현해줘"** 등 명시적 요청이 있을 때만 코드 작성
- 여러 기능 항목이 포함된 이슈를 구현할 때는 항목 하나씩 처리한다 (분석 → 질문 → 방향 → 승인 → 구현).

---

## Build & Run Commands

```bash
# 빌드 (config 디렉토리의 yml 파일을 resources로 복사 후 컴파일)
./gradlew build

# 테스트 전체 실행
./gradlew test

# 단일 테스트 클래스 실행
./gradlew test --tests "com.assu.server.backoffice.BackofficeAuditAspectTest"

# 단일 테스트 메서드 실행
./gradlew test --tests "com.assu.server.backoffice.BackofficeAuditAspectTest.testMethodName"

# 빌드 없이 로컬 실행
./gradlew bootRun --args='--spring.profiles.active=local'
```

설정 파일은 `config/local`, `config/dev`, `config/prod` 디렉토리에 위치하며, `processResources` 단계에서 `src/main/resources`로 자동 복사된다.

---

## 기술 스택

- **Spring Boot 3.5.3**, Java 17
- **DB**: MariaDB (운영), H2 (테스트), Hibernate Spatial (공간 데이터)
- **캐시/세션**: Redis
- **메시지 브로커**: RabbitMQ (FCM 알림 Outbox 패턴)
- **실시간 통신**: WebSocket + STOMP (채팅, 그룹 QR 인증)
- **파일 스토리지**: AWS S3
- **푸시 알림**: Firebase Admin SDK (FCM)
- **외부 API**: Kakao Local API (장소 검색), 알리고 SMS
- **문서화**: SpringDoc OpenAPI 3 (Swagger UI: `/swagger-ui.html`)

---

## 아키텍처 개요

### DDD 계층 구조

```
Presentation  (Controller)   ← 클라이언트 요청 수신, DTO 반환
      ↓
Application   (Service)      ← 비즈니스 로직, 트랜잭션
      ↓
Domain        (Entity, Repository interface)
      ↑
Infrastructure (Repository impl, External API client)
```

**규칙**
- 상위 레이어 → 하위 레이어 접근 가능, 역방향 불가
- 각 도메인은 서로 철저히 분리
- 각 레이어는 하나의 관심사에만 집중

### 패키지 구조

```
src/main/java/com/assu/server/
├── ServerApplication.java
├── domain/
│   ├── common/          # 공통 엔티티 (BaseEntity, AdminUser, 공통 enum)
│   ├── admin/           # 학생회 계정
│   ├── appreview/       # 앱 리뷰
│   ├── auth/            # 인증 (로그인, 회원가입, 탈퇴, JWT, Security)
│   ├── backoffice/      # 백오피스 운영자
│   ├── certification/   # QR 인증 (개인/그룹)
│   ├── chat/            # 채팅 (학생회 ↔ 제휴업체)
│   ├── deviceToken/     # FCM 디바이스 토큰
│   ├── inquiry/         # 학생 문의하기
│   ├── map/             # 지도/장소 검색 (Kakao Local API)
│   ├── member/          # 공통 회원 (프로필 이미지)
│   ├── notification/    # 알림 (FCM, RabbitMQ Outbox)
│   ├── partner/         # 제휴업체
│   ├── partnership/     # 제휴 계약 (Paper, PaperContent, Goods)
│   ├── qr/              # QR 코드 (임시 QR, 딥링크 리다이렉트)
│   ├── report/          # 신고
│   ├── review/          # 가게 리뷰
│   ├── store/           # 가게
│   ├── student/         # 학생 (USaint 토큰 인증)
│   └── suggestion/      # 학생→학생회 제안
├── global/
│   ├── apiPayload/      # BaseResponse, 공통 응답 코드
│   ├── config/          # Spring 설정 (Security, Redis, WebSocket 등)
│   ├── exception/       # 전역 예외 처리
│   ├── security/        # 인증 에러 핸들러
│   └── util/            # 유틸리티
└── infra/
    ├── aligo/           # 알리고 SMS
    ├── firebase/        # FCM, RabbitMQ
    └── s3/              # AWS S3
```

각 도메인 내부 구조:

```
<domain>/
├── controller/
├── service/
├── repository/
├── dto/
├── entity/
│   └── enums/           # 도메인별 enum
└── exception/
    ├── handler/
    ├── validator/
    └── annotation/
```

### 도메인 설명

| 도메인 | 설명 |
|--------|------|
| common | BaseEntity, AdminUser, 공통 enum (UserRole, ActivationStatus 등) |
| admin | 학생회 계정. `SUSPEND` 상태로 가입 후 백오피스 승인 필요 |
| appreview | 학생의 앱 스토어 리뷰 작성 기록 |
| auth | JWT 발급, 로그인/로그아웃, 회원가입, 탈퇴, Security 필터 체인 |
| backoffice | 운영자 전용 관리 API. 별도 JWT(`aud=backoffice`) 사용 |
| certification | QR 스캔 인증. 개인 인증과 WebSocket 기반 그룹 인증 |
| chat | 학생회 ↔ 제휴업체 간 채팅. Redis Pub/Sub + WebSocket |
| deviceToken | FCM 푸시 알림용 디바이스 토큰 등록/삭제 |
| inquiry | 학생의 1:1 문의. 백오피스에서 답변 |
| map | Kakao Local API 기반 장소 검색 및 주변 가게 조회 |
| member | 공통 회원 프로필 이미지 관리 (S3) |
| notification | FCM 푸시 알림. Outbox 패턴으로 RabbitMQ 경유 발송 |
| partner | 제휴업체 계정. `SUSPEND` 상태로 가입 후 백오피스 승인 필요 |
| partnership | 제휴 계약 (Paper). 계약서 내용(PaperContent), 제공 상품(Goods) |
| qr | 임시 QR 스탬프, QR 딥링크 리다이렉트 (Thymeleaf) |
| report | 리뷰/채팅 신고. 백오피스에서 처리 |
| review | 학생의 가게 리뷰 및 사진 |
| store | 가게 정보, 랭킹 |
| student | 학생 계정. 숭실대 유세인트 토큰으로 인증 |
| suggestion | 학생→학생회 제휴 건의 |

---

## 코드 컨벤션

### DTO

- **모든 DTO는 `record`로** 작성. 클래스명은 `~~DTO`로 통일.
- Request, Response 각각 별도 record. 중첩이 필요하면 내부 static record 사용.
- 모든 필드에 `@Schema(description = "...", example = "...")` 작성 필수.
- `@NotNull`, `@NotBlank`, `@Size`, `@Email` 등 제약 조건 명시.

```java
@Schema(description = "로그인 요청")
public record LoginRequestDTO(
    @Schema(description = "이메일", example = "user@soongsil.ac.kr")
    @NotBlank @Email
    String email,

    @Schema(description = "비밀번호 (평문)", example = "P@ssw0rd!")
    @NotBlank @Size(min = 8, max = 64)
    String password
) {}
```

### 변환 로직 (Converter → DTO static 메서드)

별도 Converter 클래스를 두지 않는다. DTO record 내 `static` 팩토리 메서드로 변환한다.

```java
public record StoreResponseDTO(
    @Schema(description = "가게 ID", example = "1") Long id,
    @Schema(description = "가게명", example = "숭실카페") String name
) {
    public static StoreResponseDTO from(Store store) {
        return new StoreResponseDTO(store.getId(), store.getName());
    }
}
```

### Service & Transaction

- `@Transactional`은 **서비스 구현 클래스 레벨**에 선언.
- 읽기 전용 서비스는 클래스에 `@Transactional(readOnly = true)`.
- 쓰기 작업이 포함된 개별 메서드에만 `@Transactional` 별도 선언.

```java
@Service
@Transactional(readOnly = true)
public class StoreServiceImpl implements StoreService {

    public StoreResponseDTO findStore(Long id) { ... }  // readOnly 상속

    @Transactional
    public void createStore(StoreRequestDTO req) { ... } // readOnly 오버라이드
}
```

### Enum 위치

- 도메인별 enum → 해당 도메인의 `entity/enums/` 패키지.
- 여러 도메인이 공유하는 enum → `domain/common/entity/enums/` 또는 `domain/common/enums/`.

### 필드명

의도가 드러나게 작성한다.

| 나쁜 예 | 좋은 예 |
|---------|---------|
| `password` | `hashedPassword` |
| `status` | `activationStatus` |
| `flag` | `isChatBlocked` |

### Swagger (@Schema)

- 모든 컨트롤러 메서드에 `@Operation(summary = "...")` 작성.
- 모든 DTO 필드에 `@Schema(description = "...", example = "...")` 작성.
- OpenAPI 3 (`springdoc-openapi`) 기준 어노테이션 사용.

### 기타

- 미사용 import, 로그, 메서드, 파일은 모두 제거 (IntelliJ: `Alt + Ctrl + O`).
- 불필요한 주석 제거. WHY가 명확하지 않은 경우에만 한 줄 주석 허용.
- 쿼리 내에 `lower()` 등 서비스 로직 삽입 금지.
- N+1 문제 주의. 필요 시 `@EntityGraph`, `fetch join` 사용.
- Entity에 무분별한 public setter 사용 금지. 행위 메서드로 대체.

---

## 코드 리뷰 기준

PR 리뷰 시 아래 우선순위로 검토한다.

1. **버그 가능성** — 잘못된 분기, NPE, 경계값 오류
2. **데이터 정합성** — 트랜잭션 누락, 중복 저장, unique 제약 미적용
3. **보안** — 인증 로직 누락, 민감 정보 노출
4. **성능** — N+1 쿼리, 불필요한 반복 조회
5. **설계** — 레이어 책임 분리, 도메인 로직 위치
6. **가독성** — 변수명, 메서드 분리, 불필요한 복잡성

### 체크리스트

- Controller → Service → Repository 역할 분리 명확
- 비즈니스 로직이 Service에 위치
- Entity에 무분별한 public setter 없음 (행위 메서드로 대체)
- `@Transactional` 필요한 위치에 선언 (readOnly 포함)
- N+1 문제 없음 (`@EntityGraph`, fetch join 활용)
- unique 제약 조건이 DB 레벨에서 보장
- `ErrorStatus` enum 기반 `GeneralException` 예외 처리
- 모든 DTO 필드에 `@Schema(description, example)` 작성
- 미사용 import/로그 없음
- enum이 `entity/enums/` 패키지에 위치
- `@BackofficeAudited`가 CUD 핸들러에만 적용 (GET 제외)

### 위험 신호 (반드시 지적)

- Entity setter 남용 → 행위 메서드(`member.activate()` 등)로 대체
- 트랜잭션 없이 엔티티 변경
- 인증 로직이 Controller에 위치
- unique 제약 없이 중복 생성 가능
- 비즈니스 로직이 여러 레이어에 분산

---

## 사용자 역할 (UserRole)

| 역할 | 설명 |
|------|------|
| STUDENT | 숭실대 유세인트 토큰으로 인증하는 학생 |
| ADMIN | 학생회. `SUSPEND` 상태로 가입 → 백오피스 승인 필요 |
| PARTNER | 제휴업체. `SUSPEND` 상태로 가입 → 백오피스 승인 필요 |
| BACKOFFICE | 운영자. `/auth/backoffice/login`으로만 로그인. `aud=backoffice` JWT 발급 |

---

## API 그룹 및 URL 패턴

| URL 접두사 | 대상 역할 | 설명 |
|---|---|---|
| `/auth/**` | 퍼블릭 | 인증/인가 (회원가입, 로그인, 토큰 갱신, 탈퇴) |
| `/auth/backoffice/**` | BACKOFFICE | 백오피스 전용 로그인/토큰 갱신 |
| `/backoffice/**` | BACKOFFICE | 운영자 전용 관리 API |
| `/admin/**` | ADMIN | 학생회 전용 API |
| `/partner/**` | PARTNER | 제휴업체 전용 API |
| `/students/**` | STUDENT | 학생 전용 API |
| `/partnership/**` | ADMIN/PARTNER/STUDENT | 제휴 제안서 관리 |
| `/store/**` | STUDENT/ADMIN/PARTNER | 가게 조회/랭킹 |
| `/map/**` | STUDENT/ADMIN/PARTNER | 지도 및 장소 검색 |
| `/chat/**` | ADMIN/PARTNER | 채팅 (REST) |
| `/ws/**` | 퍼블릭 | WebSocket 엔드포인트 |
| `/reviews/**` | 모든 역할 | 리뷰 CRUD |
| `/reports/**` | 모든 역할 | 신고 |
| `/notifications/**` | 모든 역할 | 알림 목록/설정 |
| `/inquiries/**` | STUDENT | 문의하기 |
| `/suggestion/**` | STUDENT/ADMIN | 제휴 건의 |
| `/members/**` | 모든 역할 | 프로필 이미지 관리 |
| `/device-tokens/**` | 모든 역할 | FCM 디바이스 토큰 |
| `/temporary-qr/**` | STUDENT | 임시 QR 스탬프 |
| `/certification/**` | STUDENT | QR 인증 (REST + WebSocket) |
| `/app-reviews/**` | STUDENT | 앱 리뷰 작성 |
| `/verify` | 퍼블릭 | QR 딥링크 리다이렉트 (Thymeleaf) |

퍼밋 없이 접근 가능한 엔드포인트는 `SecurityConfig`에 명시 (회원가입, 로그인, Swagger, actuator 등).

---

## 핵심 비즈니스 흐름

### 제휴(Partnership) 흐름

1. ADMIN이 초안 생성 (`POST /partnership/proposal/draft`)
2. 내용 작성 (`PATCH /partnership/proposal`)
3. PARTNER가 상태 변경 (승인/거절)
4. 수동 등록 (`POST /partnership/passivity`)으로 계약서 이미지와 함께 한 번에 생성도 가능
5. Paper가 `ACTIVE` 상태일 때 학생이 QR 스캔으로 혜택 사용 가능
6. `PaperScheduler`가 만료된 Paper를 자동으로 `INACTIVE`로 변경

### QR 인증 흐름

- 학생이 `/verify?storeId=`로 접근 → Thymeleaf 페이지에서 앱 딥링크로 리다이렉트
- 개인 인증: `POST /certification/personal`
- 그룹 인증: `POST /certification/session`으로 세션 생성 → WebSocket `/app/certify`로 그룹원 인증

### 알림(Notification) 흐름

1. 알림 생성 시 `NotificationOutbox` 테이블에 저장 (Outbox 패턴)
2. `OutboxAfterCommitPublisher`가 트랜잭션 커밋 후 RabbitMQ에 발행
3. `NotificationListener`(Consumer)가 FCM으로 실제 전송
4. 실패 시 최대 3회 자동 재시도, 이후 백오피스에서 수동 재전송 가능

---

## 인증 흐름

1. JWT는 `Authorization: Bearer <token>` 헤더로 전달
2. `JwtAuthFilter` → `RoutingAuthenticationProvider` → 역할별 `UserDetailsService` 체인
3. BACKOFFICE는 별도 audience(`aud=backoffice`) 클레임으로 일반 토큰과 분리
4. Refresh Token은 Redis에 저장, 토큰 회전(rotate) 방식 사용
5. 로그아웃 시 Access Token을 Redis 블랙리스트에 등록

---

## 공통 응답 형식

모든 API는 `BaseResponse<T>` 래퍼로 응답한다:

```json
{
  "isSuccess": true,
  "code": "COMMON200",
  "message": "요청에 성공하였습니다.",
  "result": { ... }
}
```

- 에러 코드는 `ErrorStatus` enum에 HTTP 상태 + 커스텀 코드 + 메시지로 정의
- `GeneralException(ErrorStatus)` throw → `GlobalExceptionAdvice`에서 일괄 처리

---

## 특수 어노테이션

### @BackofficeAudited

백오피스 **CUD(Create/Update/Delete)** 핸들러에만 적용. GET 조회에는 불필요.

```java
@BackofficeAudited(action = "MEMBER_APPROVE", targetId = "#memberId")
public void approveMember(Long memberId) { ... }
```

`targetId`는 SpEL 표현식으로 메서드 파라미터 참조 가능.

### @CheckPage

페이지 번호 유효성 검증 어노테이션 (`global/exception/annotation/`).

---

## Map API 역할별 응답 분기

`GET /map/nearby`, `GET /map/search`는 로그인 역할에 따라 다른 타입을 반환한다:

| 역할 | 반환 타입 |
|------|-----------|
| STUDENT | `StoreMapResponseDTO` |
| ADMIN | `PartnerMapResponseDTO` |
| PARTNER | `AdminMapResponseDTO` |

---

## Git Convention

### Branch

```
<type>/#<issue-number>-<brief-english-description>
```

```
feat/#123-add-review-api
fix/#346-fix-null-pointer-login
refactor/#354-cleanup-partnership-period
hotfix/#390-fix-prod-token-expiry
```

허용 타입: `feat`, `fix`, `refactor`, `style`, `docs`, `test`, `chore`, `hotfix`

슬래시 커맨드: `/branch`

### Commit

```
[TYPE/#issue-number] 한글로 간결하게

- 작업 내용 상세 1
- 작업 내용 상세 2
```

```
[FEAT/#123] 리뷰 생성 API 추가

- ReviewController에 POST /reviews 엔드포인트 추가
- ReviewServiceImpl에 createReview 메서드 구현
- 리뷰 이미지 S3 업로드 로직 연동
```

```
[FIX/#346] 로그인 시 NPE 수정

- JwtAuthFilter에서 null 토큰에 대한 방어 처리 추가
```

허용 타입: `FEAT`, `FIX`, `REFACTOR`, `STYLE`, `DOCS`, `TEST`, `CHORE`, `HOTFIX`, `MERGE`

슬래시 커맨드: `/commit`

### PR

`.github/PULL_REQUEST_TEMPLATE.md` 기준으로 작성:

```markdown
## #️⃣연관된 이슈
> close #이슈번호

## 📝작업 내용
> 무엇을 왜 변경했는지

## 🔎코드 설명(스크린샷(선택))
> 핵심 변경 사항 설명

## 💬고민사항 및 리뷰 요구사항 (Optional)
> 리스크, 테스트 누락, 배포 고려사항

## 비고 (Optional)
> 참고 링크 등
```

슬래시 커맨드: `/pr`

---

## 이슈 템플릿

| 타입 | 파일 | 슬래시 커맨드 |
|------|------|---------------|
| 기능 추가 | `.github/ISSUE_TEMPLATE/feature.md` | `/feature-issue` |
| 버그 수정 | `.github/ISSUE_TEMPLATE/fix.md` | `/fix-issue` |
| 리팩토링 | `.github/ISSUE_TEMPLATE/refactor.md` | `/refactor-issue` |
| 유지보수 | `.github/ISSUE_TEMPLATE/chore.md` | `/chore-issue` |
| 긴급 수정 | `.github/ISSUE_TEMPLATE/hotfix.md` | `/hotfix-issue` |
| 테스트 | `.github/ISSUE_TEMPLATE/test.md` | — |
| 문서 | `.github/ISSUE_TEMPLATE/docs.md` | — |

---

## 테스트 가이드

- 테스트 메서드명은 **영어**로 작성.
- given/when/then 흐름이 명확한 경우 주석으로 구분.
- 테스트 DB는 H2 인메모리 (`src/test/resources/application-test.yml`).
- PR 오픈 전 `./gradlew test` 실행 확인.

```java
@Test
void createReview_whenValidRequest_thenSuccess() {
    // given
    ...
    // when
    ...
    // then
    ...
}
```
