# 브랜치와 릴리스

## MVP 단계

- 장기 브랜치는 `main` 하나다.
- `feat/<issue>-<description>`, `fix/...`, `docs/...`, `test/...`, `refactor/...`, `chore/...` 형식의 짧은 브랜치를 사용한다.
- 모든 변경은 PR, 필수 CI, 대화 해결, 책임자 승인을 거친다.
- 작성자는 자신의 변경을 단독 승인하지 않는다.
- squash merge를 기본으로 하며 force push와 main 직접 push를 금지한다.
- 미공개 취약점은 공개 `security/*` 브랜치가 아니라 GitHub Security Advisory 비공개 포크에서 다룬다.

## 경량 GitFlow 전환 조건

안정적인 v1.0 수준 릴리스, 외부 기여자/복수 유지관리자, 다음 버전과 운영 긴급 수정의 병행 필요가 모두 실질적으로 생긴 뒤 ADR과 `GOVERNANCE.md`에 전환 이유를 기록한다.

전환 뒤에는 `develop`을 통합 브랜치로 두고 `feature/* → develop`, `develop → release/<version>`, 검증된 release를 `main`과 `develop`에 반영한다. hotfix는 `main`에서 분기해 두 장기 브랜치에 모두 반영한다.

## 릴리스

- Semantic Versioning `vMAJOR.MINOR.PATCH` 태그를 사용한다.
- 변경 내역, 보안 수정, 마이그레이션/복구 주의사항, 호환성을 릴리스 노트에 기록한다.
- 태그 전 전체 빌드, 실제 MySQL 마이그레이션, 권한·동시성·E2E 검증을 실행한다.
- 태그만 만들고 릴리스 문서를 생략하지 않는다.

## 운영 릴리스 게이트

- `nanoid`를 포함한 npm 전체 의존성과 JVM 런타임 의존성에 승인되지 않은 High/Critical 취약점이 없어야 한다.
- GitHub Action은 검증된 40자리 커밋 SHA를 사용하고 Gradle wrapper에는 공식 배포본 SHA-256이 있어야 한다.
- lockfile, OpenAPI 생성 타입과 빌드 산출물 검사 뒤 작업 트리가 깨끗해야 한다.
- backend, frontend와 gateway 이미지는 승인된 registry의 immutable digest로 지정한다.
- `prod` 프로필, Secure 쿠키, 문서 비활성, 내부 DB/backend 네트워크와 비밀 주입을 확인한다.
- 마이그레이션 권한 분리, 롤포워드 또는 복구 절차, 백업 복원 증적이 있어야 한다.
- 승인된 보존표, 실제 CODEOWNERS, 브랜치 보호와 비공개 보안 신고 채널이 있어야 한다.
- 하나라도 충족하지 못하면 태그와 운영 배포를 만들지 않는다.
