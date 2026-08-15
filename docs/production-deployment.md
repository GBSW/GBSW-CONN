# 운영 배포 참조 구조

## 상태

이 문서는 운영 승인이 아니라 검토 가능한 참조 구조다. 실제 운영자, registry, image digest, TLS, 도메인, 비밀 저장소, 데이터베이스 권한 분리, 백업과 복원 증적이 없으면 배포하지 않는다. 루트 `docker-compose.yml`은 로컬 개발 전용이며 운영 입력으로 사용하지 않는다.

## 산출물과 경계

- `backend/Dockerfile`과 `frontend/Dockerfile`은 다단계 빌드와 비루트 런타임을 사용한다.
- `deploy/docker-compose.prod.yml`은 DB와 backend를 외부에 publish하지 않고 내부 네트워크에 둔다.
- gateway만 loopback 포트를 publish한다. 승인된 TLS ingress가 같은 호스트 또는 보호된 네트워크에서 이 포트로 전달해야 한다.
- frontend는 `BACKEND_INTERNAL_URL=http://backend:8080`으로만 backend에 접근한다.
- 비밀번호, HMAC 비밀, Identity Vault 키, 소유권 키와 자격증명 전달 토큰은 Compose secret 파일에서 읽으며 이미지, Compose 환경 예시 또는 Git에 값을 넣지 않는다.
- 모든 서비스 이미지는 운영 전 승인된 registry의 `@sha256:` digest로 교체한다. 예시의 `REPLACE_WITH_VERIFIED_DIGEST`는 의도적인 실패 값이다.

## 필수 외부 결정

| 결정 | 실제 값 | 소유자 | 증적 |
| --- | --- | --- | --- |
| registry와 image signing | 미정 | 미정 | 미정 |
| TLS ingress, 도메인, 인증서 갱신 | 미정 | 미정 | 미정 |
| DB 운영 주체와 네트워크/TLS | 미정 | 미정 | 미정 |
| 앱 DB 사용자와 Flyway 사용자 분리 | 미정 | 미정 | 미정 |
| 비밀 저장소와 회전 절차 | 미정 | 미정 | 미정 |
| 로그 접근, 마스킹과 보존 | 미정 | 미정 | 미정 |
| 백업 RPO/RTO와 복원 책임 | 미정 | 미정 | 미정 |

## 배포 순서

1. 승인된 기본 이미지로 backend와 frontend 이미지를 빌드하고 SBOM, 취약점 결과와 최종 digest를 릴리스 기록에 남긴다.
2. Flyway 전용 사용자와 immutable migration artifact를 사용해 동일한 MySQL 버전의 격리된 복제 환경에서 마이그레이션을 먼저 검증한다.
3. 암호화 백업과 복원 가능성을 확인한 뒤 운영 migration job을 실행한다. 애플리케이션 사용자에게 스키마 변경 권한을 주지 않는다.
4. secret manager가 Compose secret 파일 또는 동등한 orchestrator mount를 원자적으로 제공하도록 구성한다.
5. backend, frontend, gateway 순서로 시작하고 health 상태를 확인한다.
6. 외부 TLS에서 HTTP 차단 또는 HTTPS 전환, Secure 쿠키, Swagger 비활성, 권한별 API 거부를 확인한다.
7. 실패 시 새 쓰기를 중지하고 승인된 롤포워드 또는 복원 절차만 사용한다.

## 초기 2인 승인 정족수 구성

1. 운영 시작 전에 수신자 결합 자격증명 전달 서비스의 HTTPS 엔드포인트와 bearer token을 구성한다. 원문 활성화 코드는 API 응답이나 운영 로그에 나타나지 않는다.
2. 오프라인 부트스트랩 명령으로 최초 슈퍼관리자 한 명을 생성하고 즉시 활성화한다.
3. 최초 관리자가 거버넌스 요청으로 두 번째 `SUPER_ADMIN` 계정을 생성한다. 비철회 슈퍼관리자가 정확히 한 명일 때만 이 요청의 자기 승인이 허용되고 별도 감사 이벤트가 기록된다.
4. 두 번째 관리자가 활성화 대기 상태인 동안 코드가 만료되면 동일 대상의 활성화 코드 재발급 요청만 최초 관리자가 자기 승인할 수 있다.
5. 두 번째 관리자를 활성화한다. 이후 모든 권한·계정·보직 변경은 서로 다른 요청자와 승인자가 필요하며 자기 승인은 거부된다.

## 실패 폐쇄 조건

- production Compose는 image, DB URL, DB 사용자, 키 버전과 secret 파일 경로가 없으면 해석 단계에서 실패한다.
- backend 컨테이너는 `prod` 프로필, DB URL/사용자, 두 키 버전, HTTPS 전달 엔드포인트, 신뢰 프록시 CIDR과 다섯 secret 파일이 없으면 시작하지 않는다.
- frontend 컨테이너는 내부 backend URL이 없으면 시작하지 않는다.
- gateway 기본 publish 주소는 loopback이다. 공개 주소로 바꾸기 전에 TLS와 방화벽 승인이 필요하다.
- 실제 digest, 복원 증적, 보존 승인 또는 CODEOWNERS가 없으면 정상 기동 여부와 관계없이 릴리스는 차단된다.
