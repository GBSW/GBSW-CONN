"use client";

import type { components } from "@/lib/api-schema";
import { ApiRequestError, apiGet, apiPost, errorMessage } from "@/lib/api-client";
import Link from "next/link";
import { useRouter } from "next/navigation";
import type { FormEvent } from "react";
import { useEffect, useState } from "react";

type CurrentUser = components["schemas"]["CurrentUserResponse"];
type CreateProposalRequest = components["schemas"]["CreateProposalRequest"];
type ProposalDetail = components["schemas"]["ProposalDetailResponse"];

export function ProposalForm() {
  const router = useRouter();
  const [access, setAccess] = useState<"loading" | "ready" | "signed-out" | "forbidden" | "error">("loading");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    apiGet<CurrentUser>("/api/v1/auth/me")
      .then((user) => {
        if (active) setAccess(user.roles.includes("STUDENT") ? "ready" : "forbidden");
      })
      .catch((caught: unknown) => {
        if (!active) return;
        const signedOut = caught instanceof ApiRequestError
          && ["AUTHENTICATION_REQUIRED", "SESSION_INVALIDATED"].includes(caught.code);
        setAccess(signedOut ? "signed-out" : "error");
      });
    return () => {
      active = false;
    };
  }, []);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSubmitting(true);
    setError(null);
    const form = new FormData(event.currentTarget);
    const request: CreateProposalRequest = {
      title: String(form.get("title") ?? "").trim(),
      content: String(form.get("content") ?? "").trim(),
      authorVisibility: String(form.get("authorVisibility") ?? "ANONYMOUS") as CreateProposalRequest["authorVisibility"],
    };
    try {
      const created = await apiPost<ProposalDetail>("/api/v1/proposals", request);
      router.replace(`/proposals/${created.publicId}`);
    } catch (caught) {
      setError(errorMessage(caught));
      setSubmitting(false);
    }
  }

  if (access === "loading") return <p className="proposal-state" role="status">작성 권한을 확인하고 있습니다…</p>;
  if (access === "signed-out") {
    return <section className="proposal-state"><h1>로그인이 필요합니다</h1><Link className="primary-link" href="/login">로그인</Link></section>;
  }
  if (access === "forbidden") {
    return <section className="proposal-state"><h1>학생만 제안을 작성할 수 있습니다</h1><Link href="/proposals">제안 목록으로</Link></section>;
  }
  if (access === "error") return <p className="form-error" role="alert">작성 권한을 확인하지 못했습니다.</p>;

  return (
    <section className="proposal-compose" aria-labelledby="compose-title">
      <Link className="back-link" href="/proposals">← 제안 목록</Link>
      <p className="eyebrow">공개 학교 개선 제안</p>
      <h1 id="compose-title">제안 작성</h1>
      <p className="proposal-compose-intro">
        등록한 내용은 학생들에게 공개되며 작성자의 동의 1표가 자동으로 포함됩니다.
        개인 고충이나 안전 제보는 이 양식에 작성하지 마세요.
      </p>
      <form className="proposal-form" onSubmit={(event) => void submit(event)}>
        <div className="field">
          <label htmlFor="proposal-title">제목</label>
          <input id="proposal-title" name="title" maxLength={200} required />
        </div>
        <div className="field">
          <label htmlFor="proposal-content">내용</label>
          <textarea id="proposal-content" name="content" maxLength={10000} rows={12} required />
          <p className="field-help">첨부파일과 HTML 실행은 지원하지 않습니다.</p>
        </div>
        <fieldset className="visibility-options">
          <legend>작성자 표시</legend>
          <label>
            <input type="radio" name="authorVisibility" value="ANONYMOUS" defaultChecked />
            <span><strong>익명으로 공개</strong><small>다른 학생과 교사에게 이름을 표시하지 않습니다.</small></span>
          </label>
          <label>
            <input type="radio" name="authorVisibility" value="NAMED" />
            <span><strong>이름 공개</strong><small>현재 계정의 표시 이름을 함께 공개합니다.</small></span>
          </label>
        </fieldset>
        <p className="privacy-note">
          익명을 선택해도 계정 남용 대응을 위한 작성자 연결정보는 암호화된 보호 영역에 분리 보관됩니다.
        </p>
        {error ? <p className="form-error" role="alert">{error}</p> : null}
        <div className="form-actions">
          <Link href="/proposals">취소</Link>
          <button className="primary-button" type="submit" disabled={submitting}>
            {submitting ? "등록 중…" : "제안 등록"}
          </button>
        </div>
      </form>
    </section>
  );
}
