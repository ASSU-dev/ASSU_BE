# ASSU 유비쿼터스 언어 (Ubiquitous Language)

> 팀 내 모든 구성원(기획, 개발, 운영)이 동일한 의미로 사용하는 용어를 정의합니다.
> 코드의 클래스명·변수명·API 파라미터명은 이 문서의 영문 용어를 기준으로 합니다.

---

## 1. 핵심 주체 (Actors)

| 한글 | 영문 (코드) | 설명 |
|------|------------|------|
| 사용자 | `Member` | 모든 사용자의 공통 기반. 역할(`role`)에 따라 학생/관리자/제휴업체/백오피스로 분류 |
| 학생 | `Student` | 숭실대학교 학생. 제휴처 이용, 리뷰 작성, 건의 등의 주체 |
| 행정관리자 | `Admin` | 숭실대 학생회. 제휴 계약 관리 및 학생 담당 |
| 제휴업체 | `Partner` | 숭실대와 제휴 계약을 맺은 가게/업체 |
| 백오피스 관리자 | `BackofficeUser` | 서비스 운영팀. 회원·신고·알림 등 전반적인 운영 관리 |

### 사용자 역할 (`UserRole`)

| 값 | 설명 | URL prefix |
|----|------|-----------|
| `STUDENT` | 학생 | `/student/**` |
| `ADMIN` | 행정관리자 | `/admin/**` |
| `PARTNER` | 제휴업체 | `/partner/**` |
| `BACKOFFICE` | 백오피스 관리자 | `/backoffice/**` |

### 계정 활성화 상태 (`ActivationStatus`)

| 값 | 설명 |
|----|------|
| `ACTIVE` | 정상 활성화된 계정 |
| `INACTIVE` | 비활성화된 계정 |
| `SUSPEND` | 정지된 계정 (신고 누적 등) |

---

## 2. 제휴 시스템 (Partnership Domain)

ASSU의 핵심 도메인. 숭실대학교와 제휴업체 간의 계약을 관리하고 학생이 혜택을 이용하는 전체 흐름을 담당한다.

### 용어 정의

| 한글 | 영문 (코드) | 설명 |
|------|------------|------|
| 제휴 | Partnership | 숭실대학교와 협력업체 간의 계약 관계 전반 |
| 제안서 | `Paper` | 제휴 조건(할인율, 대상 인원, 혜택 등)을 명시한 계약 문서 |
| 제안 옵션 | `PaperContent` | Paper 내 개별 혜택 옵션. 예) "10인 이상 방문 시 20% 할인" |
| 상품/혜택 | `Goods` | PaperContent의 세부 혜택 항목 |
| 이용내역 | `PartnershipUsage` | 학생이 제휴처를 이용한 기록 |

### 제안 기준 타입 (`CriterionType`)

| 값 | 설명 |
|----|------|
| `PRICE` | 금액 기준 (예: 3만원 이상 이용 시) |
| `HEADCOUNT` | 인원 기준 (예: 5인 이상 방문 시) |

### 제안 옵션 타입 (`OptionType`)

| 값 | 설명 |
|----|------|
| `SERVICE` | 서비스 제공 (예: 음료 1잔 무료) |
| `DISCOUNT` | 할인 적용 (예: 총액의 10% 할인) |

### 제휴 흐름

```
Admin이 Paper 작성 (PaperContent + Goods 포함)
    ↓
Student가 혜택 조회
    ↓
Student가 제휴처 방문 → Certification(인증)
    ↓
PartnershipUsage 기록 생성
    ↓
Student가 Review 작성
```

---

## 3. 매장 (Store Domain)

| 한글 | 영문 (코드) | 설명 |
|------|------------|------|
| 매장 / 편의점 | `Store` | 제휴업체(`Partner`)가 운영하는 실제 점포. 위치 정보(위도/경도)와 평점 포함 |
| 평점 | `rate` | 리뷰 점수를 집계한 매장 평균 평점 |

