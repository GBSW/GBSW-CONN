package kr.hs.gbsw.communication.proposal.domain;

public enum ProposalFeedScope {
    /** 전체 제안. */
    ALL,
    /** 정식 안건. 동의 모집을 마친 제안. */
    FORMAL_AGENDA,
    /** 채택 안 됨. 담당 교사가 반려한 제안. */
    REJECTED
}
