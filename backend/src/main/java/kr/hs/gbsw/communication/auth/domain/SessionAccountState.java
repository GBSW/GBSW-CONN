package kr.hs.gbsw.communication.auth.domain;

import java.util.List;

public record SessionAccountState(
        boolean active,
        long credentialVersion,
        List<String> authorities
) {
    public SessionAccountState {
        authorities = List.copyOf(authorities);
    }
}
