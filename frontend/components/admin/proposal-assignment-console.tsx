"use client";

import type { components } from "@/lib/api-schema";
import { ApiRequestError, apiGet, apiPost, errorMessage } from "@/lib/api-client";
import Link from "next/link";
import type { FormEvent } from "react";
import { useCallback, useEffect, useState } from "react";

type CurrentUser = components["schemas"]["CurrentUserResponse"];
type AdminProposal = components["schemas"]["AdminProposalSummaryResponse"];
type EligibleTeacher = components["schemas"]["EligibleProposalTeacherResponse"];
type Assignment = components["schemas"]["ProposalAssignmentResponse"];

const statusLabels: Record<string, string> = {
  FORMAL_AGENDA: "정식 안건",
  UNDER_REVIEW: "검토 중",
  ACCEPTED: "채택",
  ON_HOLD: "보류",
  REJECTED: "반려",
  IN_PROGRESS: "실행 중",
  COMPLETED: "완료",
};

function formText(form: FormData, name: string): string {
  return String(form.get(name) ?? "").trim();
}

export function ProposalAssignmentConsole() {
  const [access, setAccess] = useState<"loading" | "ready" | "signed-out" | "forbidden" | "error">("loading");
  const [proposals, setProposals] = useState<AdminProposal[]>([]);
  const [teachers, setTeachers] = useState<EligibleTeacher[]>([]);
  const [loading, setLoading] = useState(true);
  const [pendingId, setPendingId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const loadData = useCallback(async () => {
    setLoading(true);
    const [proposalResult, teacherResult] = await Promise.all([
      apiGet<AdminProposal[]>("/api/v1/admin/proposals?size=100"),
      apiGet<EligibleTeacher[]>("/api/v1/admin/proposals/eligible-teachers?size=100"),
    ]);
    setProposals(proposalResult);
    setTeachers(teacherResult.filter((teacher) => teacher.publicId && teacher.displayName));
    setLoading(false);
  }, []);

  useEffect(() => {
    let active = true;
    apiGet<CurrentUser>("/api/v1/auth/me")
      .then(async (user) => {
        if (!active) return;
        if (!user.roles.includes("SUPER_ADMIN")) {
          setAccess("forbidden");
          return;
        }
        setAccess("ready");
        await loadData();
      })
      .catch((caught: unknown) => {
        if (!active) return;
        const signedOut = caught instanceof ApiRequestError
          && ["AUTHENTICATION_REQUIRED", "SESSION_INVALIDATED"].includes(caught.code);
        setAccess(signedOut ? "signed-out" : "error");
        setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [loadData]);

  async function assign(event: FormEvent<HTMLFormElement>, publicId: string) {
    event.preventDefault();
    const formElement = event.currentTarget;
    const form = new FormData(formElement);
    setPendingId(publicId);
    setError(null);
    setNotice(null);
    try {
      await apiPost<Assignment>(`/api/v1/admin/proposals/${publicId}/assignments`, {
        teacherPublicId: formText(form, "teacherPublicId"),
        reason: formText(form, "reason"),
      });
      await loadData();
      formElement.reset();
      setNotice("담당 교사를 내부 지정했습니다. 이 정보는 학생 화면에 공개되지 않습니다.");
    } catch (caught) {
      if (caught instanceof ApiRequestError && caught.code === "REAUTHENTICATION_REQUIRED") {
        setError("최근 본인 확인이 필요합니다. 계정 관리 화면에서 비밀번호를 다시 확인한 뒤 재시도해 주세요.");
      } else {
        setError(errorMessage(caught));
      }
    } finally {
      setPendingId(null);
    }
  }

  if (access === "loading") return <p className="admin-state" role="status">관리 권한을 확인하고 있습니다…</p>;
  if (access === "signed-out") return <section className="admin-state"><h2>로그인이 필요합니다</h2><Link className="primary-link" href="/login">로그인</Link></section>;
  if (access === "forbidden") return <section className="admin-state"><h2>접근 권한이 없습니다</h2><p>슈퍼 어드민만 담당 교사를 내부 지정할 수 있습니다.</p></section>;
  if (access === "error") return <p className="form-error" role="alert">관리 정보를 불러오지 못했습니다.</p>;

  return (
    <div className="proposal-assignment-console">
      <section className="admin-session">
        <div>
          <h2>최소 공개 원칙</h2>
          <p>이 화면에는 제안 본문을 표시하지 않습니다. 담당 교사 정보도 학생 API와 화면에 전달되지 않습니다.</p>
        </div>
        <Link className="secondary-link" href="/admin">본인 확인·계정 관리</Link>
      </section>
      {error ? <p className="form-error" role="alert">{error}</p> : null}
      {notice ? <p className="form-notice" role="status">{notice}</p> : null}
      {loading ? <p className="admin-state" role="status">정식 안건을 불러오고 있습니다…</p> : null}
      {!loading && proposals.length === 0 ? <p className="admin-state">담당 지정이 필요한 정식 안건이 없습니다.</p> : null}
      {!loading && teachers.length === 0 ? <p className="form-error">현재 담당자로 지정할 수 있는 활성 교사가 없습니다.</p> : null}
      <div className="assignment-list">
        {proposals.map((proposal) => proposal.publicId ? (
          <article className="assignment-item" key={proposal.publicId}>
            <div className="assignment-summary">
              <span>{statusLabels[proposal.workflowStatus ?? ""] ?? proposal.workflowStatus}</span>
              <h2>{proposal.title}</h2>
              <p>
                현재 담당: {proposal.assignment?.teacherDisplayName ?? "미지정"}
              </p>
            </div>
            {!(["REJECTED", "COMPLETED"].includes(proposal.workflowStatus ?? "")) ? (
              <form onSubmit={(event) => void assign(event, proposal.publicId!)}>
                <div className="field">
                  <label htmlFor={`teacher-${proposal.publicId}`}>담당 교사</label>
                  <select id={`teacher-${proposal.publicId}`} name="teacherPublicId" required defaultValue="">
                    <option value="" disabled>교사를 선택하세요</option>
                    {teachers.map((teacher) => (
                      <option key={teacher.publicId} value={teacher.publicId}>{teacher.displayName}</option>
                    ))}
                  </select>
                </div>
                <div className="field">
                  <label htmlFor={`reason-${proposal.publicId}`}>지정·변경 사유</label>
                  <input id={`reason-${proposal.publicId}`} name="reason" maxLength={500} required />
                </div>
                <button className="primary-button" type="submit" disabled={pendingId === proposal.publicId || teachers.length === 0}>
                  {pendingId === proposal.publicId ? "지정 중…" : proposal.assignment ? "담당 변경" : "담당 지정"}
                </button>
              </form>
            ) : <p className="assignment-closed">종결된 안건은 담당자를 변경하지 않습니다.</p>}
          </article>
        ) : null)}
      </div>
    </div>
  );
}
