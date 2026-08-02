# 학교 소통 제안 시스템

> **서비스 정식 명칭 미확정.** 이 문서에서는 "학교 소통 제안 시스템"이라는 설명형 이름을 사용합니다.

경북소프트웨어마이스터고등학교 학생과 교사가 건의, 제안, 정보 요청, 학교 개선 의견을 주고받는 학생자치 기반 소통 시스템입니다.

단순 익명 게시판이 아닙니다. 학생의 의견이 충분한 공동체 지지(50명 동의)를 얻으면 학교가 검토해야 하는 정식 안건이 되고, 교사는 처리 상태와 공식 답변을 투명하게 남깁니다. 익명 표현을 허용하되, 운영자의 임의적인 신원 열람과 삭제를 기술적으로 제한합니다.

---

## 목차

1. [제품 원칙과 익명성](#제품-원칙과-익명성)
2. [주요 사용자와 권한 요약](#주요-사용자와-권한-요약)
3. [핵심 흐름](#핵심-흐름)
4. [디렉터리 구조](#디렉터리-구조)
5. [기술 스택](#기술-스택)
6. [사전 설치 요구사항](#사전-설치-요구사항)
7. [환경변수](#환경변수)
8. [로컬 개발 시작 방법](#로컬-개발-시작-방법)
9. [브랜치 전략](#브랜치-전략)
10. [보안과 비밀정보 관리](#보안과-비밀정보-관리)
11. [라이선스](#라이선스)

---

## 제품 원칙과 익명성

- 욕설이나 비판적 표현을 작성 단계에서 자동 차단하지 않습니다.
- 삭제와 신원 확인은 서로 다른 행위이며 별도의 3인 심의가 필요합니다.
- 슈퍼 어드민도 단순 권한만으로 익명 작성자의 신원을 임의 조회할 수 없습니다.
- 익명 작성자의 신원 연결 정보는 암호화된 별도 영역에 보관됩니다.

---

## 주요 사용자와 권한 요약

| 역할 | 설명 |
|------|------|
| `STUDENT` | 제안 작성, 동의, 본인 작성물 조회 |
| `TEACHER` | 정식 안건(50명 이상) 검토 및 공식 답변 |
| `SUPER_ADMIN` | 계정 생성·정지, 역할·보직 관리 |
| `STUDENT_COUNCIL_PRESIDENT` | 일반 학생 기능 + 심의위원 역할 |
| `STUDENT_COUNCIL_VICE_PRESIDENT` | 일반 학생 기능 + 심의위원 역할 |
| `STUDENT_AFFAIRS_TEACHER` | 교사 기능 + 심의위원 역할 + 신원 열람 |

보직은 임기 기반이며 이력으로 관리됩니다. 학생회 임원은 별도 계정을 만들지 않고 일반 학생 계정에 보직이 추가됩니다.

---

## 핵심 흐름

### 공개 제안 → 정식 안건

```
제안 등록 → 학생 공개 → 동의 모집 → 50명 도달 → 정식 안건 승격
→ 교사 검토 → 공식 답변 및 결정 → 실행 상태 추적
```

50명 미만 제안은 일반 교사에게 본문이 보이지 않습니다.

### 신원 확인 심의

신원 확인은 학생부장 교사 1명, 전교 학생회장 1명, 전교 학생부회장 1명, **세 명 모두** 승인해야 통과합니다. 학생부장 교사만 최근 재인증 후 실제 신원을 열람할 수 있습니다.

---

## 디렉터리 구조

```
project-root/
├── .github/
│   ├── CODEOWNERS               # 검토 책임자 지정
│   ├── ISSUE_TEMPLATE/
│   │   ├── bug_report.md
│   │   └── feature_request.md
│   ├── pull_request_template.md
│   └── workflows/               # CI 워크플로 (다음 단계에서 추가)
├── backend/                     # Spring Boot 백엔드 (초기화 예정)
├── frontend/                    # Next.js 프런트엔드 (초기화 예정)
├── docs/                        # 아키텍처, ERD, 권한표 등 문서
├── docker-compose.yml           # 로컬 MySQL (백엔드 초기화 후 완성 예정)
├── .env.example                 # 환경변수 키 목록과 설명
├── README.md
├── SECURITY.md
├── CONTRIBUTING.md
├── CODE_OF_CONDUCT.md
├── GOVERNANCE.md
├── LICENSE                      # Apache-2.0
└── NOTICE
```

---

## 기술 스택

> **아직 초기화 전입니다.** 아래 버전은 계획 예정 버전입니다. 실제 초기화 시 공식 안정 버전을 확인하고 이 표를 갱신합니다.

| 영역 | 기술 | 버전 (예정) |
|------|------|------------|
| 백엔드 | Spring Boot | 확인 예정 |
| 백엔드 언어 | Java (LTS) | 확인 예정 |
| 프런트엔드 | Next.js | 확인 예정 |
| 프런트엔드 언어 | TypeScript strict | 확인 예정 |
| 런타임 | Node.js (LTS) | 확인 예정 |
| DB | MySQL 8.x (InnoDB) | 확인 예정 |
| 마이그레이션 | Flyway | 확인 예정 |
| API 문서 | springdoc-openapi (OpenAPI 3) | 확인 예정 |
| 세션 | 서버 세션 → MySQL 영속화 | — |
| 캐시 | 없음 (Redis 미사용) | — |

버전과 호환성 근거는 백엔드·프런트엔드 초기화 단계에서 이 표에 기록됩니다.

---

## 사전 설치 요구사항

> 아래 항목은 백엔드·프런트엔드 초기화 완료 후 정확한 버전과 함께 갱신됩니다.

- Java (LTS) — 버전 확인 예정
- Gradle — 프로젝트 Wrapper 포함 예정
- Node.js (LTS) — 버전 확인 예정
- Docker Desktop (로컬 MySQL 실행용)
- Git

---

## 환경변수

`.env.example`을 복사해 `.env`를 만들고 값을 채우십시오.

```bash
cp .env.example .env
```

`.env`는 절대 저장소에 커밋하지 않습니다. `.gitignore`에 포함되어 있습니다.

주요 환경변수:

| 키 | 설명 |
|----|------|
| `DB_HOST` | MySQL 호스트 |
| `DB_PORT` | MySQL 포트 (기본 3306) |
| `DB_NAME` | DB 이름 |
| `DB_USERNAME` | DB 사용자 |
| `DB_PASSWORD` | DB 비밀번호 |
| `DB_ROOT_PASSWORD` | MySQL root 비밀번호 (Docker용) |
| `SESSION_SECRET` | 서버 세션 비밀값 |
| `IDENTITY_ENCRYPTION_KEY` | 익명 신원 암호화 키 (Base64, 256bit) |
| `HMAC_KEY` | HMAC 서명 키 |
| `NEIS_API_KEY` | 학교알리미 API 키 (빈 값이면 외부 연동 비활성화) |
| `NEXT_PUBLIC_API_BASE_URL` | 프런트엔드 → 백엔드 API URL |

전체 목록과 설명은 `.env.example`을 참고하십시오.

---

## 로컬 개발 시작 방법

> **아직 초기화 전입니다.** 백엔드와 프런트엔드 프로젝트가 초기화된 뒤 이 항목을 채웁니다.

### 1. 환경변수 설정

```bash
cp .env.example .env
# .env 를 열어 값을 채우십시오.
```

### 2. MySQL 실행 (Docker Compose)

```bash
# TODO: docker-compose.yml 에 MySQL 서비스가 추가된 뒤 아래 명령을 사용합니다.
# docker compose up -d
```

### 3. 백엔드 실행

```bash
# TODO: 백엔드 초기화 후 추가됩니다.
# cd backend && ./gradlew bootRun
```

### 4. 프런트엔드 실행

```bash
# TODO: 프런트엔드 초기화 후 추가됩니다.
# cd frontend && npm ci && npm run dev
```

---

## 브랜치 전략

현재 **1단계 MVP Feature Branch** 방식을 사용합니다.

- 장기 브랜치는 `main` 하나만 유지합니다.
- 모든 변경은 작업 브랜치에서 Pull Request를 통해 `main`에 병합합니다.
- `main` 직접 push와 force push를 금지합니다.
- 기본 병합 방식은 Squash Merge입니다.

전환 조건과 2단계 경량 GitFlow 규칙은 [`GOVERNANCE.md`](GOVERNANCE.md)를 참조하십시오.

---

## 보안과 비밀정보 관리

보안 취약점은 공개 Issue로 신고하지 않습니다. [`SECURITY.md`](SECURITY.md)의 비공개 신고 절차를 따르십시오.

비밀값(비밀번호, 키, 세션 비밀값 등)은 절대 소스코드나 Git에 포함하지 않습니다.

---

## 라이선스

Apache License 2.0 — 자세한 내용은 [`LICENSE`](LICENSE)를 참조하십시오.

저작권 주체는 아직 확정되지 않았습니다. [`NOTICE`](NOTICE)를 참조하십시오.
