"use client";

import type { components } from "@/lib/api-schema";
import { ApiRequestError, apiPost, errorMessage } from "@/lib/api-client";
import { Banner } from "@astryxdesign/core/Banner";
import { Button } from "@astryxdesign/core/Button";
import { Heading } from "@astryxdesign/core/Heading";
import { Section } from "@astryxdesign/core/Section";
import { Selector } from "@astryxdesign/core/Selector";
import { VStack } from "@astryxdesign/core/Stack";
import { Text } from "@astryxdesign/core/Text";
import { TextArea } from "@astryxdesign/core/TextArea";
import type { FormEvent } from "react";
import { useState } from "react";

type OfficialResponseRequest = components["schemas"]["OfficialResponseRequest"];
type ProposalWorkflowResponse = components["schemas"]["ProposalWorkflowResponse"];

const decisionOptions = [
  { value: "accept", label: "채택" },
  { value: "hold", label: "보류" },
  { value: "reject", label: "반려" },
];

export function ProposalWorkflowPanel({
  publicId,
  workflowStatus,
  onUpdated,
  onUnavailable,
  onSignedOut,
}: {
  publicId: string;
  workflowStatus: string;
  onUpdated: () => Promise<void>;
  onUnavailable: () => void;
  onSignedOut: () => void;
}) {
  const [command, setCommand] = useState("accept");
  const [reason, setReason] = useState("");
  const [content, setContent] = useState("");
  const [decisionReason, setDecisionReason] = useState("");
  const [followUpPlan, setFollowUpPlan] = useState("");
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const reasonOnly = workflowStatus === "FORMAL_AGENDA" || workflowStatus === "ON_HOLD";
  const responseCommand = ["UNDER_REVIEW", "ACCEPTED", "IN_PROGRESS"].includes(workflowStatus);
  if (!reasonOnly && !responseCommand) return null;

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setPending(true);
    setError(null);
    setNotice(null);
    try {
      let endpoint: string;
      let body: components["schemas"]["ProposalTransitionReasonRequest"] | OfficialResponseRequest;
      if (workflowStatus === "FORMAL_AGENDA") {
        endpoint = "review-start";
        body = { reason: reason.trim() };
      } else if (workflowStatus === "ON_HOLD") {
        endpoint = "review-resume";
        body = { reason: reason.trim() };
      } else {
        const resolvedCommand = workflowStatus === "UNDER_REVIEW" ? command : workflowStatus === "ACCEPTED" ? "execution-start" : "execution-complete";
        endpoint = workflowStatus === "UNDER_REVIEW" ? `decisions/${resolvedCommand}` : resolvedCommand;
        body = {
          content: content.trim(),
          decisionReason: decisionReason.trim(),
          ...(followUpPlan.trim() ? { followUpPlan: followUpPlan.trim() } : {}),
        };
      }
      await apiPost<ProposalWorkflowResponse>(`/api/v1/proposals/${publicId}/${endpoint}`, body);
      await onUpdated();
      setReason("");
      setContent("");
      setDecisionReason("");
      setFollowUpPlan("");
      setNotice("상태와 공식 기록을 저장했습니다.");
    } catch (caught) {
      if (caught instanceof ApiRequestError && caught.code === "PROPOSAL_NOT_FOUND") {
        onUnavailable();
        return;
      }
      if (caught instanceof ApiRequestError && ["AUTHENTICATION_REQUIRED", "SESSION_INVALIDATED"].includes(caught.code)) {
        onSignedOut();
        return;
      }
      setError(errorMessage(caught));
    } finally {
      setPending(false);
    }
  }

  const heading = workflowStatus === "FORMAL_AGENDA" ? "검토 시작" : workflowStatus === "ON_HOLD" ? "검토 재개" : workflowStatus === "UNDER_REVIEW" ? "공식 결정과 답변" : workflowStatus === "ACCEPTED" ? "실행 시작 기록" : "실행 완료 기록";
  const canSubmit = reasonOnly ? Boolean(reason.trim()) : Boolean(content.trim() && decisionReason.trim());

  return (
    <Section variant="section" padding={6} dividers={["top", "bottom"]} aria-labelledby="workflow-command-title">
      <VStack gap={4}>
        <VStack gap={1}>
          <Heading level={2} id="workflow-command-title">{heading}</Heading>
          <Text as="p" color="secondary">저장한 내용은 진행 이력과 학교 공식 답변으로 학생에게 공개됩니다.</Text>
        </VStack>
        <form onSubmit={submit}>
          <VStack gap={4}>
            {reasonOnly ? (
              <TextArea label="변경 사유" value={reason} onChange={setReason} maxLength={500} rows={4} isRequired width="100%" />
            ) : (
              <>
                {workflowStatus === "UNDER_REVIEW" ? <Selector label="결정" options={decisionOptions} value={command} onChange={setCommand} width="100%" /> : null}
                <TextArea label="공식 답변" value={content} onChange={setContent} maxLength={10000} rows={6} isRequired width="100%" />
                <TextArea label="결정·변경 사유" value={decisionReason} onChange={setDecisionReason} maxLength={500} rows={4} isRequired width="100%" />
                <TextArea label="후속 계획 또는 일정" value={followUpPlan} onChange={setFollowUpPlan} maxLength={10000} rows={4} isOptional width="100%" />
              </>
            )}
            <Button label="공식 기록 저장" type="submit" variant="primary" isLoading={pending} isDisabled={pending || !canSubmit} />
          </VStack>
        </form>
        {error ? <Banner status="error" title="공식 기록을 저장할 수 없습니다" description={error} /> : null}
        {notice ? <Banner status="success" title={notice} /> : null}
      </VStack>
    </Section>
  );
}
