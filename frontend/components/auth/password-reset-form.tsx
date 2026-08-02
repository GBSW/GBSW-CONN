"use client";

import type { components } from "@/lib/api-schema";
import { apiPost, errorMessage } from "@/lib/api-client";
import { useRouter } from "next/navigation";
import { FormEvent, useState } from "react";

type PasswordResetRequest = components["schemas"]["PasswordResetRequest"];

export function PasswordResetForm() {
  const router = useRouter();
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSubmitting(true);
    setError(null);
    const form = new FormData(event.currentTarget);
    const request: PasswordResetRequest = {
      loginId: String(form.get("loginId") ?? ""),
      resetCode: String(form.get("resetCode") ?? ""),
      newPassword: String(form.get("newPassword") ?? ""),
    };
    if (request.newPassword !== String(form.get("passwordConfirmation") ?? "")) {
      setError("비밀번호 확인이 일치하지 않습니다.");
      setSubmitting(false);
      return;
    }
    try {
      await apiPost<void>("/api/v1/auth/password-reset/complete", request);
      router.replace("/login");
    } catch (caught) {
      setError(errorMessage(caught));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form className="auth-form" onSubmit={(event) => void submit(event)}>
      <div className="field">
        <label htmlFor="reset-login-id">로그인 ID</label>
        <input id="reset-login-id" name="loginId" autoComplete="username" required />
      </div>
      <div className="field">
        <label htmlFor="reset-code">재설정 코드</label>
        <input id="reset-code" name="resetCode" autoComplete="one-time-code" spellCheck={false} required />
      </div>
      <div className="field">
        <label htmlFor="reset-password">새 비밀번호</label>
        <input id="reset-password" name="newPassword" type="password" autoComplete="new-password" minLength={12} required />
      </div>
      <div className="field">
        <label htmlFor="reset-password-confirmation">새 비밀번호 확인</label>
        <input
          id="reset-password-confirmation"
          name="passwordConfirmation"
          type="password"
          autoComplete="new-password"
          minLength={12}
          required
        />
      </div>
      {error ? (
        <p className="form-error" role="alert">
          {error}
        </p>
      ) : null}
      <button className="primary-button" type="submit" disabled={submitting}>
        {submitting ? "변경 중…" : "비밀번호 변경"}
      </button>
      <div className="form-links">
        <a href="/login">로그인으로 돌아가기</a>
      </div>
    </form>
  );
}
