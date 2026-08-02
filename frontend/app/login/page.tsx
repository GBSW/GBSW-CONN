import { LoginForm } from "@/components/auth/login-form";
import type { Metadata } from "next";
import Link from "next/link";

export const metadata: Metadata = {
  title: "로그인 | 학교 소통 제안 시스템",
};

export default function LoginPage() {
  return (
    <main className="auth-page">
      <Link className="back-link" href="/">
        ← 처음으로
      </Link>
      <section className="auth-panel" aria-labelledby="login-title">
        <p className="eyebrow">계정</p>
        <h1 id="login-title">로그인</h1>
        <p className="auth-intro">학교에서 발급한 개인 계정으로 로그인합니다.</p>
        <LoginForm />
      </section>
    </main>
  );
}
