"use client";

import type { components } from "@/lib/api-schema";
import { apiPost, errorMessage } from "@/lib/api-client";
import { Banner } from "@astryxdesign/core/Banner";
import { Button } from "@astryxdesign/core/Button";
import { FormLayout } from "@astryxdesign/core/FormLayout";
import { VStack } from "@astryxdesign/core/Stack";
import { TextInput } from "@astryxdesign/core/TextInput";
import { useRouter } from "next/navigation";
import { FormEvent, useState } from "react";

type PasswordResetRequest = components["schemas"]["PasswordResetRequest"];

export function PasswordResetForm() {
  const router = useRouter();
  const [loginId, setLoginId] = useState("");
  const [resetCode, setResetCode] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [passwordConfirmation, setPasswordConfirmation] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSubmitting(true);
    setError(null);
    const request: PasswordResetRequest = { loginId, resetCode, newPassword };
    if (newPassword !== passwordConfirmation) {
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
    <form onSubmit={(event) => void submit(event)}>
      <VStack gap={4}>
        <FormLayout>
          <TextInput label="로그인 ID" htmlName="loginId" value={loginId} onChange={setLoginId} isRequired width="100%" />
          <TextInput label="재설정 코드" htmlName="resetCode" value={resetCode} onChange={setResetCode} isRequired width="100%" />
          <TextInput label="새 비밀번호" htmlName="newPassword" type="password" value={newPassword} onChange={setNewPassword} isRequired width="100%" />
          <TextInput label="새 비밀번호 확인" htmlName="passwordConfirmation" type="password" value={passwordConfirmation} onChange={setPasswordConfirmation} isRequired width="100%" />
        </FormLayout>
        {error ? <Banner status="error" title="비밀번호를 변경할 수 없습니다" description={error} /> : null}
        <Button label="비밀번호 변경" type="submit" variant="primary" width="100%" isLoading={submitting} isDisabled={submitting} />
        <Button label="로그인으로 돌아가기" href="/login" variant="ghost" size="sm" />
      </VStack>
    </form>
  );
}
