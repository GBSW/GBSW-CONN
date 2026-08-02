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

type ActivationRequest = components["schemas"]["ActivationRequest"];

export function ActivationForm() {
  const router = useRouter();
  const [loginId, setLoginId] = useState("");
  const [activationCode, setActivationCode] = useState("");
  const [password, setPassword] = useState("");
  const [passwordConfirmation, setPasswordConfirmation] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSubmitting(true);
    setError(null);
    const request: ActivationRequest = { loginId, activationCode, password };
    if (password !== passwordConfirmation) {
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
    <form onSubmit={(event) => void submit(event)}>
      <VStack gap={4}>
        <FormLayout>
          <TextInput label="로그인 ID" htmlName="loginId" value={loginId} onChange={setLoginId} isRequired width="100%" />
          <TextInput label="가입 코드" description="관리자에게 개인별로 전달받은 일회용 코드입니다." htmlName="activationCode" value={activationCode} onChange={setActivationCode} isRequired width="100%" />
          <TextInput label="새 비밀번호" description="12자 이상의 비밀번호나 기억하기 쉬운 긴 문장을 사용하세요." htmlName="password" type="password" value={password} onChange={setPassword} isRequired width="100%" />
          <TextInput label="새 비밀번호 확인" htmlName="passwordConfirmation" type="password" value={passwordConfirmation} onChange={setPasswordConfirmation} isRequired width="100%" />
        </FormLayout>
        {error ? <Banner status="error" title="계정을 활성화할 수 없습니다" description={error} /> : null}
        <Button label="계정 활성화" type="submit" variant="primary" width="100%" isLoading={submitting} isDisabled={submitting} />
        <Button label="로그인으로 돌아가기" href="/login" variant="ghost" size="sm" />
      </VStack>
    </form>
  );
}
