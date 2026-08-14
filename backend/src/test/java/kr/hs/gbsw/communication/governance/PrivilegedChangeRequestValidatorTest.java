package kr.hs.gbsw.communication.governance;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import kr.hs.gbsw.communication.governance.domain.PrivilegedChangeType;
import kr.hs.gbsw.communication.governance.dto.request.PrivilegedChangeCreateRequest;
import kr.hs.gbsw.communication.governance.service.PrivilegedChangeRequestValidator;
import kr.hs.gbsw.communication.user.domain.AccountRole;
import kr.hs.gbsw.communication.user.exception.AccountStateConflictException;
import org.junit.jupiter.api.Test;

class PrivilegedChangeRequestValidatorTest {

    @Test
    void acceptsTypedRoleRequest() {
        assertDoesNotThrow(() -> PrivilegedChangeRequestValidator.validate(new PrivilegedChangeCreateRequest(
                PrivilegedChangeType.ASSIGN_ROLE, UUID.randomUUID(), null, null,
                AccountRole.TEACHER, null, null, null, false, "업무상 필요")));
    }

    @Test
    void rejectsMissingTypedFields() {
        assertThrows(AccountStateConflictException.class, () ->
                PrivilegedChangeRequestValidator.validate(new PrivilegedChangeCreateRequest(
                        PrivilegedChangeType.APPOINT_OFFICE, UUID.randomUUID(), null, null,
                        null, null, null, null, false, "보직 변경")));
    }
}