---

## 4. 인증 (Certification Domain)

제휴처 방문을 QR 코드로 확인하는 시스템.

| 한글 | 영문 (코드) | 설명 |
|------|------------|------|
| 인증 세션 | `AssociateCertification` | 제휴처 방문 시 생성되는 인증 세션. 테이블 번호·인원수 포함 |
| QR 인증 | `QRCertification` | 학생이 QR 스캔으로 인증 세션에 참여한 결과 |
| QR 코드 | `Qr` | 인증에 사용되는 QR 코드 데이터 |

### 인증 세션 상태 (`SessionStatus`)

| 값 | 설명 |
|----|------|
| `OPENED` | 인증 세션 진행 중 |
| `COMPLETED` | 인증 완료 |
| `EXPIRED` | 세션 만료 |

---

## 5. 리뷰 (Review Domain)

| 한글 | 영문 (코드) | 설명 |
|------|------------|------|
| 리뷰 | `Review` | 학생이 제휴처 이용 후 작성하는 평점과 후기 |
| 리뷰 사진 | `ReviewPhoto` | 리뷰에 첨부된 사진 (S3 저장) |

---

## 6. 채팅 (Chat Domain)

행정관리자(`Admin`)와 제휴업체(`Partner`) 간의 1:1 메시지 시스템.

| 한글 | 영문 (코드) | 설명 |
|------|------------|------|
| 채팅방 | `ChattingRoom` | Admin과 Partner의 1:1 채팅 채널 |
| 메시지 | `Message` | 채팅방 내 개별 메시지 |
| 차단 | `Block` | 특정 사용자의 메시지 수신을 차단하는 관계 |

### 메시지 타입 (`MessageType`)

| 값 | 설명 |
|----|------|
| `TEXT` | 일반 텍스트 메시지 |
| `PROPOSAL` | 제안서 관련 메시지 |
| `SYSTEM` | 시스템 자동 발송 메시지 |
| `GUIDE` | 안내 메시지 |

---

## 7. 알림 (Notification Domain)

Outbox 패턴 기반의 푸시 알림 시스템.

| 한글 | 영문 (코드) | 설명 |
|------|------------|------|
| 알림 | `Notification` | 사용자에게 전달되는 인앱/푸시 알림 |
| 알림 설정 | `NotificationSetting` | 사용자별 알림 타입 ON/OFF 설정 |
| 발송 이력 | `NotificationOutbox` | 알림 발송 상태를 추적하는 Outbox 테이블 |
| 디바이스 토큰 | `DeviceToken` | FCM 푸시 발송에 사용되는 기기 식별 토큰 |

### 알림 타입 (`NotificationType`)

| 값 | 설명 |
|----|------|
| `CHAT` | 채팅 메시지 수신 |
| `PARTNER_SUGGESTION` | 제휴 건의 관련 |
| `PARTNER_PROPOSAL` | 제휴 제안 관련 |
| `STAMP` | 스탬프 이벤트 |
| `BACKOFFICE` | 백오피스 수동 발송 |

### 발송 상태 (`NotificationOutbox.Status`)

| 값 | 설명 |
|----|------|
| `PENDING` | 발송 대기 |
| `SENDING` | 발송 중 |
| `DISPATCHED` | 큐에 배분됨 |
| `SENT` | 발송 완료 |
| `FAILED` | 발송 실패 (최대 3회 재시도 후) |

---

## 8. 신고 (Report Domain)

| 한글 | 영문 (코드) | 설명 |
|------|------------|------|
| 신고 | `Report` | 부적절한 사용자/리뷰/건의글에 대한 신고 |

### 신고 대상 타입 (`ReportTargetType`)

| 값 | 설명 |
|----|------|
| `STUDENT_USER` | 학생 사용자 신고 |
| `REVIEW` | 리뷰 신고 |
| `SUGGESTION` | 건의글 신고 |

