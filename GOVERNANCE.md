# 거버넌스

## 프로젝트 소유권

<!-- TODO: GitHub Organization 소유자와 장기 유지관리자를 확정한 뒤 이 항목을 채우십시오. -->

이 프로젝트는 가능하면 개인 계정이 아닌 GitHub Organization이 소유합니다.  
Organization Owner는 최소 두 명을 유지해 한 명의 졸업·이탈·계정 분실이 프로젝트 소유권 상실로 이어지지 않게 합니다.

## 의사결정

- 일반적인 버그 수정과 문서 개선은 PR 검토와 병합으로 결정합니다.
- 아키텍처, 보안 정책, 데이터 모델, 개인정보 처리 방침의 변경은 이슈에서 충분한 논의 후 결정합니다.
- 인증·권한·익명 신원·감사 로그·마이그레이션 관련 변경은 CODEOWNERS 담당자의 검토가 필수입니다.

## 브랜치 전략

### 현재 단계: 1단계 — MVP Feature Branch

MVP가 완성되고 첫 안정 릴리스가 나오기 전까지 적용합니다.

- 장기 브랜치는 `main` 하나만 유지합니다.
- `main`은 항상 빌드와 테스트가 통과하는 상태를 유지합니다.
- 모든 작업은 짧은 작업 브랜치(`feat/`, `fix/`, `docs/` 등)에서 진행합니다.
- 모든 변경은 Pull Request를 통해 `main`에 병합합니다.
- 기본 병합 방식은 **Squash Merge**입니다.
- `main` 직접 push와 force push를 금지합니다.
- MVP 단계에서는 `develop`, `release/*`, `hotfix/*`를 만들지 않습니다.

브랜치 이름:
```
feat/<issue-number>-<short-description>
fix/<issue-number>-<short-description>
refactor/<issue-number>-<short-description>
test/<issue-number>-<short-description>
docs/<issue-number>-<short-description>
chore/<issue-number>-<short-description>
```

### 전환 조건: 2단계 — 경량 GitFlow

다음 조건이 **모두** 충족될 때 전환을 검토합니다. 단순히 저장소를 공개했다는 이유만으로 전환하지 않습니다.

- 실제 학교 운영에 사용되는 안정 릴리스가 존재한다.
- 최소 `v1.0.0`에 준하는 공개 릴리스 기준이 정해졌다.
- 외부 기여자 또는 여러 유지관리자가 동시에 작업하기 시작했다.
- 다음 버전 개발과 현재 운영 버전의 긴급 수정이 병행될 필요가 있다.

전환 시점과 결정 이유는 이 문서와 Architecture Decision Record에 기록합니다.

### 2단계 브랜치 책임 (전환 후 적용)

- `main`: 운영 가능한 공식 릴리스 이력만 보관. 모든 릴리스에 `vMAJOR.MINOR.PATCH` 태그.
- `develop`: 다음 릴리스를 위한 통합 브랜치.
- `feature/*`: `develop`에서 분기, `develop`으로 PR.
- `release/<version>`: `develop`에서 분기, 버전 고정·문서·마이그레이션 검증·결함 수정만 허용. 새 기능 추가 금지.
- `hotfix/<version>`: 운영 중인 `main`에서 분기, 긴급 수정 후 `main`과 `develop` 양쪽에 반영.

## 릴리스 관리

- Semantic Versioning (`MAJOR.MINOR.PATCH`) 형식의 태그를 사용합니다.
- 각 릴리스에 변경 내역, 마이그레이션 주의사항, 보안 수정, 호환성 정보를 제공합니다.
- Git 태그만 만들고 릴리스 문서를 생략하지 않습니다.
- 운영 DB 마이그레이션과 롤백 절차를 릴리스 전에 검증합니다.

## 보안 취약점 수정

공개 저장소에서 심각한 미공개 취약점을 `security/*` 공개 브랜치로 수정하지 않습니다.  
GitHub Repository Security Advisory의 임시 비공개 포크에서 협업하고, 패치와 권고문을 준비한 뒤 조정된 방식으로 공개합니다.  
자세한 내용은 [`SECURITY.md`](SECURITY.md)를 참조하십시오.

## 변경 이력

<!-- 이 문서의 주요 변경사항을 여기에 기록합니다. -->

| 날짜 | 내용 |
|------|------|
| (초안) | 문서 초안 작성 — MVP Feature Branch 방식 |
