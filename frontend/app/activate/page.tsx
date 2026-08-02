import { ActivationForm } from "@/components/auth/activation-form";
import type { Metadata } from "next";
import Link from "next/link";

export const metadata: Metadata = {
  title: "계정 활성화 | 학교 소통 제안 시스템",
};

export default function ActivationPage() {
  return (
    <main className="auth-page">
      <Link className="back-link" href="/">
        ← 처음으로
      </Link>
      <section className="auth-panel" aria-labelledby="activation-title">
        <p className="eyebrow">처음 한 번</p>
        <h1 id="activation-title">계정 활성화</h1>
        <p className="auth-intro">
          개인 가입 코드를 확인한 뒤 본인만 아는 비밀번호를 설정합니다. 가입 코드는 사용 즉시 폐기됩니다.
        </p>
        <ActivationForm />
      </section>
    </main>
  );
}
