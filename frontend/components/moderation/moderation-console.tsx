"use client";

import type { components } from "@/lib/api-schema";
import { ApiRequestError, apiGet, apiPost, errorMessage } from "@/lib/api-client";
import Link from "next/link";
import { FormEvent, useEffect, useState } from "react";

type CurrentUser = components["schemas"]["CurrentUserResponse"];
type ReportItem = components["schemas"]["ReportInboxItemResponse"];
type ModerationCase = components["schemas"]["ModerationCaseResponse"];

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

const dateTimeFormatter = new Intl.DateTimeFormat("ko-KR", {
  timeZone: "Asia/Seoul",
  dateStyle: "medium",
  timeStyle: "short",
});

function formatDateTime(value?: string): string {
  return value ? dateTimeFormatter.format(new Date(value)) : "시간 정보 없음";
}

export function ModerationConsole() {
  const [user, setUser] = useState<CurrentUser | null>(null);
  const [reports, setReports] = useState<ReportItem[]>([]);
  const [cases, setCases] = useState<ModerationCase[]>([]);
  const [state, setState] = useState<"loading" | "ready" | "signed-out" | "error">("loading");
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    apiGet<CurrentUser>("/api/v1/auth/me")
      .then(async (currentUser) => {
        const reviewerCases = await apiGet<ModerationCase[]>("/api/v1/moderation/cases?size=100");
        const reportItems = currentUser.offices.includes("STUDENT_AFFAIRS_TEACHER")
          ? await apiGet<ReportItem[]>("/api/v1/moderation/reports?size=100")
          : [];
        if (!active) return;
        setUser(currentUser);
        setCases(reviewerCases);
        setReports(reportItems);
        setState("ready");
      })
      .catch((caught: unknown) => {
        if (!active) return;
        if (caught instanceof ApiRequestError
          && ["AUTHENTICATION_REQUIRED", "SESSION_INVALIDATED"].includes(caught.code)) {
          setState("signed-out");
        } else {
          setError(errorMessage(caught));
          setState("error");
        }
      });
    return () => { active = false; };
  }, []);

  function created(reportPublicId: string, createdCase: ModerationCase) {
    setCases((current) => [createdCase, ...current]);
    setReports((current) => current.map((report) => report.publicId === reportPublicId ? {
      ...report,
      existingCaseTypes: [...(report.existingCaseTypes ?? []), createdCase.caseType ?? ""],
    } : report));
  }

  if (state === "loading") return <p className="proposal-state" role="status">심의 사건함을 불러오고 있습니다…</p>;
  if (state === "signed-out") return <section className="proposal-state"><h1>로그인이 필요합니다</h1><Link className="primary-link" href="/login">로그인</Link></section>;
  if (state === "error") return <p className="form-error" role="alert">{error ?? "심의 사건함을 불러오지 못했습니다."}</p>;

  const studentAffairs = user?.offices.includes("STUDENT_AFFAIRS_TEACHER") ?? false;
  return (
    <div className="moderation-console">
      <section className="moderation-intro">
        <p className="eyebrow">보호 심의</p>
        <h1>신고와 결정을 분리합니다</h1>
        <p>사건 생성 시 학생부장·학생회장·학생부회장 세 명이 심의자로 고정됩니다. 공개 제한과 신원 확인은 각각 별도 사건으로 의결합니다.</p>
      </section>

      {studentAffairs ? (
        <section className="moderation-section" aria-labelledby="reports-title">
          <div className="moderation-section-heading">
            <h2 id="reports-title">접수된 신고</h2>
            <p>신고만으로 제안 상태는 바뀌지 않습니다. 필요한 심의만 분리해 시작하세요.</p>
          </div>
          <div className="moderation-list">
            {reports.length === 0 ? <p className="empty-state">접수된 신고가 없습니다.</p> : reports.map((report) => (
              <article className="moderation-report-card" key={report.publicId}>
                <div className="moderation-card-meta">
                  <time dateTime={report.createdAt}>{formatDateTime(report.createdAt)}</time>
                  <span>{(report.existingCaseTypes ?? []).length}개 사건 생성됨</span>
                </div>
                <h3>{report.proposalTitle ?? "제목 없는 제안"}</h3>
                <p className="moderation-proposal-content">{report.proposalContent}</p>
                <dl><dt>신고 사유</dt><dd>{report.reason}</dd></dl>
                {report.publicId ? (
                  <CreateCaseForm
                    key={`${report.publicId}-${(report.existingCaseTypes ?? []).join("-")}`}
                    report={report}
                    onCreated={(createdCase) => created(report.publicId ?? "", createdCase)}
                  />
                ) : null}
              </article>
            ))}
          </div>
        </section>
      ) : null}

      <section className="moderation-section" aria-labelledby="cases-title">
        <div className="moderation-section-heading">
          <h2 id="cases-title">내 심의 사건</h2>
          <p>본인에게 고정 배정된 사건만 표시됩니다.</p>
        </div>
        <div className="moderation-list">
          {cases.length === 0 ? <p className="empty-state">배정된 심의 사건이 없습니다.</p> : cases.map((moderationCase) => (
            <article className="moderation-case-card" key={moderationCase.publicId}>
              <div className="moderation-card-meta">
                <span>{caseTypeLabels[moderationCase.caseType ?? ""] ?? moderationCase.caseType}</span>
                <span className={`case-status case-status-${(moderationCase.caseStatus ?? "").toLowerCase()}`}>
                  {statusLabels[moderationCase.caseStatus ?? ""] ?? moderationCase.caseStatus}
                </span>
              </div>
              <h3>{moderationCase.proposalTitle ?? "제목 없는 제안"}</h3>
              <p>{officeLabels[moderationCase.viewerOffice ?? ""] ?? moderationCase.viewerOffice} 자격으로 심의 · {(moderationCase.votes ?? []).length}/3 의결</p>
              {moderationCase.publicId ? <Link className="secondary-link" href={`/moderation/${moderationCase.publicId}`}>사건 상세 보기</Link> : null}
            </article>
          ))}
        </div>
      </section>
    </div>
  );
}

