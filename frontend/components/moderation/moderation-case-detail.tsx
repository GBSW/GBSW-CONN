"use client";

import type { components } from "@/lib/api-schema";
import { ApiRequestError, apiGet, apiPost, errorMessage } from "@/lib/api-client";
import { officeLabel } from "@/lib/roles";
import { Banner } from "@astryxdesign/core/Banner";
import { Button } from "@astryxdesign/core/Button";
import { Card } from "@astryxdesign/core/Card";
import { EmptyState } from "@astryxdesign/core/EmptyState";
import { Heading } from "@astryxdesign/core/Heading";
import { List, ListItem } from "@astryxdesign/core/List";
import { MetadataList, MetadataListItem } from "@astryxdesign/core/MetadataList";
import { Section } from "@astryxdesign/core/Section";
import { Spinner } from "@astryxdesign/core/Spinner";
import { HStack, VStack } from "@astryxdesign/core/Stack";
import { StatusDot } from "@astryxdesign/core/StatusDot";
import { Text } from "@astryxdesign/core/Text";
import { TextArea } from "@astryxdesign/core/TextArea";
import { TextInput } from "@astryxdesign/core/TextInput";
import { FormEvent, useEffect, useState } from "react";

type ModerationCase = components["schemas"]["ModerationCaseResponse"];
type VoteResult = components["schemas"]["ModerationVoteResultResponse"];
type IdentityReveal = components["schemas"]["IdentityRevealResponse"];

const caseTypeLabels: Record<string, string> = { CONTENT_VISIBILITY: "공개 제한 심의", IDENTITY_REVEAL: "신원 확인 심의" };
const statusLabels: Record<string, string> = { PENDING: "심의 중", APPROVED: "승인", REJECTED: "반려" };
const decisionLabels: Record<string, string> = { APPROVE: "승인", REJECT: "반대" };
const dateTimeFormatter = new Intl.DateTimeFormat("ko-KR", { timeZone: "Asia/Seoul", dateStyle: "medium", timeStyle: "short" });
const statusVariants: Record<string, "accent" | "success" | "error"> = { PENDING: "accent", APPROVED: "success", REJECTED: "error" };
const formatDateTime = (value?: string) => value ? dateTimeFormatter.format(new Date(value)) : "시간 정보 없음";

