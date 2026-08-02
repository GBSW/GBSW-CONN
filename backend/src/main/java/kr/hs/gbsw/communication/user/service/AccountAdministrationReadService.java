package kr.hs.gbsw.communication.user.service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import kr.hs.gbsw.communication.auth.domain.AccountRecord;
import kr.hs.gbsw.communication.auth.repository.AuthRepository;
import kr.hs.gbsw.communication.user.dto.response.AccountDetailResponse;
import kr.hs.gbsw.communication.user.dto.response.AccountPageResponse;
import kr.hs.gbsw.communication.user.domain.AccountStatus;
import kr.hs.gbsw.communication.user.exception.AccountNotFoundException;
import kr.hs.gbsw.communication.user.repository.UserAdministrationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountAdministrationReadService {

    private final AuthRepository authRepository;
    private final UserAdministrationRepository repository;
    private final Clock clock;

    public AccountAdministrationReadService(
            AuthRepository authRepository,
            UserAdministrationRepository repository,
            Clock clock
    ) {
        this.authRepository = authRepository;
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AccountPageResponse list(String query, AccountStatus status, int page, int size) {
        String normalizedQuery = query == null || query.isBlank() ? null : query.strip();
        long offset = (long) page * size;
        Instant now = clock.instant();
        return AccountPageResponse.from(
                repository.findAccounts(normalizedQuery, status, now, size, offset),
                page,
                size,
                repository.countAccounts(normalizedQuery, status));
    }

    @Transactional(readOnly = true)
    public AccountDetailResponse get(UUID publicId) {
        AccountRecord account = authRepository.findByPublicId(publicId)
                .orElseThrow(AccountNotFoundException::new);
        return AccountDetailResponse.from(
                account,
                repository.findRoleHistory(account.id()),
                repository.findOfficeHistory(account.id()));
    }
}
