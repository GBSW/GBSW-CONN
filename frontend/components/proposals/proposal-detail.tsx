"use client";

import type { components } from "@/lib/api-schema";
import { ApiRequestError, apiDelete, apiGet, apiPut, errorMessage } from "@/lib/api-client";
import Link from "next/link";
import { useEffect, useState } from "react";
import { ProposalWorkflowPanel } from "@/components/proposals/proposal-workflow-panel";
import { ProposalReportForm } from "@/components/proposals/proposal-report-form";

type CurrentUser = components["schemas"]["CurrentUserResponse"];
type ProposalDetailResponse = components["schemas"]["ProposalDetailResponse"];
type SupportResponse = components["schemas"]["SupportResponse"];

const dateTimeFormatter = new Intl.DateTimeFormat("ko-KR", {
  timeZone: "Asia/Seoul",
  dateStyle: "medium",
  timeStyle: "short",
});

function formatDateTime(value: string): string {
  return dateTimeFormatter.format(new Date(value));
}

const statusLabels: Record<string, string> = {
  GATHERING_SUPPORT: "동의 모집 중",
  FORMAL_AGENDA: "정식 안건",
  UNDER_REVIEW: "검토 중",
  ACCEPTED: "채택",
  ON_HOLD: "보류",
  REJECTED: "반려",
  IN_PROGRESS: "실행 중",
  COMPLETED: "완료",
};

