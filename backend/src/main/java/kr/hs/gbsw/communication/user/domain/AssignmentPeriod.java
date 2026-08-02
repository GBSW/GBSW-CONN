package kr.hs.gbsw.communication.user.domain;

import java.time.Instant;

public record AssignmentPeriod(String type, Instant startsAt, Instant endsAt) {
}
