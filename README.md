# 🎓 A:SSU (숭실대학교 종합 제휴 플랫폼)
<img width="1920" height="1080" alt="예비" src="https://github.com/user-attachments/assets/099ac5d9-2208-4aa3-9cbc-1d4925d1db88" />




> **"제휴의 시작부터 끝까지, A:SSU에서 한 번에!"**  
> 🏆 **제5회 숭실대학교 IT 프로젝트 공모전 수상작**

[📱 Google Play Store](#) | [💻 GitHub Repository](#) | [📄 API 명세서 (Swagger)](#)

---

## 📋 목차
1. [프로젝트 개요](#-프로젝트-개요)
2. [팀원 소개](#-팀원-소개)
3. [주요 기능](#-주요-기능)
4. [기술 스택](#-기술-스택)
5. [시스템 아키텍처](#-시스템-아키텍처)
6. [배포](#-배포)
7. [프로젝트 구조](#-프로젝트-구조)
8. [문의](#-문의)

---

## 🎯 프로젝트 개요

대학교 주변 제휴 혜택은 정보가 분산되어 있어 학생은 이용이 번거롭고, 학생회는 매 학기 제휴 관리 부담이 컸으며, 제휴업체는 인증 과정의 비효율과 홍보 효과 측정의 한계를 겪고 있었습니다.

**A:SSU**는 제휴 계약부터 이용까지의 흐름을 하나의 시스템으로 통합해, 위치 기반 검색과 모바일 인증으로 이용 절차를 단순화하고 제휴 이용 현황을 데이터로 관리할 수 있도록 설계된 플랫폼입니다.

### ✨ 주요 특징
* 🔐 **다중 인증 시스템**: 일반 회원가입, SSU 포털 연동, 토큰 기반 인증
* 💬 **실시간 채팅**: WebSocket 기반 실시간 1:1 메시징
* 📍 **위치 기반 서비스**: 공간 데이터(Spatial)를 활용한 지도 서비스 및 주변 매장 탐색
* 🔔 **푸시 알림**: Firebase FCM 및 Outbox 패턴을 통한 신뢰성 있는 실시간 알림
* 📊 **관리자 백오피스**: 종합적인 회원, 매장, 제휴 및 통계 관리 기능

---

## 👥 팀원 소개 (Team Members)

| 이름 | 학과 / 학번 | GitHub / Contact |
| :---: | :---: | :---: |
| 이수민 | 컴퓨터학부 23학번 | [@github](https://github.com/leesumin0526) |
| 이호근 | 컴퓨터학부 21학번 | [@github](https://github/2ghrms.com/2ghrms) |
| 백종원 | 컴퓨터학부 23학번 | [@github](https://github.com/BAEK0111) |
| 김예원 | 컴퓨터학부 24학번 | [@github](https://github.com/kimyw1018) |
| 이서희 | 컴퓨터학부 24학번 | [@github](https://github.com/eeeeeaaan) |


---

## ✨ 주요 기능

| 기능 | 설명 |
| :--- | :--- |
| **👤 회원 관리** | 학생/파트너 회원가입 및 프로필 관리 |
| **🔐 인증/인가** | JWT 기반 인증, 학생·파트너·관리자 역할 권한 관리 |
| **🏦 매장 관리** | 파트너 매장 등록·수정, 위치 기반 매장 검색 |
| **🤝 제휴 관리** | 제휴 제안·수락 및 제휴 현황 관리 |
| **💬 실시간 채팅** | WebSocket / STOMP 기반 1:1 실시간 채팅 |
| **📍 위치 기반 서비스** | Hibernate Spatial 기반 주변 매장 탐색 및 지도 |
| **🔔 푸시 알림** | FCM 기반 실시간 알림 (Transactional Outbox 패턴 적용) |
| **📱 디바이스 관리** | FCM 토큰 관리 및 멀티 디바이스 지원 |
| **⭐ 리뷰 및 신고** | 매장 리뷰 작성/관리 및 부적절한 콘텐츠 신고 처리 |
| **📊 관리자 기능** | 사용자·매장·제휴 통합 관리 및 데이터 통계 제공 |
| **📖 API 문서화** | SpringDoc OpenAPI (Swagger UI) 지원 |

---

## 🛠 기술 스택

| 항목 | 사용 기술 / 라이브러리 |
| :--- | :--- |
| **Backend** | Spring Boot 3.5.3, Java 17, Kotlin |
| **Database & ORM** | MariaDB, Spring Data JPA (Hibernate), Hibernate Spatial |
| **Security & Cache** | Spring Security, JWT, Redis |
| **Messaging** | RabbitMQ, Firebase Cloud Messaging (FCM) |
| **Storage & Real-time** | AWS S3, WebSocket, STOMP |
| **API Docs & Build** | SpringDoc OpenAPI (Swagger), Gradle |


## 🏗 시스템 아키텍처

```text
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   Mobile App    │    │   Web Client    │    │  Admin Panel    │
└─────────────────┘    └─────────────────┘    └─────────────────┘
         │                       │                       │
         └───────────────────────┼───────────────────────┘
                                 │
                    ┌─────────────────┐
                    │  Load Balancer  │
                    └─────────────────┘
                                 │
         ┌───────────────────────┼───────────────────────┐
         │                       │                       │
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   App Server    │    │   App Server    │    │   App Server    │
│   (Blue/Green)  │    │   (Blue/Green)  │    │   (Blue/Green)  │
└─────────────────┘    └─────────────────┘    └─────────────────┘
         │                       │                       │
         └───────────────────────┼───────────────────────┘
                                 │
    ┌─────────────────────────────────────────────────────────┐
    │                    Data Layer                           │
    │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐      │
    │  │   MariaDB   │  │    Redis    │  │  RabbitMQ   │      │
    │  └─────────────┘  └─────────────┘  └─────────────┘      │
    └─────────────────────────────────────────────────────────┘
                                 │
    ┌─────────────────────────────────────────────────────────┐
    │                External Services                        │
    │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐      │
    │  │   AWS S3    │  │ Firebase FCM│  │  Kakao API  │      │
    │  └─────────────┘  └─────────────┘  └─────────────┘      │
    └─────────────────────────────────────────────────────────┘
```
## 📁 프로젝트 구조
```text
src/main/java/com/assu/server/
├── domain/                     # 도메인별 비즈니스 로직
│   ├── admin/                  # 관리자 관리
│   ├── auth/                   # 인증 및 권한
│   ├── certification/          # 인증서 관리
│   ├── chat/                   # 실시간 채팅
│   ├── common/                 # 공통 엔티티
│   ├── deviceToken/            # 디바이스 토큰 관리
│   ├── inquiry/                # 문의 관리
│   ├── map/                    # 지도 서비스
│   ├── mapping/                # 학생-관리자 매핑
│   ├── member/                 # 회원 관리
│   ├── notification/           # 알림 시스템
│   ├── partner/                # 파트너 관리
│   ├── partnership/            # 파트너십 관리
│   ├── report/                 # 신고 처리
│   ├── review/                 # 리뷰 시스템
│   ├── store/                  # 매장 관리
│   ├── suggestion/             # 건의사항
│   ├── term/                   # 약관 관리
│   └── user/                   # 사용자 관리
├── global/                     # 전역 설정 및 유틸리티
│   ├── apiPayload/             # API 응답 형식
│   ├── config/                 # 설정 클래스
│   ├── exception/              # 예외 처리
│   └── util/                   # 유틸리티 클래스
├── infra/                      # 외부 서비스 연동
│   ├── aligo/                  # SMS 서비스
│   ├── firebase/               # FCM 푸시 알림
│   └── s3/                     # AWS S3 연동
└── ServerApplication.java      # 메인 애플리케이션
```
## 도메인별 구조
각 도메인은 다음과 같은 계층 구조를 따릅니다:
```text
domain/example/
├── controller/                 # REST API 컨트롤러
├── service/                    # 비즈니스 로직
├── repository/                 # 데이터 액세스
├── entity/                     # JPA 엔티티
├── dto/                        # 데이터 전송 객체
├── converter/                  # DTO-Entity 변환
└── exception/                  # 도메인별 예외
```
## 🚢 배포
### CI/CD 파이프라인
프로젝트는 GitHub Actions를 사용한 자동화된 CI/CD 파이프라인을 구축하고 있습니다.

### CI (Continuous Integration)
트리거: develop 브랜치에 Push 또는 PR
과정: 코드 빌드 → 테스트 실행 → 정적 분석
### CD (Continuous Deployment)
트리거: main 브랜치에 Push
과정: Docker 이미지 빌드 → DockerHub 푸시 → EC2 배포
### Blue-Green 배포
무중단 배포를 위해 Blue-Green 배포 전략을 사용합니다.



## 📞 문의
프로젝트에 대한 문의사항이 있으시면 다음을 통해 연락해 주세요:

팀 이메일: assu.soongsil.cse@gmail.com
A:SSU © 2026

