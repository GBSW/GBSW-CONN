"use client";

import type { components } from "@/lib/api-schema";
import { apiPost, errorMessage } from "@/lib/api-client";
import { useRouter } from "next/navigation";
import { FormEvent, useState } from "react";

type LoginRequest = components["schemas"]["LoginRequest"];
type CurrentUserResponse = components["schemas"]["CurrentUserResponse"];

export function LoginForm() {
  const router = useRouter();
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSubmitting(true);
    setError(null);
    const form = new FormData(event.currentTarget);
    const request: LoginRequest = {
      loginId: String(form.get("loginId") ?? ""),
      password: String(form.get("password") ?? ""),
    };
    try {
      await apiPost<CurrentUserResponse>("/api/v1/auth/login", request);
      router.replace("/");
      router.refresh();
    } catch (caught) {
      setError(errorMessage(caught));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form className="auth-form" onSubmit={(event) => void submit(event)}>
      <div className="field">
        <label htmlFor="login-id">로그인 ID</label>
        <input id="login-id" name="loginId" autoComplete="username" required />
      </div>
      <div className="field">
        <label htmlFor="password">비밀번호</label>
        <input id="password" name="password" type="password" autoComplete="current-password" required />
      </div>
      {error ? (
        <p className="form-error" role="alert">
          {error}
        </p>
      ) : null}
      <button className="primary-button" type="submit" disabled={submitting}>
        {submitting ? "확인 중…" : "로그인"}
      </button>
      <div className="form-links">
        <a href="/activate">처음 사용하는 계정인가요?</a>
        <a href="/password-reset">재설정 코드를 받았나요?</a>
      </div>
    </form>
  );
}
