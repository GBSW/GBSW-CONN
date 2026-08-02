"use client";

import type { components } from "@/lib/api-schema";
import { apiPost, errorMessage } from "@/lib/api-client";
import { Banner } from "@astryxdesign/core/Banner";
import { Button } from "@astryxdesign/core/Button";
import { Heading } from "@astryxdesign/core/Heading";
import { Section } from "@astryxdesign/core/Section";
import { HStack, VStack } from "@astryxdesign/core/Stack";
import { Text } from "@astryxdesign/core/Text";
import { TextArea } from "@astryxdesign/core/TextArea";
import { FormEvent, useState } from "react";

type ContentReportResponse = components["schemas"]["ContentReportResponse"];

export function ProposalReportForm({ publicId }: { publicId: string }) {
  const [reason, setReason] = useState("");
  const [open, setOpen] = useState(false);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [reported, setReported] = useState(false);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setPending(true);
    setError(null);
    try {
      await apiPost<ContentReportResponse>(`/api/v1/proposals/${publicId}/reports`, { reason });
      setReason("");
      setReported(true);
      setOpen(false);
    } catch (caught) {
      setError(errorMessage(caught));
    } finally {
      setPending(false);
    }
  }

  return (
    <Section variant="muted" padding={5} aria-labelledby="proposal-report-title">
      <VStack gap={3}>
        <VStack gap={1}>
          <Heading level={2} id="proposal-report-title">제안 신고</Heading>
          <Text as="p" color="secondary">신고는 검토 요청만 접수하며, 제안을 자동으로 숨기거나 작성자 신원을 공개하지 않습니다.</Text>
        </VStack>
        {reported ? <Banner status="success" title="신고가 접수되었습니다" description="같은 제안의 중복 신고는 한 건으로 처리됩니다." /> : open ? (
          <form onSubmit={(event) => void submit(event)}>
            <VStack gap={3}>
              <TextArea label="신고 사유" value={reason} onChange={setReason} maxLength={2000} rows={5} isRequired width="100%" />
              {error ? <Banner status="error" title="신고를 접수할 수 없습니다" description={error} /> : null}
              <HStack gap={2} wrap="wrap">
                <Button label="신고 접수" type="submit" variant="primary" isLoading={pending} isDisabled={pending || !reason.trim()} />
                <Button label="취소" variant="ghost" isDisabled={pending} onClick={() => setOpen(false)} />
              </HStack>
            </VStack>
          </form>
        ) : <Button label="신고 사유 작성" variant="secondary" onClick={() => setOpen(true)} />}
      </VStack>
    </Section>
  );
}
