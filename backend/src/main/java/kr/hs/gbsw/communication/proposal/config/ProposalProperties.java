package kr.hs.gbsw.communication.proposal.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.proposal")
public record ProposalProperties(
        /** 정식 안건으로 승격되는 유효 동의 수. */
        @Min(1) @Max(10000) int supportThreshold,
        /**
         * 일반 사용자에게 제안을 임시로 가리는 유효 신고 수.
         * 임시 가림은 되돌릴 수 있으며 3인 심의의 확정 공개 제한과는 별개다.
         */
        @Min(1) @Max(1000) int reportHideThreshold
) {
}