### 신고 처리 상태 (`ReportStatus`)

| 값 | 설명 |
|----|------|
| `PENDING` | 처리 대기 |
| `PROCESSED` | 처리 완료 |
| `REJECTED` | 기각 |

### 콘텐츠 신고 상태 (`ReportedStatus`)

리뷰, 건의글 등 콘텐츠에 적용.

| 값 | 설명 |
|----|------|
| `NORMAL` | 정상 |
| `REPORTED` | 신고 접수됨 |
| `DELETED` | 삭제됨 |

---

## 9. 건의 (Suggestion Domain)

| 한글 | 영문 (코드) | 설명 |
|------|------------|------|
| 건의 | `Suggestion` | 학생이 새 매장 추가를 요청하거나 의견을 제출하는 기능 |

---

## 10. 문의 (Inquiry Domain)

| 한글 | 영문 (코드) | 설명 |
|------|------------|------|
| 문의 | `Inquiry` | 사용자가 제출하는 1:1 고객 문의 |

### 문의 상태 (`Inquiry.Status`)

| 값 | 설명 |
|----|------|
| `WAITING` | 답변 대기 중 |
| `ANSWERED` | 답변 완료 |

---

## 11. 인증/로그인 (Auth Domain)

| 한글 | 영문 (코드) | 설명 |
|------|------------|------|
| 일반 인증 | `CommonAuth` | 이메일/비밀번호 기반 인증 |
| 숭실대 인증 | `SSUAuth` | 학번 기반 숭실대 유세인트 연동 인증 |

### JWT 토큰 구분 (`AuthRealm`)

| 값 | 설명 |
|----|------|
| `app` | 일반 사용자(Student/Admin/Partner) 토큰 |
| `backoffice` | 백오피스 전용 토큰 (혼용 불가) |

---

## 12. 공통 Enum

### 학적 상태 (`EnrollmentStatus`)

| 값 | 설명 |
|----|------|
| `ENROLLED` | 재학 |
| `LEAVE` | 휴학 |
| `GRADUATED` | 졸업 |

### 단과대 (`Department`)

`HUMANITIES`(인문대) / `NATURAL_SCIENCE`(자연과학대) / `LAW`(법과대) / `SOCIAL_SCIENCE`(사회과학대) / `ECONOMICS`(경제통상대) / `BUSINESS`(경영대) / `ENGINEERING`(공과대) / `IT`(IT대) / `LIBERAL_STUDIES`(자유전공) / `AI`(AI대)

---

## 13. 공통 변수명 규칙

| 패턴 | 의미 |
|------|------|
| `*Id` | 엔티티 기본키 또는 외래키 (예: `memberId`, `storeId`) |
| `is*` | boolean 플래그 (예: `isActivated`, `isRead`, `isCertified`) |
| `*At` | 시각 정보 (예: `createdAt`, `deletedAt`, `readAt`) |
| `*Url` | 외부 리소스 URL (예: `profileUrl`, `licenseUrl`) |
| `*Key` | S3 오브젝트 키 (예: `contractImageKey`, `keyName`) |
| `point` | PostGIS 지리 좌표 (`latitude` + `longitude` 조합) |

---

## 14. 도메인 간 관계 요약

```
Member (기반)
├── Student ─── Review, PartnershipUsage, Suggestion, Certification
├── Admin ─────── Paper, ChattingRoom, Suggestion(담당)
├── Partner ────── Store, Paper, ChattingRoom
└── BackofficeUser ─ 운영 전반 관리

Store ─── Review, Paper, Certification

Paper (제안서)
├── PaperContent (옵션) ── Goods (혜택)
└── PartnershipUsage (이용내역)

Notification ── NotificationOutbox ── DeviceToken
Report ── (대상: Student / Review / Suggestion)
ChattingRoom ── Message
                Block (차단)
```