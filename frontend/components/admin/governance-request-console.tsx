"use client";

import { apiGet, apiPost, errorMessage } from "@/lib/api-client";
import { Banner } from "@astryxdesign/core/Banner";
import { Button } from "@astryxdesign/core/Button";
import { EmptyState } from "@astryxdesign/core/EmptyState";
import { Heading } from "@astryxdesign/core/Heading";
import { List, ListItem } from "@astryxdesign/core/List";
import { Section } from "@astryxdesign/core/Section";
import { Spinner } from "@astryxdesign/core/Spinner";
import { HStack, VStack } from "@astryxdesign/core/Stack";
import { Text } from "@astryxdesign/core/Text";
import { TextArea } from "@astryxdesign/core/TextArea";
import { useEffect, useState } from "react";
import { CredentialDeliveryStatus } from "./credential-delivery-status";

type GovernanceRequest = {
  publicId: string;
  changeType: string;
  status: string;
  targetUserPublicId?: string;
  requestedByDisplayName: string;
  requestedAt: string;
  expiresAt: string;
  approvedByDisplayName?: string;
  executedAt?: string;
  deliveryStatus?: string;
};

const labels: Record<string, string> = {
  CREATE_ACCOUNT: "계정 생성",
  REISSUE_ACTIVATION_CODE: "가입 코드 재발급",
  ISSUE_PASSWORD_RESET_CODE: "비밀번호 재설정 코드 발급",
  ASSIGN_ROLE: "역할 부여",
  END_ROLE: "역할 종료",
  APPOINT_OFFICE: "보직 임명",
  END_OFFICE: "보직 종료",
};

export function GovernanceRequestConsole() {
  const [items, setItems] = useState<GovernanceRequest[] | null>(null);
  const [approvalReason, setApprovalReason] = useState<Record<string, string>>({});
  const [pendingId, setPendingId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [version, setVersion] = useState(0);

  useEffect(() => {
    let active = true;
    apiGet<GovernanceRequest[]>("/api/v1/admin/governance/requests?size=50")
      .then((result) => { if (active) { setItems(result); setError(null); } })
      .catch((caught: unknown) => { if (active) setError(errorMessage(caught)); });
    return () => { active = false; };
  }, [version]);

  async function approve(publicId: string) {
    const reason = approvalReason[publicId]?.trim();
    if (!reason) return;
    setPendingId(publicId);
    setError(null);
    try {
      await apiPost<GovernanceRequest>(`/api/v1/admin/governance/requests/${publicId}/approve`, { reason });
      setApprovalReason((current) => ({ ...current, [publicId]: "" }));
      setVersion((current) => current + 1);
    } catch (caught) {
      setError(errorMessage(caught));
    } finally {
      setPendingId(null);
    }
  }

  return (
    <VStack as="section" gap={4} aria-labelledby="governance-title">
      <VStack gap={1} maxWidth="72ch">
        <Heading level={2} id="governance-title">2인 승인 요청</Heading>
        <Text as="p" color="secondary">요청자와 다른 슈퍼 어드민이 최근 본인 확인 후 승인해야 실행됩니다. 자격증명 원문은 이 화면에 표시되지 않습니다.</Text>
      </VStack>
      {error ? <Banner status="error" title="거버넌스 요청을 처리하지 못했습니다" description={error} /> : null}
      {!items ? <Spinner label="승인 요청을 불러오는 중…" /> : items.length === 0 ? <EmptyState title="승인 요청이 없습니다" isCompact /> : (
        <List hasDividers density="spacious">
          {items.map((item) => (
            <ListItem
              key={item.publicId}
              label={`${labels[item.changeType] ?? item.changeType} · ${item.status}`}
              description={`요청자 ${item.requestedByDisplayName} · 만료 ${new Date(item.expiresAt).toLocaleString("ko-KR")}`}
              endContent={item.status === "PENDING" ? (
                <Section variant="muted" padding={3}>
                  <HStack gap={2} vAlign="end" wrap="wrap">
                    <TextArea
                      label="승인 사유"
                      value={approvalReason[item.publicId] ?? ""}
                      onChange={(value) => setApprovalReason((current) => ({ ...current, [item.publicId]: value }))}
                      maxLength={2000}
                      rows={2}
                      width="18rem"
                    />
                    <Button
                      label="승인하고 실행"
                      variant="destructive"
                      isLoading={pendingId === item.publicId}
                      isDisabled={pendingId !== null || !(approvalReason[item.publicId]?.trim())}
                      onClick={() => void approve(item.publicId)}
                    />
                  </HStack>
                </Section>
              ) : <CredentialDeliveryStatus status={item.deliveryStatus} />}
            />
          ))}
        </List>
      )}
    </VStack>
  );
}
