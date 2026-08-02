# 기여하기

이 프로젝트는 학생의 표현 자유와 익명 신원을 다루므로 일반 기능보다 권한·개인정보 변경을 더 엄격하게 검토한다.

## 시작하기

1. `README.md`로 로컬 환경을 실행한다.
2. `docs/requirements-checklist.md`, 권한표, 상태 전이와 보안 모델을 읽는다.
3. 기능 변경은 Issue에서 범위와 사용자 영향을 먼저 합의한다.
4. MVP 브랜치 규칙에 맞는 짧은 브랜치를 만든다.
5. 구현, 자동화 테스트, OpenAPI와 문서를 함께 변경한다.

## 코드 기준

- 도메인 우선 패키지 구조를 유지한다.
- Entity를 외부에 노출하지 않고 Request/Response DTO를 분리한다.
- Controller가 클라이언트의 역할·작성자·동의 수를 신뢰하지 않게 한다.
- DB가 보장할 수 있는 불변조건은 고유/외래/검사 제약으로도 강제한다.
- 실제 개인정보, 운영 로그, 비밀 또는 운영 인프라 세부사항을 커밋하지 않는다.
- 새 의존성은 필요성, 라이선스, 유지보수 상태와 보안 영향을 PR에 적는다.

## Pull Request 확인

```bash
cd backend && ./gradlew test
cd frontend && npm run check && npm audit
```

PR에는 변경 이유, 관련 Issue, 테스트 결과, 보안/개인정보 영향, 마이그레이션과 문서 변경 여부를 기록한다. 인증, 권한, 암호화, 심의, Flyway와 GitHub 설정은 지정 CODEOWNER의 승인이 필요하다.

## 보안 취약점

공개 Issue나 PR로 신고하지 않는다. GitHub Private Vulnerability Reporting 또는 저장소에 지정될 비공개 채널을 사용한다. 실제 운영 서버 시험과 실제 신원/데이터 접근은 금지한다.
