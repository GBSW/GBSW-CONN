# REST API 규약

## 경로와 계약

- 외부 API 기본 경로는 `/api/v1`이다.
- 목록은 `page`, `size`, 허용된 `sort`를 사용하며 `size` 상한을 둔다.
- 외부 공개 UUID만 경로에 사용하고 내부 사용자 UUID는 응답에 포함하지 않는다.
- 상태 전이는 범용 PATCH보다 의도가 드러나는 명령 리소스를 사용한다.
- OpenAPI JSON이 계약의 원본이며 프런트 타입은 `openapi-typescript`로 생성한다.

구현된 인증·제안·심의 명령:

```text
GET    /api/v1/auth/csrf
POST   /api/v1/auth/activate
POST   /api/v1/auth/login
POST   /api/v1/auth/reauthenticate
POST   /api/v1/auth/logout
GET    /api/v1/auth/me
POST   /api/v1/auth/password-reset/complete
GET    /api/v1/admin/users?query=&status=&page=&size=
POST   /api/v1/admin/users
POST   /api/v1/admin/users/{publicId}/activation-code
POST   /api/v1/admin/users/{publicId}/password-reset-code
POST   /api/v1/admin/users/{publicId}/suspensions
POST   /api/v1/admin/users/{publicId}/reactivations
POST   /api/v1/admin/users/{publicId}/roles
POST   /api/v1/admin/users/{publicId}/roles/{role}/end
POST   /api/v1/admin/offices/{office}/appointments
POST   /api/v1/admin/offices/{office}/users/{publicId}/end
GET    /api/v1/admin/users/{publicId}
GET    /api/v1/proposals?scope=&sort=&query=&page=&size=
POST   /api/v1/proposals
GET    /api/v1/proposals/{publicId}
PUT    /api/v1/proposals/{publicId}/support
DELETE /api/v1/proposals/{publicId}/support
GET    /api/v1/admin/proposals?query=&size=
GET    /api/v1/admin/proposals/eligible-teachers?query=&size=
POST   /api/v1/admin/proposals/{publicId}/assignments
POST   /api/v1/proposals/{publicId}/review-start
POST   /api/v1/proposals/{publicId}/review-resume
POST   /api/v1/proposals/{publicId}/decisions/accept
POST   /api/v1/proposals/{publicId}/decisions/hold
POST   /api/v1/proposals/{publicId}/decisions/reject
POST   /api/v1/proposals/{publicId}/execution-start
POST   /api/v1/proposals/{publicId}/execution-complete
POST   /api/v1/proposals/{publicId}/reports
GET    /api/v1/moderation/reports?size=
POST   /api/v1/moderation/reports/{reportPublicId}/cases
GET    /api/v1/moderation/cases?size=
GET    /api/v1/moderation/cases/{publicId}
POST   /api/v1/moderation/cases/{publicId}/votes/approve
POST   /api/v1/moderation/cases/{publicId}/votes/reject
POST   /api/v1/identity-reveal-cases/{publicId}/reveal
```

계정 목록은 로그인 ID·표시 이름 검색, 계정 상태 필터, 최대 50건 페이지네이션을 지원한다. 목록에는 조회 시점에 유효한 역할·보직만 표시하고, 상세 조회에는 종료된 임기를 포함한 전체 이력을 반환한다.

제안 피드는 최신순이 기본이며 허용된 `LATEST`, `MOST_SUPPORTED` 정렬만 받는다. 학생은 모든 공개 제안을 조회하고, 교사는 `GATHERING_SUPPORT` 제안의 목록·상세 존재 여부를 모두 받지 않는다. 동의 수는 현재 활성 학생 역할이 있는 DB 동의 행에서 계산하며 클라이언트 값을 받지 않는다.

동의 PUT과 DELETE는 이미 같은 상태여도 동일한 성공 표현을 반환한다. 단, 정식 안건 승격 뒤 DELETE는 `SUPPORT_WITHDRAWAL_CLOSED` 충돌을 반환한다. 신원 확인과 공개 제한은 서로 다른 사건 유형 및 서비스 명령을 사용하며 같은 삭제 API에 섞지 않는다.

정식 안건 담당 지정은 슈퍼 어드민 최근 재인증과 대상 계정의 현재 활성 `TEACHER` 역할을 요구한다. 기존 담당을 바꿀 때 이전 지정은 종료 이력으로 남긴다. 상태 명령은 현재 담당 교사만 사용할 수 있고 허용된 전 상태가 아니면 `PROPOSAL_STATE_CONFLICT`를 반환한다. 학생·일반 교사 제안 상세에는 `viewerCanManage` boolean만 제공하며 담당·응답 교사 식별자는 포함하지 않는다.

신고 POST는 같은 사용자와 제안 조합을 멱등 처리해 최초에는 201, 기존 신고에는 같은 공개 ID로 200을 반환한다. 신고 사건함과 사건 생성은 현임 학생부장교사에게만 허용한다. 사건 생성 시 세 현임 보직자를 스냅샷하며, 이후 목록·상세·의결은 이 스냅샷에 포함된 사용자만 접근한다. 반대 1표는 사건을 즉시 반려하고 승인 3표만 승인으로 결정한다. 공개 제한 승인만 제안 공개 상태를 변경하며, 신원 확인 승인은 별도 열람 명령의 선행조건일 뿐 일반 DTO에 신원을 추가하지 않는다. 열람은 사건에 고정된 세 심의자가 승인 시점부터 정해진 기간 안에서만 수행할 수 있고 매 열람이 기록된다. 신원 열람 응답은 `Cache-Control: no-store`를 사용한다.

## 공통 오류

```json
{
  "code": "VALIDATION_FAILED",
  "message": "요청 값을 확인해 주세요.",
  "timestamp": "2026-08-02T00:00:00Z",
  "traceId": "01K...",
  "fieldErrors": [
    { "field": "title", "reason": "제목을 입력해 주세요." }
  ]
}
```

- `code`는 안정적인 기계 판독 값이다.
- `message`는 계정 존재 여부, 권한 대상, SQL, 클래스명, 경로를 노출하지 않는다.
- `traceId`는 요청 상관관계용이며 세션 ID나 사용자 ID가 아니다.
- `fieldErrors`는 입력 검증 오류에만 제공한다.
- 인증되지 않음은 401, 인증되었으나 권한 없음은 필요 이상 객체 존재를 드러내지 않는 403/404 정책을 도메인별로 적용한다.

## 세션과 CSRF

브라우저는 불투명한 서버 세션 쿠키를 사용한다. 상태 변경 요청은 먼저 `/api/v1/auth/csrf`에서 받은 토큰을 응답의 `headerName` 헤더로 보내야 한다. 로그인 성공은 기존 세션을 폐기하고 새 세션을 만들며, 비밀번호 재설정은 해당 계정의 모든 세션을 폐기한다. 각 요청에서 계정 상태·자격 증명 버전·현재 역할/보직이 세션 값과 일치하는지 재검증한다. 계정·역할·보직·일회 코드 관리 명령은 최근 재인증도 요구한다. Swagger UI는 개발 환경에만 열고 동일한 세션/CSRF 규칙을 우회하지 않는다.
