package kr.hs.gbsw.communication.proposal.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.proposal")
public record ProposalProperties(
        @Min(1) @Max(10000) int supportThreshold
) {
}
