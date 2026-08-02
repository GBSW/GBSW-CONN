"use client";

import type { components } from "@/lib/api-schema";
import { ApiRequestError, apiGet, apiPost, errorMessage } from "@/lib/api-client";
import Link from "next/link";
import { FormEvent, useEffect, useState } from "react";

type ModerationCase = components["schemas"]["ModerationCaseResponse"];
type VoteResult = components["schemas"]["ModerationVoteResultResponse"];
type IdentityReveal = components["schemas"]["IdentityRevealResponse"];

const caseTypeLabels: Record<string, string> = {
  CONTENT_VISIBILITY: "공개 제한 심의",
  IDENTITY_REVEAL: "신원 확인 심의",
};

const statusLabels: Record<string, string> = {
  PENDING: "심의 중",
  APPROVED: "승인",
  REJECTED: "반려",
};

const officeLabels: Record<string, string> = {
  STUDENT_AFFAIRS_TEACHER: "학생부장",
  STUDENT_COUNCIL_PRESIDENT: "학생회장",
  STUDENT_COUNCIL_VICE_PRESIDENT: "학생부회장",
};

const decisionLabels: Record<string, string> = {
  APPROVE: "승인",
  REJECT: "반대",
};

const dateTimeFormatter = new Intl.DateTimeFormat("ko-KR", {
  timeZone: "Asia/Seoul",
  dateStyle: "medium",
  timeStyle: "short",
});

function formatDateTime(value?: string): string {
  return value ? dateTimeFormatter.format(new Date(value)) : "시간 정보 없음";
}

export function ModerationCaseDetail({ publicId }: { publicId: string }) {
  const [moderationCase, setModerationCase] = useState<ModerationCase | null>(null);
  const [state, setState] = useState<"loading" | "ready" | "signed-out" | "not-found" | "error">("loading");
  const [error, setError] = useState<string | null>(null);

  async function load() {
    const response = await apiGet<ModerationCase>(`/api/v1/moderation/cases/${publicId}`);
    setModerationCase(response);
  }

  useEffect(() => {
    let active = true;
    apiGet<ModerationCase>(`/api/v1/moderation/cases/${publicId}`)
      .then((response) => {
        if (!active) return;
        setModerationCase(response);
        setState("ready");
      })
      .catch((caught: unknown) => {
        if (!active) return;
        if (caught instanceof ApiRequestError && caught.code === "MODERATION_NOT_FOUND") {
          setState("not-found");
        } else if (caught instanceof ApiRequestError
          && ["AUTHENTICATION_REQUIRED", "SESSION_INVALIDATED"].includes(caught.code)) {
          setState("signed-out");
        } else {
          setError(errorMessage(caught));
          setState("error");
        }
      });
    return () => { active = false; };
  }, [publicId]);

  if (state === "loading") return <p className="proposal-state" role="status">심의 사건을 불러오고 있습니다…</p>;
  if (state === "signed-out") return <section className="proposal-state"><h1>로그인이 필요합니다</h1><Link className="primary-link" href="/login">로그인</Link></section>;
  if (state === "not-found") return <section className="proposal-state"><h1>사건을 찾을 수 없습니다</h1><p>이 사건의 고정 심의자에게만 공개됩니다.</p><Link href="/moderation">사건함으로</Link></section>;
  if (state === "error" || !moderationCase) return <p className="form-error" role="alert">{error ?? "사건을 불러오지 못했습니다."}</p>;

  const pendingVote = moderationCase.caseStatus === "PENDING" && !moderationCase.viewerVoted;
  const canReveal = moderationCase.caseType === "IDENTITY_REVEAL"
    && moderationCase.caseStatus === "APPROVED"
    && moderationCase.viewerOffice === "STUDENT_AFFAIRS_TEACHER";

  return (
    <article className="moderation-detail">
      <Link className="back-link" href="/moderation">← 심의 사건함</Link>
      <div className="moderation-card-meta">
        <span>{caseTypeLabels[moderationCase.caseType ?? ""] ?? moderationCase.caseType}</span>
        <span className={`case-status case-status-${(moderationCase.caseStatus ?? "").toLowerCase()}`}>
          {statusLabels[moderationCase.caseStatus ?? ""] ?? moderationCase.caseStatus}
        </span>
      </div>
      <h1>{moderationCase.proposalTitle ?? "제목 없는 제안"}</h1>
      <p className="reviewer-office">{officeLabels[moderationCase.viewerOffice ?? ""] ?? moderationCase.viewerOffice} 자격으로 심의 중</p>

      <section className="moderation-evidence" aria-labelledby="evidence-title">
        <h2 id="evidence-title">심의 자료</h2>
        <div className="moderation-proposal-body">{moderationCase.proposalContent}</div>
        <dl>
          <div><dt>공개 작성자 표시</dt><dd>{moderationCase.authorVisibility === "NAMED" ? moderationCase.authorDisplayName : "익명"}</dd></div>
          <div><dt>신고 사유</dt><dd>{moderationCase.sourceReportReason}</dd></div>
          <div><dt>사건 생성 사유</dt><dd>{moderationCase.caseReason}</dd></div>
          <div><dt>사건 생성</dt><dd>{formatDateTime(moderationCase.createdAt)}</dd></div>
        </dl>
      </section>

      <section className="moderation-votes" aria-labelledby="votes-title">
        <div className="moderation-section-heading">
          <h2 id="votes-title">의결 현황</h2>
          <p>세 명이 모두 승인해야 승인되며, 한 명이라도 반대하면 반려됩니다.</p>
        </div>
        {(moderationCase.votes ?? []).length === 0 ? <p className="empty-state">아직 등록된 의결이 없습니다.</p> : (
          <ol>
            {(moderationCase.votes ?? []).map((vote, index) => (
              <li key={`${vote.office}-${vote.createdAt}-${index}`}>
                <div>
                  <strong>{officeLabels[vote.office ?? ""] ?? vote.office}</strong>
                  <span>{decisionLabels[vote.decision ?? ""] ?? vote.decision}</span>
                </div>
                <p>{vote.reason}</p>
                <time dateTime={vote.createdAt}>{formatDateTime(vote.createdAt)}</time>
              </li>
            ))}
          </ol>
        )}
      </section>

      {pendingVote ? <VoteForm publicId={publicId} onVoted={load} /> : moderationCase.viewerVoted ? (
        <p className="form-notice">이 사건에 대한 의결을 완료했습니다.</p>
      ) : null}

      {canReveal ? (
        moderationCase.identityRevealed
          ? <p className="identity-reveal-closed">신원 확인은 이미 한 번 완료되었습니다. 결과는 다시 표시되지 않습니다.</p>
          : <IdentityRevealForm publicId={publicId} onRevealed={() => setModerationCase((current) => current ? { ...current, identityRevealed: true } : current)} />
      ) : null}
    </article>
  );
}

