# Gemini Instructions — Assu Backend

- 모든 답변은 **한국어**로 작성한다.
- 코드 블록 내 주석은 작성하지 않는다.
- import 시 와일드카드(`*`)를 사용하지 않는다.
- PR 리뷰 시 구체적이고 실무적인 기준으로 피드백을 제공한다.
- 논리적 근거 기반으로 제안한다.

## 코드 컨벤션

- **DTO**: `record`로 작성. 클래스명 `~~DTO`. 모든 필드에 `@Schema(description, example)` 작성.
- **Converter**: 별도 클래스 금지. DTO record 내 `static` 팩토리 메서드로 변환.
- **@Transactional**: 서비스 구현 클래스 레벨에 선언. 읽기 전용은 `readOnly = true`.
- **Enum**: `<domain>/entity/enums/` 패키지에 위치.
- **필드명**: 의도 명시. `password` → `hashedPassword`.
- **미사용 코드**: import, 로그, 메서드 모두 제거.

## 커밋 컨벤션

```text
[TYPE/#issue-number] 한글로 간결하게

- 작업 내용 상세 1
- 작업 내용 상세 2
```

허용 타입: `FEAT`, `FIX`, `REFACTOR`, `STYLE`, `DOCS`, `TEST`, `CHORE`, `HOTFIX`, `MERGE`

예시:
```text
[FEAT/#123] 리뷰 생성 API 추가

- ReviewController에 POST /reviews 엔드포인트 추가
- ReviewServiceImpl에 createReview 메서드 구현
```

## 주석 컨벤션

- 주석은 가능하면 한 줄로 작성한다.
- WHY가 명확하지 않은 경우에만 작성하고, WHAT 설명은 생략한다.
- 미결 사항이나 잠재적 이슈는 IntelliJ 기본 `FIXME` 형식으로 표시한다.

## 테스트 컨벤션

- 테스트 메서드명은 **영어**로 작성한다.
- given/when/then 흐름이 명확한 경우 주석으로 구분한다.

## PR 리뷰 기준 (우선순위 순)

1. 버그 가능성 (NPE, 잘못된 분기, 경계값)
2. 데이터 정합성 (트랜잭션 누락, unique 제약 미적용)
3. 보안 (인증 로직 누락, 민감 정보 노출)
4. 성능 (N+1 쿼리, 중복 조회)
5. 설계 (레이어 책임 분리, 도메인 로직 위치)
6. 가독성 (변수명, 메서드 분리)

이 컨벤션을 따라 코드를 검토하거나 제안할 때 적용하라.