function CreateCaseForm({ report, onCreated }: {
  report: ReportItem;
  onCreated: (createdCase: ModerationCase) => void;
}) {
  const existing = report.existingCaseTypes ?? [];
  const available = ["CONTENT_VISIBILITY", "IDENTITY_REVEAL"].filter((type) => !existing.includes(type));
  const [caseType, setCaseType] = useState(available[0] ?? "");
  const [reason, setReason] = useState("");
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!report.publicId || !caseType) return;
    setPending(true);
    setError(null);
    try {
      const created = await apiPost<ModerationCase>(
        `/api/v1/moderation/reports/${report.publicId}/cases`,
        { caseType, reason },
      );
      setReason("");
      onCreated(created);
    } catch (caught) {
      setError(errorMessage(caught));
    } finally {
      setPending(false);
    }
  }

  if (available.length === 0) {
    return <p className="case-complete-note">공개 제한과 신원 확인 사건이 모두 생성되었습니다.</p>;
  }
  return (
    <form className="case-create-form" onSubmit={(event) => void submit(event)}>
      <div className="field">
        <label htmlFor={`case-type-${report.publicId}`}>심의 유형</label>
        <select id={`case-type-${report.publicId}`} value={caseType} onChange={(event) => setCaseType(event.target.value)} required>
          {available.map((type) => <option key={type} value={type}>{caseTypeLabels[type]}</option>)}
        </select>
      </div>
      <div className="field">
        <label htmlFor={`case-reason-${report.publicId}`}>사건 생성 사유</label>
        <textarea id={`case-reason-${report.publicId}`} value={reason} onChange={(event) => setReason(event.target.value)} maxLength={2000} required />
      </div>
      {error ? <p className="form-error" role="alert">{error}</p> : null}
      <button className="primary-button" type="submit" disabled={pending}>{pending ? "생성 중…" : "심의 사건 생성"}</button>
    </form>
  );
}
