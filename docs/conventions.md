# ASSU_BE 개발 컨벤션 가이드

> AI Native 개발 환경에서 Claude Code 등의 AI 도구가 이 레포지토리의 규칙을 즉시 파악할 수 있도록 정리한 문서입니다.

---

## 1. 이슈 컨벤션

### 이슈 제목 형식

```
[TYPE] 한글 설명
```

| TYPE | 의미 |
|------|------|
| `FEAT` | 새 기능 추가 |
| `FIX` | 버그 수정 |
| `REFACTOR` | 코드 리팩토링 |
| `TEST` | 테스트 코드 작성 |
| `DOCS` | 문서 작성 |
| `MOD` | 기타 수정 (설정, 스타일 등) |

**예시**
- `[FEAT] 백오피스 푸시 알림 그룹 발송 구현`
- `[FIX] 알림 ALL 토글 버그 수정`

### 이슈 본문 템플릿

```markdown
## 📝 Description
> 이슈에 대해 간결하게 설명해주세요.

## 📝 Todo
- [ ] 작업 항목 1
- [ ] 작업 항목 2 (작업자가 완료하면 체크, PM이 진척도 파악 가능)

## 📝 참고 사항
> 작업자가 참고해야 할 내용 (레퍼런스, 스크린샷 등)
```

### 라벨

| 라벨 | 사용 시점 |
|------|-----------|
| `:sparkles: feature` | 새 기능 / 개선 작업 |
| `:bug: bug` | 버그 수정 |
| `:recycle: refactor` | 리팩토링 |
| `:memo: docs` | 문서 작업 |
| `🚀 deploy` | 배포 관련 작업 |

---

## 2. 브랜치 컨벤션

### 브랜치 이름 형식

```
type/#이슈번호-kebab-case-설명
```

**type은 소문자로**, 이슈 번호 뒤에 짧은 영문 kebab-case 설명을 붙입니다.

| 브랜치 prefix | 용도 |
|--------------|------|
| `feat/` | 새 기능 개발 |
| `fix/` | 버그 수정 |
| `refactor/` | 리팩토링 |
| `test/` | 테스트 코드 |
| `mod/` | 기타 수정 |

**예시**
```
feat/#395-ai-native-setup
fix/#385-notification-all-toggle
feat/#359-admin-api-authorization
refactor/#232-sumin-refactoring-1
```

### 브랜치 전략

```
feature 브랜치 → develop → main
```

- 모든 작업 브랜치는 `develop` 기준으로 생성
- PR은 `develop`으로 머지
- `develop` → `main` 은 배포 시점에 진행
- CI는 `develop` 브랜치 push/PR에서 자동 실행

---

## 3. 커밋 컨벤션

### 커밋 메시지 형식

```
[TYPE/#이슈번호] 한글 설명
```

**예시**
```
[FEAT/#395] AI Native 개발 환경 세팅을 위한 conventions 문서 작성
[FIX/#385] 알림 ALL 토글 버그 수정
[TEST/#359] 백오피스 테스트
[REFACTOR/#232] 알림 서비스 코드 분리
```

> 커밋 메시지에 `Co-Authored-By` 등의 자동 생성 주석을 붙이지 않습니다.

---

## 4. PR 컨벤션

### PR 제목 형식

```
[TYPE/#이슈번호] 한글 설명
```

커밋 메시지 형식과 동일합니다. 배포 PR은 `[DEPLOY]` 타입을 사용합니다.

| 상황 | 예시 |
|------|------|
| 일반 작업 PR | `[FEAT/#395] AI Native 개발 환경 세팅` |
| 버그 수정 PR | `[FIX/#385] 알림 ALL 토글 버그 수정` |
| 배포 PR | `[DEPLOY] v.71 백오피스 기능 추가` |

### PR 대상 브랜치

| PR 종류 | head → base |
|---------|------------|
| 일반 작업 | `feat/fix/refactor/... 브랜치` → `develop` |
| 배포 | `develop` → `main` |

> 대부분의 PR은 `develop`으로 머지합니다. `main`으로 직접 올리는 경우는 배포 시점에만 진행합니다.

### PR 본문 템플릿

```markdown
## #️⃣연관된 이슈
> #이슈번호

## 📝작업 내용
> 작업한 내용을 작성해주세요.

## 🔎코드 설명(스크린샷(선택))
> 코드에 대한 설명을 작성해주세요.

## 💬고민사항 및 리뷰 요구사항 (Optional)
> 고민사항 및 의견 받고 싶은 부분

## 비고 (Optional)
> 참고 링크, 레퍼런스 등
```

---

## 5. AI Native 개발 환경

### Claude Code 활용

이 레포지토리는 Claude Code 기반 AI Native 워크플로우를 지원합니다.

- `CLAUDE.md` — Claude Code가 자동으로 로딩하는 프로젝트 컨텍스트 파일 (빌드 명령, 아키텍처, 패턴 등)
- `docs/conventions.md` (이 파일) — 이슈/브랜치/커밋/PR 규칙 정리

### 작업 흐름 (AI 지원)

1. 작업 목표 설명 → AI가 GitHub 이슈 생성
2. AI가 컨벤션에 맞는 브랜치 생성 및 체크아웃
3. 코드 작업 진행 (AI 페어 프로그래밍)
4. AI가 커밋 메시지 컨벤션에 맞게 커밋
5. AI가 PR 템플릿에 맞게 PR 생성

### 관련 설정 파일

| 파일 | 역할 |
|------|------|
| `CLAUDE.md` | Claude Code 프로젝트 컨텍스트 (빌드/아키텍처/패턴) |
| `docs/conventions.md` | 이슈·브랜치·커밋·PR 컨벤션 정리 (이 파일) |
| `.github/ISSUE_TEMPLATE/feature.md` | GitHub 이슈 템플릿 |
| `.github/PULL_REQUEST_TEMPLATE.md` | GitHub PR 템플릿 |
| `.github/workflows/ci.yml` | CI 파이프라인 (develop 브랜치 자동 빌드·테스트) |