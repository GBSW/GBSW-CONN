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
