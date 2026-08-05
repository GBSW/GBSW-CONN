package kr.hs.gbsw.communication.proposal.domain;

public enum ProposalSort {
    /** 최신순. 등록일 내림차순. */
    LATEST,
    /** 날짜순. 등록일 오름차순. */
    OLDEST,
    /** 동의수 내림차순. */
    MOST_SUPPORTED,
    /** 동의수 오름차순. */
    LEAST_SUPPORTED
}