export function ModerationCaseDetail({ publicId }: { publicId: string }) {
  const [moderationCase, setModerationCase] = useState<ModerationCase | null>(null);
  const [state, setState] = useState<"loading" | "ready" | "signed-out" | "not-found" | "error">("loading");
  const [error, setError] = useState<string | null>(null);
  async function load() { setModerationCase(await apiGet<ModerationCase>(`/api/v1/moderation/cases/${publicId}`)); }

  useEffect(() => {
    let active = true;
    apiGet<ModerationCase>(`/api/v1/moderation/cases/${publicId}`)
      .then((response) => { if (active) { setModerationCase(response); setState("ready"); } })
      .catch((caught: unknown) => {
        if (!active) return;
        if (caught instanceof ApiRequestError && caught.code === "MODERATION_NOT_FOUND") setState("not-found");
        else if (caught instanceof ApiRequestError && ["AUTHENTICATION_REQUIRED", "SESSION_INVALIDATED"].includes(caught.code)) setState("signed-out");
        else { setError(errorMessage(caught)); setState("error"); }
      });
    return () => { active = false; };
  }, [publicId]);

  if (state === "loading") return <Spinner size="lg" label="심의 사건을 불러오고 있습니다…" />;
  if (state === "signed-out") return <EmptyState title="로그인이 필요합니다" actions={<Button label="로그인" href="/login" variant="primary" />} headingLevel={1} />;
  if (state === "not-found") return <EmptyState title="사건을 찾을 수 없습니다" description="이 사건의 고정 심의자에게만 공개됩니다." actions={<Button label="사건함으로" href="/moderation" />} headingLevel={1} />;
  if (state === "error" || !moderationCase) return <Banner status="error" title="사건을 불러오지 못했습니다" description={error ?? undefined} />;

  const pendingVote = moderationCase.caseStatus === "PENDING" && !moderationCase.viewerVoted;
  const canReveal = moderationCase.caseType === "IDENTITY_REVEAL" && moderationCase.caseStatus === "APPROVED" && moderationCase.viewerOffice === "STUDENT_AFFAIRS_TEACHER";
  const status = moderationCase.caseStatus ?? "";

  return (
    <VStack as="article" gap={8}>
      <VStack gap={3}>
        <Button label="심의 사건함으로" href="/moderation" variant="ghost" />
        <HStack gap={2} vAlign="center" wrap="wrap">
          <StatusDot variant={statusVariants[status] ?? "accent"} label={statusLabels[status] ?? status} />
          <Text type="label">{caseTypeLabels[moderationCase.caseType ?? ""] ?? moderationCase.caseType} · {statusLabels[status] ?? status}</Text>
        </HStack>
        <Heading level={1} type="display-2">{moderationCase.proposalTitle ?? "제목 없는 제안"}</Heading>
        <Text as="p" color="secondary">{officeLabel(moderationCase.viewerOffice ?? "")} 자격으로 심의 중</Text>
      </VStack>

      <Section variant="muted" padding={6} aria-labelledby="evidence-title">
        <VStack gap={4}>
          <Heading level={2} id="evidence-title">심의 자료</Heading>
          <Text as="p" type="large" className="pre-wrap wrap-anywhere">{moderationCase.proposalContent}</Text>
          <MetadataList columns="single">
            <MetadataListItem label="공개 작성자 표시">{moderationCase.authorVisibility === "NAMED" ? moderationCase.authorDisplayName : "익명"}</MetadataListItem>
            <MetadataListItem label="신고 사유">{moderationCase.sourceReportReason}</MetadataListItem>
            <MetadataListItem label="사건 생성 사유">{moderationCase.caseReason}</MetadataListItem>
            <MetadataListItem label="사건 생성">{formatDateTime(moderationCase.createdAt)}</MetadataListItem>
          </MetadataList>
        </VStack>
      </Section>

      <VStack as="section" gap={4} aria-labelledby="votes-title">
        <VStack gap={1}>
          <Heading level={2} id="votes-title">의결 현황</Heading>
          <Text as="p" color="secondary">세 명이 모두 승인해야 승인되며, 한 명이라도 반대하면 반려됩니다.</Text>
        </VStack>
        {(moderationCase.votes ?? []).length === 0 ? <EmptyState title="아직 등록된 의결이 없습니다" isCompact /> : (
          <List listStyle="decimal" density="spacious" hasDividers>
            {(moderationCase.votes ?? []).map((vote, index) => (
              <ListItem
                key={`${vote.office}-${vote.createdAt}-${index}`}
                label={`${officeLabel(vote.office ?? "")} · ${decisionLabels[vote.decision ?? ""] ?? vote.decision}`}
                description={`${vote.reason} · ${formatDateTime(vote.createdAt)}`}
              />
            ))}
          </List>
        )}
      </VStack>

      {pendingVote ? <VoteForm publicId={publicId} onVoted={load} /> : moderationCase.viewerVoted ? <Banner status="success" title="이 사건에 대한 의결을 완료했습니다" /> : null}
      {canReveal ? moderationCase.identityRevealed ? <Banner status="info" title="신원 확인을 이미 완료했습니다" description="일회성 결과는 다시 표시되지 않습니다." /> : <IdentityRevealForm publicId={publicId} onRevealed={() => setModerationCase((current) => current ? { ...current, identityRevealed: true } : current)} /> : null}
    </VStack>
  );
}