function VoteForm({ publicId, onVoted }: { publicId: string; onVoted: () => Promise<void> }) {
  const [reason, setReason] = useState("");
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function vote(decision: "approve" | "reject") {
    setPending(true);
    setError(null);
    try {
      await apiPost<VoteResult>(`/api/v1/moderation/cases/${publicId}/votes/${decision}`, { reason });
      await onVoted();
    } catch (caught) {
      setError(errorMessage(caught));
    } finally {
      setPending(false);
    }
  }

  return (
    <section className="moderation-action" aria-labelledby="vote-form-title">
      <div>
        <h2 id="vote-form-title">내 의결</h2>
        <p>등록 후에는 바꿀 수 없습니다. 근거를 남기고 결정해 주세요.</p>
      </div>
      <div className="compact-form">
        <div className="field">
          <label htmlFor="vote-reason">의결 사유</label>
          <textarea id="vote-reason" value={reason} onChange={(event) => setReason(event.target.value)} maxLength={2000} required />
        </div>
        {error ? <p className="form-error" role="alert">{error}</p> : null}
        <div className="button-row">
          <button className="primary-button" type="button" disabled={pending || !reason.trim()} onClick={() => void vote("approve")}>승인</button>
          <button className="danger-button" type="button" disabled={pending || !reason.trim()} onClick={() => void vote("reject")}>반대</button>
        </div>
      </div>
    </section>
  );
}

function IdentityRevealForm({ publicId, onRevealed }: { publicId: string; onRevealed: () => void }) {
  const [password, setPassword] = useState("");
  const [reason, setReason] = useState("");
  const [identity, setIdentity] = useState<IdentityReveal | null>(null);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function reveal(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setPending(true);
    setError(null);
    try {
      await apiPost<unknown>("/api/v1/auth/reauthenticate", { password });
      const result = await apiPost<IdentityReveal>(
        `/api/v1/identity-reveal-cases/${publicId}/reveal`,
        { reason },
      );
      setPassword("");
      setReason("");
      setIdentity(result);
      onRevealed();
    } catch (caught) {
      setError(errorMessage(caught));
    } finally {
      setPending(false);
    }
  }

  if (identity) {
    return (
      <section className="identity-reveal-result" aria-labelledby="identity-result-title">
        <p className="eyebrow">일회성 표시</p>
        <h2 id="identity-result-title">확인된 작성자</h2>
        <dl>
          <div><dt>로그인 ID</dt><dd>{identity.loginId}</dd></div>
          <div><dt>이름</dt><dd>{identity.displayName}</dd></div>
          <div><dt>확인 시각</dt><dd>{formatDateTime(identity.revealedAt)}</dd></div>
        </dl>
        <p>이 정보는 현재 화면 상태에만 있으며, 닫은 뒤 다시 조회할 수 없습니다.</p>
        <button className="danger-button" type="button" onClick={() => setIdentity(null)}>확인 결과 닫기</button>
      </section>
    );
  }

  return (
    <section className="identity-reveal" aria-labelledby="identity-reveal-title">
      <div>
        <p className="eyebrow">학생부장 전용</p>
        <h2 id="identity-reveal-title">작성자 신원 일회 확인</h2>
        <p>승인된 사건에서만 가능합니다. 비밀번호로 다시 인증한 뒤 결과가 한 번 표시됩니다.</p>
      </div>
      <form className="compact-form" onSubmit={(event) => void reveal(event)}>
        <div className="field">
          <label htmlFor="reveal-password">현재 비밀번호</label>
          <input id="reveal-password" type="password" autoComplete="current-password" value={password} onChange={(event) => setPassword(event.target.value)} required />
        </div>
        <div className="field">
          <label htmlFor="reveal-reason">확인 사유</label>
          <textarea id="reveal-reason" value={reason} onChange={(event) => setReason(event.target.value)} maxLength={2000} required />
        </div>
        {error ? <p className="form-error" role="alert">{error}</p> : null}
        <button className="danger-button" type="submit" disabled={pending}>{pending ? "확인 중…" : "재인증하고 한 번 확인"}</button>
      </form>
    </section>
  );
}
