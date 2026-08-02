"use client";

import type { components } from "@/lib/api-schema";
import { apiPost, errorMessage } from "@/lib/api-client";
import { FormEvent, useState } from "react";

type ContentReportResponse = components["schemas"]["ContentReportResponse"];

export function ProposalReportForm({ publicId }: { publicId: string }) {
  const [reason, setReason] = useState("");
  const [open, setOpen] = useState(false);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [reported, setReported] = useState(false);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setPending(true);
    setError(null);
    try {
      await apiPost<ContentReportResponse>(`/api/v1/proposals/${publicId}/reports`, { reason });
      setReason("");
      setReported(true);
      setOpen(false);
    } catch (caught) {
      setError(errorMessage(caught));
    } finally {
      setPending(false);
    }
  }

  return (
    <section className="proposal-report" aria-labelledby="proposal-report-title">
      <div>
        <h2 id="proposal-report-title">제안 신고</h2>
        <p>신고는 검토 요청을 접수할 뿐, 제안을 자동으로 숨기거나 작성자 신원을 공개하지 않습니다.</p>
      </div>
      {reported ? (
        <p className="form-notice" role="status">신고가 접수되었습니다. 같은 제안의 중복 신고는 한 건으로 처리됩니다.</p>
      ) : open ? (
        <form className="compact-form" onSubmit={(event) => void submit(event)}>
          <div className="field">
            <label htmlFor="report-reason">신고 사유</label>
            <textarea
              id="report-reason"
              value={reason}
              onChange={(event) => setReason(event.target.value)}
              maxLength={2000}
              required
            />
          </div>
          {error ? <p className="form-error" role="alert">{error}</p> : null}
          <div className="button-row">
            <button className="primary-button" type="submit" disabled={pending}>
              {pending ? "접수 중…" : "신고 접수"}
            </button>
            <button className="secondary-button" type="button" disabled={pending} onClick={() => setOpen(false)}>
              취소
            </button>
          </div>
        </form>
      ) : (
        <button className="secondary-button" type="button" onClick={() => setOpen(true)}>신고 사유 작성</button>
      )}
    </section>
  );
}
