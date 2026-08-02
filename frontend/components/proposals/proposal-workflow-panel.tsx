"use client";

import type { components } from "@/lib/api-schema";
import { apiPost, errorMessage } from "@/lib/api-client";
import type { FormEvent } from "react";
import { useState } from "react";

type OfficialResponseRequest = components["schemas"]["OfficialResponseRequest"];
type ProposalWorkflowResponse = components["schemas"]["ProposalWorkflowResponse"];

function formText(form: FormData, name: string): string {
  return String(form.get(name) ?? "").trim();
}

export function ProposalWorkflowPanel({
  publicId,
  workflowStatus,
  onUpdated,
}: {
  publicId: string;
  workflowStatus: string;
  onUpdated: () => Promise<void>;
}) {
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const reasonOnly = workflowStatus === "FORMAL_AGENDA" || workflowStatus === "ON_HOLD";
  const responseCommand = ["UNDER_REVIEW", "ACCEPTED", "IN_PROGRESS"].includes(workflowStatus);
  if (!reasonOnly && !responseCommand) return null;

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const formElement = event.currentTarget;
    const form = new FormData(formElement);
    setPending(true);
    setError(null);
    setNotice(null);
    try {
      let endpoint: string;
      let body: components["schemas"]["ProposalTransitionReasonRequest"] | OfficialResponseRequest;
      if (workflowStatus === "FORMAL_AGENDA") {
        endpoint = "review-start";
        body = { reason: formText(form, "reason") };
      } else if (workflowStatus === "ON_HOLD") {
        endpoint = "review-resume";
        body = { reason: formText(form, "reason") };
      } else {
        const command = workflowStatus === "UNDER_REVIEW"
          ? formText(form, "command")
          : workflowStatus === "ACCEPTED" ? "execution-start" : "execution-complete";
        endpoint = workflowStatus === "UNDER_REVIEW" ? `decisions/${command}` : command;
        const followUpPlan = formText(form, "followUpPlan");
        body = {
          content: formText(form, "content"),
          decisionReason: formText(form, "decisionReason"),
          ...(followUpPlan ? { followUpPlan } : {}),
        };
      }
      await apiPost<ProposalWorkflowResponse>(`/api/v1/proposals/${publicId}/${endpoint}`, body);
      await onUpdated();
      formElement.reset();
      setNotice("상태와 공식 기록을 저장했습니다.");
    } catch (caught) {
      setError(errorMessage(caught));
    } finally {
      setPending(false);
    }
  }

  const heading = workflowStatus === "FORMAL_AGENDA"
    ? "검토 시작"
    : workflowStatus === "ON_HOLD"
      ? "검토 재개"
      : workflowStatus === "UNDER_REVIEW"
        ? "공식 결정과 답변"
        : workflowStatus === "ACCEPTED"
          ? "실행 시작 기록"
          : "실행 완료 기록";

  return (
    <section className="proposal-workflow-panel" aria-labelledby="workflow-command-title">
      <div>
        <p className="eyebrow">내부 지정 담당 교사</p>
        <h2 id="workflow-command-title">{heading}</h2>
        <p>저장한 내용은 진행 이력과 학교 공식 답변으로 학생에게 공개됩니다.</p>
      </div>
      <form onSubmit={submit}>
        {reasonOnly ? (
          <div className="field">
            <label htmlFor="workflow-reason">변경 사유</label>
            <textarea id="workflow-reason" name="reason" rows={3} maxLength={500} required />
          </div>
        ) : (
          <>
            {workflowStatus === "UNDER_REVIEW" ? (
              <div className="field">
                <label htmlFor="workflow-command">결정</label>
                <select id="workflow-command" name="command" defaultValue="accept">
                  <option value="accept">채택</option>
                  <option value="hold">보류</option>
                  <option value="reject">반려</option>
                </select>
              </div>
            ) : null}
            <div className="field">
              <label htmlFor="workflow-content">공식 답변</label>
              <textarea id="workflow-content" name="content" rows={5} maxLength={10000} required />
            </div>
            <div className="field">
              <label htmlFor="workflow-decision-reason">결정·변경 사유</label>
              <textarea id="workflow-decision-reason" name="decisionReason" rows={3} maxLength={500} required />
            </div>
            <div className="field">
              <label htmlFor="workflow-follow-up">후속 계획 또는 일정</label>
              <textarea id="workflow-follow-up" name="followUpPlan" rows={3} maxLength={10000} />
            </div>
          </>
        )}
        <button className="primary-button" type="submit" disabled={pending}>
          {pending ? "저장 중…" : "공식 기록 저장"}
        </button>
      </form>
      {error ? <p className="form-error" role="alert">{error}</p> : null}
      {notice ? <p className="form-notice" role="status">{notice}</p> : null}
    </section>
  );
}
