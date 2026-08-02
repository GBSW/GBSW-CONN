import { PasswordResetForm } from "@/components/auth/password-reset-form";
import type { Metadata } from "next";
import Link from "next/link";

export const metadata: Metadata = {
  title: "비밀번호 재설정 | 학교 소통 제안 시스템",
};

export default function PasswordResetPage() {
  return (
    <main className="auth-page">
      <Link className="back-link" href="/">
        ← 처음으로
      </Link>
      <section className="auth-panel" aria-labelledby="password-reset-title">
        <p className="eyebrow">계정 복구</p>
        <h1 id="password-reset-title">비밀번호 재설정</h1>
        <p className="auth-intro">
          관리자가 발급한 개인 재설정 코드를 사용합니다. 변경이 끝나면 기존 로그인은 모두 종료됩니다.
        </p>
        <PasswordResetForm />
      </section>
    </main>
  );
}
