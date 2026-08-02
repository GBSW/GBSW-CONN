package kr.hs.gbsw.communication.proposal.exception;

import kr.hs.gbsw.communication.common.exception.ApiException;
import org.springframework.http.HttpStatus;

public class StudentRoleRequiredException extends ApiException {

    public StudentRoleRequiredException() {
        super(HttpStatus.FORBIDDEN, "STUDENT_ROLE_REQUIRED", "활성 학생 계정만 이 작업을 수행할 수 있습니다.");
    }
}
