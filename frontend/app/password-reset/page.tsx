import { PasswordResetForm } from "@/components/auth/password-reset-form";
import { AuthPageShell } from "@/components/design-system/auth-page-shell";
import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "비밀번호 재설정 | 학교 소통 제안 시스템",
};

export default function PasswordResetPage() {
  return (
    <AuthPageShell
      title="비밀번호 재설정"
      description="관리자가 발급한 개인 재설정 코드를 사용합니다. 변경이 끝나면 기존 로그인은 모두 종료됩니다."
    >
      <PasswordResetForm />
    </AuthPageShell>
  );
}
