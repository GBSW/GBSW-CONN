package kr.hs.gbsw.communication.proposal.domain;

public enum ProposalFeedScope {
    /** 전체 제안. */
    ALL,
    /** 정식 안건. 동의 모집을 마친 제안. */
    FORMAL_AGENDA,
    /** 채택 안 됨. 담당 교사가 반려한 제안. */
    REJECTED,
    /** 동의 모집 중. 아직 임계값에 이르지 못한 제안. 교사 대시보드의 검토 대기 목록이 쓴다. */
    GATHERING_SUPPORT
}
