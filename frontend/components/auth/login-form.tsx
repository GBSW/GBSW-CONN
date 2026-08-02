"use client";

import type { components } from "@/lib/api-schema";
import { apiPost, errorMessage } from "@/lib/api-client";
import { Banner } from "@astryxdesign/core/Banner";
import { Button } from "@astryxdesign/core/Button";
import { FormLayout } from "@astryxdesign/core/FormLayout";
import { HStack, VStack } from "@astryxdesign/core/Stack";
import { TextInput } from "@astryxdesign/core/TextInput";
import { useRouter } from "next/navigation";
import { FormEvent, useState } from "react";

type LoginRequest = components["schemas"]["LoginRequest"];
type CurrentUserResponse = components["schemas"]["CurrentUserResponse"];

export function LoginForm() {
  const router = useRouter();
  const [loginId, setLoginId] = useState("");
  const [password, setPassword] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSubmitting(true);
    setError(null);
    const request: LoginRequest = { loginId, password };
    try {
      await apiPost<CurrentUserResponse>("/api/v1/auth/login", request);
      router.replace("/dashboard");
      router.refresh();
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
          <TextInput label="비밀번호" htmlName="password" type="password" value={password} onChange={setPassword} isRequired width="100%" />
        </FormLayout>
        {error ? <Banner status="error" title="로그인할 수 없습니다" description={error} /> : null}
        <Button label="로그인" type="submit" variant="primary" width="100%" isLoading={submitting} isDisabled={submitting} />
        <HStack gap={2} wrap="wrap">
          <Button label="계정 활성화" href="/activate" variant="ghost" size="sm" />
          <Button label="비밀번호 재설정" href="/password-reset" variant="ghost" size="sm" />
        </HStack>
      </VStack>
    </form>
  );
}
