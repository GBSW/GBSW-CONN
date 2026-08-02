import { ActivationForm } from "@/components/auth/activation-form";
import { AuthPageShell } from "@/components/design-system/auth-page-shell";
import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "계정 활성화 | 학교 소통 제안 시스템",
};

export default function ActivationPage() {
  return (
    <AuthPageShell
      title="계정 활성화"
      description="개인 가입 코드를 확인한 뒤 본인만 아는 비밀번호를 설정합니다. 가입 코드는 사용 즉시 폐기됩니다."
    >
      <ActivationForm />
    </AuthPageShell>
  );
}