function VoteForm({ publicId, onVoted }: { publicId: string; onVoted: () => Promise<void> }) {
  const [reason, setReason] = useState("");
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  async function vote(decision: "approve" | "reject") {
    setPending(true); setError(null);
    try { await apiPost<VoteResult>(`/api/v1/moderation/cases/${publicId}/votes/${decision}`, { reason }); await onVoted(); }
    catch (caught) { setError(errorMessage(caught)); }
    finally { setPending(false); }
  }
  return (
    <Section variant="section" padding={6} dividers={["top", "bottom"]} aria-labelledby="vote-form-title">
      <VStack gap={4}>
        <VStack gap={1}><Heading level={2} id="vote-form-title">내 의결</Heading><Text as="p" color="secondary">등록 후에는 바꿀 수 없습니다. 근거를 남기고 결정해 주세요.</Text></VStack>
        <TextArea label="의결 사유" value={reason} onChange={setReason} maxLength={2000} rows={5} isRequired width="100%" />
        {error ? <Banner status="error" title="의결을 등록할 수 없습니다" description={error} /> : null}
        <HStack gap={2} wrap="wrap">
          <Button label="승인" variant="primary" isLoading={pending} isDisabled={pending || !reason.trim()} clickAction={() => vote("approve")} />
          <Button label="반대" variant="destructive" isDisabled={pending || !reason.trim()} clickAction={() => vote("reject")} />
        </HStack>
      </VStack>
    </Section>
  );
}

function IdentityRevealForm({ publicId, onRevealed }: { publicId: string; onRevealed: () => void }) {
  const [password, setPassword] = useState("");
  const [reason, setReason] = useState("");
  const [identity, setIdentity] = useState<IdentityReveal | null>(null);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  async function reveal(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); setPending(true); setError(null);
    try {
      await apiPost<unknown>("/api/v1/auth/reauthenticate", { password });
      const result = await apiPost<IdentityReveal>(`/api/v1/identity-reveal-cases/${publicId}/reveal`, { reason });
      setPassword(""); setReason(""); setIdentity(result);
    } catch (caught) { setError(errorMessage(caught)); }
    finally { setPending(false); }
  }

  if (identity) return (
    <Card padding={6} variant="red" elevation="med">
      <VStack gap={4}>
        <Heading level={2}>확인된 작성자 · 일회성 표시</Heading>
        <MetadataList columns="single">
          <MetadataListItem label="로그인 ID">{identity.loginId}</MetadataListItem>
          <MetadataListItem label="이름">{identity.displayName}</MetadataListItem>
          <MetadataListItem label="확인 시각">{formatDateTime(identity.revealedAt)}</MetadataListItem>
        </MetadataList>
        <Text as="p">이 정보는 현재 화면 상태에만 있으며, 닫은 뒤 다시 조회할 수 없습니다.</Text>
        <Button label="확인 결과 닫기" variant="destructive" onClick={onRevealed} />
      </VStack>
    </Card>
  );

  return (
    <Section variant="muted" padding={6} aria-labelledby="identity-reveal-title">
      <VStack gap={4}>
        <VStack gap={1}><Heading level={2} id="identity-reveal-title">작성자 신원 일회 확인</Heading><Text as="p" color="secondary">학생부장교사만 승인된 사건에서 사용할 수 있습니다. 비밀번호로 다시 인증한 뒤 결과가 한 번 표시됩니다.</Text></VStack>
        <form onSubmit={(event) => void reveal(event)}>
          <VStack gap={4}>
            <TextInput label="현재 비밀번호" type="password" value={password} onChange={setPassword} isRequired width="100%" />
            <TextArea label="확인 사유" value={reason} onChange={setReason} maxLength={2000} rows={5} isRequired width="100%" />
            {error ? <Banner status="error" title="작성자 신원을 확인할 수 없습니다" description={error} /> : null}
            <Button label="재인증하고 한 번 확인" type="submit" variant="destructive" isLoading={pending} isDisabled={pending || !password || !reason.trim()} />
          </VStack>
        </form>
      </VStack>
    </Section>
  );
}
