"use client";

import type { components } from "@/lib/api-schema";
import { apiPost, errorMessage } from "@/lib/api-client";
import { useRouter } from "next/navigation";
import { FormEvent, useState } from "react";

type ActivationRequest = components["schemas"]["ActivationRequest"];

export function ActivationForm() {
  const router = useRouter();
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSubmitting(true);
    setError(null);
    const form = new FormData(event.currentTarget);
    const request: ActivationRequest = {
      loginId: String(form.get("loginId") ?? ""),
      activationCode: String(form.get("activationCode") ?? ""),
      password: String(form.get("password") ?? ""),
    };
    const confirmation = String(form.get("passwordConfirmation") ?? "");
    if (request.password !== confirmation) {
      setError("비밀번호 확인이 일치하지 않습니다.");
      setSubmitting(false);
      return;
    }
    try {
      await apiPost<void>("/api/v1/auth/activate", request);
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
        <label htmlFor="activation-login-id">로그인 ID</label>
        <input id="activation-login-id" name="loginId" autoComplete="username" required />
      </div>
      <div className="field">
        <label htmlFor="activation-code">가입 코드</label>
        <input
          id="activation-code"
          name="activationCode"
          autoComplete="one-time-code"
          spellCheck={false}
          required
        />
        <p className="field-help">관리자에게 개인별로 전달받은 일회용 코드를 입력합니다.</p>
      </div>
      <div className="field">
        <label htmlFor="new-password">새 비밀번호</label>
        <input id="new-password" name="password" type="password" autoComplete="new-password" minLength={12} required />
        <p className="field-help">12자 이상의 비밀번호나 기억하기 쉬운 긴 문장을 사용하세요.</p>
      </div>
      <div className="field">
        <label htmlFor="password-confirmation">새 비밀번호 확인</label>
        <input
          id="password-confirmation"
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
        {submitting ? "활성화 중…" : "계정 활성화"}
      </button>
      <div className="form-links">
        <a href="/login">이미 활성화했나요? 로그인</a>
      </div>
    </form>
  );
}