export function ProposalDetail({ publicId }: { publicId: string }) {
  const [user, setUser] = useState<CurrentUser | null>(null);
  const [proposal, setProposal] = useState<ProposalDetailResponse | null>(null);
  const [state, setState] = useState<"loading" | "ready" | "signed-out" | "not-found" | "error">("loading");
  const [actionPending, setActionPending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    Promise.all([
      apiGet<CurrentUser>("/api/v1/auth/me"),
      apiGet<ProposalDetailResponse>(`/api/v1/proposals/${publicId}`),
    ])
      .then(([currentUser, response]) => {
        if (!active) return;
        setUser(currentUser);
        setProposal(response);
        setState("ready");
      })
      .catch((caught: unknown) => {
        if (!active) return;
        if (caught instanceof ApiRequestError && caught.code === "PROPOSAL_NOT_FOUND") {
          setState("not-found");
        } else if (caught instanceof ApiRequestError
          && ["AUTHENTICATION_REQUIRED", "SESSION_INVALIDATED"].includes(caught.code)) {
          setState("signed-out");
        } else {
          setState("error");
        }
      });
    return () => {
      active = false;
    };
  }, [publicId]);

  async function changeSupport() {
    if (!proposal) return;
    setActionPending(true);
    setError(null);
    setNotice(null);
    try {
      const result = proposal.viewerSupported
        ? await apiDelete<SupportResponse>(`/api/v1/proposals/${publicId}/support`)
        : await apiPut<SupportResponse>(`/api/v1/proposals/${publicId}/support`);
      setProposal((current) => current ? {
        ...current,
        viewerSupported: result.supported,
        supportCount: result.supportCount,
        workflowStatus: result.workflowStatus,
        formalizedSupportCount: result.justFormalized ? result.supportCount : current.formalizedSupportCount,
        formalizedAt: result.justFormalized ? new Date().toISOString() : current.formalizedAt,
      } : current);
      setNotice(result.justFormalized
        ? "50명의 동의에 도달해 정식 안건으로 승격되었습니다."
        : result.supported ? "이 제안에 동의했습니다." : "동의를 철회했습니다.");
    } catch (caught) {
      setError(errorMessage(caught));
    } finally {
      setActionPending(false);
    }
  }

  if (state === "loading") return <p className="proposal-state" role="status">제안을 불러오고 있습니다…</p>;
  if (state === "signed-out") return <section className="proposal-state"><h1>로그인이 필요합니다</h1><Link className="primary-link" href="/login">로그인</Link></section>;
  if (state === "not-found") return <section className="proposal-state"><h1>제안을 찾을 수 없습니다</h1><p>삭제되었거나 현재 계정에 공개되지 않은 제안입니다.</p><Link href="/proposals">제안 목록으로</Link></section>;
  if (state === "error" || !proposal) return <p className="form-error" role="alert">제안을 불러오지 못했습니다.</p>;

  const student = user?.roles.includes("STUDENT") ?? false;
  const teacher = user?.roles.includes("TEACHER") ?? false;
  const gathering = proposal.workflowStatus === "GATHERING_SUPPORT";
  return (
    <article className="proposal-detail">
      <Link className="back-link" href="/proposals">← 제안 목록</Link>
      <div className="proposal-detail-meta">
        <span>{proposal.authorVisibility === "NAMED" ? proposal.authorDisplayName : "익명"}</span>
        <time dateTime={proposal.createdAt}>{formatDateTime(proposal.createdAt)}</time>
      </div>
      <p className="proposal-status-label">{statusLabels[proposal.workflowStatus] ?? proposal.workflowStatus}</p>
      <h1>{proposal.title}</h1>
      <div className="proposal-content">{proposal.content}</div>

      <section className="support-panel" aria-labelledby="support-title">
        <div>
          <h2 id="support-title">{gathering ? "정식 안건까지" : "정식 안건"}</h2>
          {gathering ? (
            <p><strong>{proposal.supportCount}</strong> / {proposal.supportThreshold}명 동의</p>
          ) : (
            <p>
              승격 당시 <strong>{proposal.formalizedSupportCount ?? proposal.supportThreshold}</strong>명
              {proposal.supportCount !== proposal.formalizedSupportCount
                ? ` · 현재 ${proposal.supportCount}명 동의`
                : ""}
            </p>
          )}
        </div>
        {student ? (
          proposal.viewerSupported && !gathering ? (
            <p className="support-locked">동의함 · 정식 안건 이후에는 철회할 수 없습니다.</p>
          ) : (
            <button className={proposal.viewerSupported ? "secondary-button" : "primary-button"} type="button" disabled={actionPending} onClick={() => void changeSupport()}>
              {actionPending ? "처리 중…" : proposal.viewerSupported ? "동의 철회" : "동의하기"}
            </button>
          )
        ) : null}
      </section>
      {error ? <p className="form-error" role="alert">{error}</p> : null}
      {notice ? <p className="form-notice" role="status">{notice}</p> : null}

      <ProposalReportForm publicId={publicId} />

      {proposal.officialResponses.length > 0 ? (
        <section className="proposal-responses" aria-labelledby="responses-title">
          <div className="proposal-section-heading">
            <p className="eyebrow">학교 공식 기록</p>
            <h2 id="responses-title">공식 답변과 실행 현황</h2>
          </div>
          <ol>
            {proposal.officialResponses.map((response, index) => (
              <li key={`${response.resultingStatus}-${response.createdAt}-${index}`}>
                <div>
                  <strong>{statusLabels[response.resultingStatus ?? ""] ?? response.resultingStatus}</strong>
                  {response.createdAt ? <time dateTime={response.createdAt}>{formatDateTime(response.createdAt)}</time> : null}
                </div>
                <p className="official-response-content">{response.content}</p>
                <dl>
                  <div><dt>결정·변경 사유</dt><dd>{response.decisionReason}</dd></div>
                  {response.followUpPlan ? <div><dt>후속 계획</dt><dd>{response.followUpPlan}</dd></div> : null}
                </dl>
              </li>
            ))}
          </ol>
        </section>
      ) : !gathering ? (
        <p className="proposal-empty-response">아직 등록된 학교 공식 답변이 없습니다.</p>
      ) : null}

      {proposal.viewerCanManage ? (
        <ProposalWorkflowPanel
          publicId={publicId}
          workflowStatus={proposal.workflowStatus}
          onUpdated={async () => {
            const updated = await apiGet<ProposalDetailResponse>(`/api/v1/proposals/${publicId}`);
            setProposal(updated);
          }}
        />
      ) : teacher && !gathering ? (
        <p className="proposal-assignment-note">내부 지정된 담당 교사만 이 안건의 상태와 공식 답변을 변경할 수 있습니다.</p>
      ) : null}

      <section className="proposal-history" aria-labelledby="history-title">
        <h2 id="history-title">진행 이력</h2>
        <ol>
          {proposal.statusHistory.map((history) => (
            <li key={`${history.toStatus}-${history.createdAt}`}>
              <div>
                <strong>{statusLabels[history.toStatus] ?? history.toStatus}</strong>
                <time dateTime={history.createdAt}>{formatDateTime(history.createdAt)}</time>
              </div>
              <p>{history.reason}</p>
              {history.supportCountSnapshot !== undefined ? <small>유효 동의 {history.supportCountSnapshot}명</small> : null}
            </li>
          ))}
        </ol>
      </section>
    </article>
  );
}
