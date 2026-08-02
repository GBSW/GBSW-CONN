import { LoginForm } from "@/components/auth/login-form";
import { AuthPageShell } from "@/components/design-system/auth-page-shell";
import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "로그인 | 학교 소통 제안 시스템",
};

export default function LoginPage() {
  return (
    <AuthPageShell title="로그인" description="학교에서 발급한 개인 계정으로 로그인합니다.">
      <LoginForm />
    </AuthPageShell>
  );
}
