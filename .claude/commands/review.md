---
description: Review a pull request or code diff using Assu backend review standards.
argument-hint: "<PR number or leave empty to review current diff>"
allowed-tools: Bash(git diff), Bash(git diff --stat), Bash(git log --oneline -10), Bash(gh pr diff), Bash(gh pr view)
---

You are reviewing backend code for the Assu project.

Use Korean.

Review focus order (highest to lowest priority):

1. **버그 가능성** — 잘못된 분기, NPE, 경계값 오류
2. **데이터 정합성** — 트랜잭션 누락, 중복 저장, unique 제약 미적용
3. **보안** — 인증 로직 누락, 민감 정보 노출
4. **성능** — N+1 쿼리, 불필요한 반복 조회
5. **설계** — 레이어 책임 분리, 도메인 로직 위치
6. **가독성** — 변수명, 메서드 분리, 복잡성

Input from the user:

```text
$ARGUMENTS
```

Process:

1. If a PR number is given, run `gh pr diff <number>` and `gh pr view <number>` for context. Otherwise run `git diff` for current changes.
2. Check `git log --oneline -10` for context.
3. Apply review checklist below.
4. Output findings grouped by file, sorted by priority.

## Review Checklist

### 아키텍처 / 설계
- Controller → Service → Repository 역할 분리가 명확한가?
- 비즈니스 로직이 Service에 위치하는가?
- Repository에 불필요한 로직(lower() 등)이 없는가?

### 도메인 모델 (Entity)
- 무분별한 public setter 사용 없음 (행위 메서드로 대체)
- soft delete 정책 일관성 (`deletedAt`, `isActivated`)

### 인증 / 보안
- JWT 인증 로직이 Controller가 아닌 Security Filter에 위치
- 민감 정보(비밀번호, 토큰)가 로그에 출력되지 않음
- BACKOFFICE 전용 엔드포인트에 `aud=backoffice` JWT 검증 존재

### 트랜잭션
- 서비스 클래스에 `@Transactional` 선언 (readOnly=true 적절히 활용)
- 조회 후 엔티티 수정 시 트랜잭션 존재 여부

### 성능 / 쿼리
- N+1 문제 가능성 (`@EntityGraph`, `fetch join` 활용 여부)
- find 후 다시 조회하는 중복 쿼리

### DTO / API
- 모든 DTO가 `record` 형식
- `@Schema(description, example)` 작성 여부
- Entity 직접 반환 여부 (금지)
- DTO 내 static 변환 메서드 사용 (별도 Converter 클래스 금지)

### 예외 처리
- `ErrorStatus` enum 기반 `GeneralException` 사용
- 의미 없는 RuntimeException throw 없음

### 기타
- 미사용 import, 로그, 메서드 없음
- enum이 `entity/enums/` 패키지에 위치
- `@BackofficeAudited`가 CUD 핸들러에만 적용 (GET 제외)

## 위험 신호 (반드시 지적)

- Entity setter 남용 → 행위 메서드로 대체 필요
- 트랜잭션 없는 엔티티 변경
- 인증 로직이 Controller에 위치
- unique 제약 없이 중복 생성 가능

## 출력 형식

```text
### [파일명] 문제점
- 설명

### 왜 문제인가
- 설명

### 개선 방법
- 설명 (가능하면 코드 예시 포함)
```

단순 스타일 지적보다 치명적인 설계/정합성 문제를 우선 지적하고, "취향" 수준의 nitpick은 지양한다.
