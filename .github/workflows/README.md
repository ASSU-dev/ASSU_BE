# CI/CD 워크플로우 가이드

ASSU 서버 repo 의 GitHub Actions 워크플로우는 **환경(dev / prod)** 과 **배포 방식(Docker EC2 / ArgoCD K3S)** 의 조합으로 분리되어 있습니다.

## 파일 구성

```text
.github/workflows/
├── ci.yml                          # PR / develop 대상 빌드 검증
├── cd-prod-argocd.yml              # prod → ArgoCD GitOps (활성)
├── cd-dev-docker.yml               # dev  → 기존 TEST EC2  (활성)
└── unused/                         # 비활성화 보관
    ├── cd-prod-docker.yml          # prod 의 legacy 경로 (ArgoCD 안정화 전까지 rollback 용)
    └── cd-dev-argocd.yml           # dev K3S 준비 후 활성화 예정
```

| 파일 | 환경 | 방식 | 트리거 브랜치 | 대상 인프라 |
|---|---|---|---|---|
| `cd-prod-docker.yml` | prod | Docker Compose (legacy) | `main` | `EC2_HOST` |
| `cd-prod-argocd.yml` | prod | ArgoCD GitOps (K3S) | `main` | `assu-prod` namespace |
| `cd-dev-docker.yml` | dev | Docker Compose (legacy) | `develop` | `TEST_EC2_HOST` |
| `cd-dev-argocd.yml` | dev | ArgoCD GitOps (K3S) | `develop` | `assu-dev` namespace |

## 비활성화 컨벤션: `.github/workflows/unused/`

GitHub Actions 는 **`.github/workflows/` 직속 yml 파일만** 워크플로우로 등록합니다. 하위 폴더 파일은 워크플로우로 인식되지 않으므로, 일시적으로 끄고 싶은 파일은 `unused/` 로 옮겨 둡니다.

- Actions 탭에서 사라지고 `workflow_dispatch` 도 노출되지 않아 **실수 트리거가 원천 차단**됩니다.
- 다시 켤 때는 `git mv` 한 줄: `git mv .github/workflows/unused/foo.yml .github/workflows/foo.yml`

## Docker vs ArgoCD 동작 차이

| 항목 | Docker (`cd-*-docker.yml`) | ArgoCD (`cd-*-argocd.yml`) |
|---|---|---|
| 흐름 | DockerHub push → SSH 로 EC2 에 접속해 `docker compose pull && up` | DockerHub push → manifest repo `*/deployment.yml` 의 image 태그 갱신 커밋 → ArgoCD 가 K3S 로 자동 sync |
| 이미지 태그 | `:latest` 를 EC2 가 pull (변경점 식별은 SHA 태그로 별도 push) | `:{sha}` 로 deployment.yml 을 고정 (rollback 용이) |
| 필요 secret | `EC2_HOST` / `EC2_SSH_KEY` 또는 `TEST_EC2_HOST` / `TEST_EC2_SSH_KEY`, `SERVICE_ACCOUNT_B64`, DockerHub | `ACTION_TOKEN` (manifest repo write 권한), DockerHub |
| 배포 검증 | `/actuator/health` 폴링 + Discord 알림 | ArgoCD 가 sync / 헬스체크. 결과는 클러스터 / ArgoCD UI 에서 확인 |
| 언제 사용 | K3S 전환 전, 또는 ArgoCD 장애 시 rollback 경로 | K3S 전환 완료 후 표준 경로 |