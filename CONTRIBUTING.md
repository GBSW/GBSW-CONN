# 기여 안내

## 시작 전에 읽어야 할 것

- [`CODE_OF_CONDUCT.md`](CODE_OF_CONDUCT.md) — 공동체 행동 기준
- [`SECURITY.md`](SECURITY.md) — 보안 취약점은 공개 Issue가 아닌 비공개 채널로 신고
- [`GOVERNANCE.md`](GOVERNANCE.md) — 의사결정 구조와 브랜치 전략

이 시스템은 실제 학생 데이터를 다룹니다.  
**개인정보·세션·비밀값·익명 신원 연결 정보를 코드나 커밋에 포함하지 마십시오.**

## 이슈 먼저

버그 수정이든 기능 제안이든 코드 작성 전에 Issue를 먼저 여십시오.  
다음 항목을 확인합니다.

- 이미 진행 중인 작업인지
- 제안이 제품 원칙과 충돌하는지
- 어떤 도메인(auth, proposal, moderation …)에 영향을 주는지

## 브랜치와 PR 규칙

### MVP 단계 브랜치 이름

```
feat/<issue-number>-<short-description>
fix/<issue-number>-<short-description>
refactor/<issue-number>-<short-description>
test/<issue-number>-<short-description>
docs/<issue-number>-<short-description>
chore/<issue-number>-<short-description>
```

### PR 규칙

- `main`에 직접 push하지 않습니다.
- Pull Request는 관련 Issue를 반드시 연결합니다.
- 기본 병합 방식은 **Squash Merge**입니다.
- PR 템플릿의 모든 체크리스트를 확인한 뒤 제출합니다.

### CODEOWNERS 검토

다음 경로의 변경은 지정된 담당자의 승인이 필요합니다.

- `/.github/` — GitHub 저장소 설정
- (백엔드 코드 구조가 확정되면 추가 예정: `auth/`, `identity/`, `moderation/`, `db/migration/`)

## 코드 품질 기준

- Entity를 API 응답으로 직접 반환하지 않습니다. Request/Response DTO를 분리합니다.
- 권한 검사는 Controller(URL 수준)와 Service(메서드 수준) 양쪽에서 수행합니다.
- 모든 쓰기 업무는 `@Transactional` 경계 안에서 처리합니다.
- 비밀값, 테스트용 계정, 하드코딩된 초기 비밀번호를 남기지 않습니다.

## 테스트

PR에는 관련 테스트가 포함되어야 합니다.  
통합 테스트는 인메모리 DB가 아닌 실제 MySQL 호환 환경을 사용합니다.

## 커밋 메시지

```
<type>(<scope>): <짧은 설명>

[선택] 더 자세한 설명

[선택] Closes #<issue-number>
```

type: `feat` `fix` `refactor` `test` `docs` `chore`

## 문서 동기화

기능을 추가하거나 변경할 때 다음을 함께 갱신합니다.

- `README.md` — 관련 항목
- `docs/` 아래 해당 문서 (ERD, 권한표, 상태 전이 등)
- Swagger/OpenAPI 문서

문서 작업을 마지막에 몰아서 하지 않습니다.
