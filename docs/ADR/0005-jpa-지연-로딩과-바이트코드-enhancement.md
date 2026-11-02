# ADR-0005: JPA 연관관계는 지연 로딩을 기본으로 하고, 바이트코드 enhancement를 사용한다

- 상태: 채택 (2026-11-02)
- 관련: #546, #547, #516

## 맥락
- 부하테스트(#516)에서 가장 느린 학생 API 3개는 요청 하나에 SQL을 35개씩 실행하고 있었다.
- 모든 API가 거치는 JWT 인증 필터의 회원 조회도 SQL이 3개였다.
- 원인은 세 가지였다.
  1. `UserPaper.paperContent` 지연 로딩 N+1
  2. `Admin`·`Partner`·`Student`의 `@OneToOne Member member`가 기본값 EAGER
  3. Member의 역방향(`mappedBy`) `@OneToOne` 6개: `studentProfile`, `adminProfile`, `partnerProfile`, `backofficeProfile`, `ssuAuth`, `commonAuth`
     - 이 중 `ssuAuth`와 `commonAuth`는 `LAZY`로 선언돼 있었지만 실제로는 지연 로딩되지 않았다.
     - 역방향 `@OneToOne`은 외래 키가 상대 테이블에 있다. Hibernate가 연관 객체의 존재 여부를 알려면 조회해야 하므로, 프록시를 만들 수 없다.

## 결정
- `build.gradle`에 `org.hibernate.orm` 플러그인을 추가해 바이트코드 enhancement를 켠다. 플러그인 버전은 `hibernate-core` 버전과 맞춘다.
- `@OneToOne`, `@ManyToOne`은 모두 `fetch = FetchType.LAZY`를 명시한다. 두 어노테이션의 기본값은 EAGER다.
- `hibernate.default_batch_fetch_size: 100`을 둔다. 남은 지연 로딩은 `IN (...)` 한 번으로 묶인다.
- 인증 경로의 회원 조회는 `MemberRepository.findWithProfilesById`(`@EntityGraph`)로 프로필 6개를 한 번에 불러온다.

### 검토한 대안

| 방법 | 기각 이유 |
|---|---|
| 역방향 필드 제거 후 Repository로 조회 | 사용처 약 60곳 수정(인증, 회원가입, 탈퇴, 백오피스) |
| EAGER 유지 + fetch join 추가 | 역방향 `@OneToOne`의 추가 SELECT는 fetch join 없이 Member를 읽는 모든 곳에서 계속 발생 |

## 결과
- 학생 제휴 API의 요청당 쿼리: 35 → 2
- 인증 필터의 회원 조회: 3 → 1
- 이제 연관관계가 **실제로** 지연 로딩된다. 그래서 세션(트랜잭션·OSIV) 밖에서 연관에 접근하면 `LazyInitializationException`이 난다. 아래 주의사항을 지킨다.

## 이후 작업 시 주의사항

### 1. Member에 연관을 추가하면 EntityGraph에도 넣는다
- JWT 필터(`JwtAuthFilter` → `JwtUtil.buildAuthentication`)는 OSIV보다 **먼저** 실행된다.
  - 그래서 `PrincipalDetails.getMember()`는 세션이 닫힌 detached 엔티티다.
  - OSIV가 켜져 있어도 컨트롤러에서 이 Member의 연관을 지연 로딩할 수 없다.
- STOMP(WebSocket)는 CONNECT 때 만든 같은 Member를 세션이 끝날 때까지 재사용한다.
- 해야 할 것: Member에 LAZY 연관을 추가하고 Principal에서 꺼내 쓸 계획이라면, `findWithProfilesById`의 `attributePaths`에 추가한다.
- 그래프에 없는 연관이 필요하면 Principal의 Member를 쓰지 말고, 서비스의 트랜잭션 안에서 `memberId`로 다시 조회한다.

### 2. 세션 밖에서 엔티티 연관을 탐색하지 않는다
아래 실행 맥락에는 OSIV가 없다. 연관을 탐색하는 메서드에 `@Transactional(readOnly = true)`를 붙이거나, 필요한 연관을 fetch join으로 미리 불러온다.
- `@Scheduled`
- `@RabbitListener`
- `@Async`
- `@TransactionalEventListener(AFTER_COMMIT)`
- STOMP `@MessageMapping`
- Filter·Interceptor

### 3. 새 연관관계는 LAZY로 선언한다
- `@ManyToOne`, `@OneToOne`은 `fetch = FetchType.LAZY`를 명시한다.
- 화면에 필요한 연관은 쿼리에서 `JOIN FETCH`나 `@EntityGraph`로 가져온다.
- 컬렉션(`@OneToMany`)을 fetch join하면 행이 곱해진다. 컬렉션은 별도 `IN` 조회나 batch fetch로 가져온다.

### 4. 쿼리 수 테스트를 유지한다
- `StudentPartnershipQueryCountTest`는 Hibernate `Statistics`로 요청당 SQL 수의 상한을 검사한다.
- 이 테스트가 실패하면 상한을 올리지 말고, 어떤 연관이 추가 조회를 만드는지 `show_sql` 로그로 먼저 확인한다.
- 다른 조회 API의 N+1을 고칠 때도 같은 방식으로 테스트를 추가한다.
  - 영속성 컨텍스트를 비운다(`em.clear()`).
  - `statistics.clear()`로 카운터를 초기화한다.
  - 대상 메서드를 호출한다.
  - `getPrepareStatementCount()`를 검사한다.

### 5. enhancement 관련 주의
- 엔티티의 지연 로딩 필드는 getter로 접근한다. 리플렉션으로 필드를 직접 읽으면 초기화되지 않은 값을 볼 수 있다.
- Hibernate 버전을 올릴 때 `org.hibernate.orm` 플러그인 버전도 함께 올린다. 두 버전이 다르면 빌드나 실행 시점에 오류가 날 수 있다.
- 엔티티를 Jackson으로 직접 직렬화하지 않는다. 응답은 DTO로 변환한다.

### 6. 알려진 사각지대
- `GroupCertificationController`는 예외를 `catch (Exception)`으로 삼키고 `null`을 반환한다. 이 경로에서 `LazyInitializationException`이 나도 클라이언트에는 드러나지 않는다.
- 그룹 QR 인증을 수정한 뒤에는 서버 로그를 확인한다.
