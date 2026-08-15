"use client";

import type { components } from "@/lib/api-schema";
import { ApiRequestError, apiDelete, apiGet, apiPut, errorMessage } from "@/lib/api-client";
import { ProposalWorkflowPanel } from "@/components/proposals/proposal-workflow-panel";
import { ProposalReportForm } from "@/components/proposals/proposal-report-form";
import { ProposalComments } from "@/components/proposals/proposal-comments";
import { AlertDialog } from "@astryxdesign/core/AlertDialog";
import { Banner } from "@astryxdesign/core/Banner";
import { Button } from "@astryxdesign/core/Button";
import { EmptyState } from "@astryxdesign/core/EmptyState";
import { Heading } from "@astryxdesign/core/Heading";
import { List, ListItem } from "@astryxdesign/core/List";
import { MetadataList, MetadataListItem } from "@astryxdesign/core/MetadataList";
import { ProgressBar } from "@astryxdesign/core/ProgressBar";
import { Section } from "@astryxdesign/core/Section";
import { Spinner } from "@astryxdesign/core/Spinner";
import { HStack, VStack } from "@astryxdesign/core/Stack";
import { StatusDot } from "@astryxdesign/core/StatusDot";
import { Text } from "@astryxdesign/core/Text";
import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";

type CurrentUser = components["schemas"]["CurrentUserResponse"];
type ProposalDetailResponse = components["schemas"]["ProposalDetailResponse"];
type ProposalStatusHistory = components["schemas"]["ProposalStatusHistoryResponse"];
type SupportResponse = components["schemas"]["SupportResponse"];

const dateTimeFormatter = new Intl.DateTimeFormat("ko-KR", { timeZone: "Asia/Seoul", dateStyle: "medium", timeStyle: "short" });
const statusLabels: Record<string, string> = {
  GATHERING_SUPPORT: "동의 모집 중", FORMAL_AGENDA: "정식 안건", UNDER_REVIEW: "검토 중", ACCEPTED: "채택",
  ON_HOLD: "보류", REJECTED: "반려", IN_PROGRESS: "실행 중", COMPLETED: "완료",
};
const statusVariants: Record<string, "accent" | "success" | "warning" | "error" | "neutral"> = {
  GATHERING_SUPPORT: "accent", FORMAL_AGENDA: "success", UNDER_REVIEW: "accent", ACCEPTED: "success",
  ON_HOLD: "warning", REJECTED: "error", IN_PROGRESS: "accent", COMPLETED: "success",
};

/**
 * 진행 이력 한 줄의 설명을 만든다.
 * 동의 수는 nullable이므로 문자열에 그대로 보간하지 않는다. 값이 없으면 해당
 * 지표를 빼고, `null명` 같은 표기가 화면에 나가지 않게 한다.
 */
function historyDescription(history: ProposalStatusHistory): string {
  const parts = [history.reason, dateTimeFormatter.format(new Date(history.createdAt))];
  if (typeof history.supportCountSnapshot === "number" && Number.isFinite(history.supportCountSnapshot)) {
    parts.push(`전환 당시 유효 동의 ${history.supportCountSnapshot}명`);
  }
  return parts.join(" · ");
}

export function ProposalDetail({ publicId }: { publicId: string }) {
  const router = useRouter();
  const [user, setUser] = useState<CurrentUser | null>(null);
  const [proposal, setProposal] = useState<ProposalDetailResponse | null>(null);
  const [state, setState] = useState<"loading" | "ready" | "signed-out" | "not-found" | "error">("loading");
  const [actionPending, setActionPending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [withdrawOpen, setWithdrawOpen] = useState(false);
  const [withdrawPending, setWithdrawPending] = useState(false);
  const [reloadVersion, setReloadVersion] = useState(0);
  const [loadError, setLoadError] = useState<string | null>(null);

  function showUnavailable() {
    setProposal(null);
    setError(null);
    setNotice(null);
    setWithdrawOpen(false);
    setState("not-found");
  }

  function showSignedOut() {
    setProposal(null);
    setError(null);
    setNotice(null);
    setWithdrawOpen(false);
    setState("signed-out");
  }

  function handleFatalError(caught: unknown): boolean {
    if (caught instanceof ApiRequestError && caught.code === "PROPOSAL_NOT_FOUND") {
      showUnavailable();
      return true;
    }
    if (caught instanceof ApiRequestError && ["AUTHENTICATION_REQUIRED", "SESSION_INVALIDATED"].includes(caught.code)) {
      showSignedOut();
      return true;
    }
    return false;
  }

  useEffect(() => {
    let active = true;
    apiGet<CurrentUser>("/api/v1/auth/me")
      .then(async (currentUser) => {
        const response = await apiGet<ProposalDetailResponse>(`/api/v1/proposals/${publicId}`);
        if (!active) return;
        setUser(currentUser);
        setProposal(response);
        setState("ready");
      })
      .catch((caught: unknown) => {
        if (!active) return;
        if (caught instanceof ApiRequestError && caught.code === "PROPOSAL_NOT_FOUND") setState("not-found");
        else if (caught instanceof ApiRequestError && ["AUTHENTICATION_REQUIRED", "SESSION_INVALIDATED"].includes(caught.code)) setState("signed-out");
        else {
          setLoadError(errorMessage(caught));
          setState("error");
        }
      });
    return () => { active = false; };
  }, [publicId, reloadVersion]);

  async function refreshProposal() {
    try {
      setProposal(await apiGet<ProposalDetailResponse>(`/api/v1/proposals/${publicId}`));
    } catch (caught) {
      if (!handleFatalError(caught)) throw caught;
    }
  }

  async function changeSupport() {
    if (!proposal) return;
    setActionPending(true);
    setError(null);
    setNotice(null);
    try {
      const result = proposal.viewerSupported
        ? await apiDelete<SupportResponse>(`/api/v1/proposals/${publicId}/support`)
        : await apiPut<SupportResponse>(`/api/v1/proposals/${publicId}/support`);
      setProposal((current) => current ? {
        ...current, viewerSupported: result.supported, supportCount: result.supportCount, workflowStatus: result.workflowStatus,
        formalizedSupportCount: result.justFormalized ? result.supportCount : current.formalizedSupportCount,
        formalizedAt: result.justFormalized ? new Date().toISOString() : current.formalizedAt,
      } : current);
      setNotice(result.justFormalized ? "50명의 동의에 도달해 정식 안건으로 승격되었습니다." : result.supported ? "이 제안에 동의했습니다." : "동의를 철회했습니다.");
    } catch (caught) {
      if (!handleFatalError(caught)) setError(errorMessage(caught));
    } finally {
      setActionPending(false);
    }
  }

  async function withdrawProposal() {
    setWithdrawPending(true);
    setError(null);
    try {
      await apiDelete<void>(`/api/v1/proposals/${publicId}`);
      setWithdrawOpen(false);
      router.replace("/proposals");
      router.refresh();
    } catch (caught) {
      if (!handleFatalError(caught)) setError(errorMessage(caught));
      setWithdrawOpen(false);
      setWithdrawPending(false);
    }
  }

  if (state === "loading") return <Spinner size="lg" label="제안을 불러오고 있습니다…" />;
  if (state === "signed-out") return <EmptyState title="로그인이 필요합니다" actions={<Button label="로그인" href="/login" variant="primary" />} headingLevel={1} />;
  if (state === "not-found") return <EmptyState title="제안을 찾을 수 없습니다" description="존재하지 않거나 현재 계정에서 접근할 수 없는 제안입니다." actions={<Button label="제안 목록으로" href="/proposals" />} headingLevel={1} />;
  if (state === "error" || !proposal) return (
    <EmptyState
      title="제안을 불러오지 못했습니다"
      description={loadError ?? "잠시 후 다시 시도해 주세요."}
      actions={(
        <HStack gap={2} wrap="wrap">
          <Button
            label="다시 시도"
            variant="primary"
            onClick={() => {
              setState("loading");
              setProposal(null);
              setLoadError(null);
              setReloadVersion((value) => value + 1);
            }}
          />
          <Button label="제안 목록으로" href="/proposals" variant="secondary" />
        </HStack>
      )}
      headingLevel={1}
    />
  );

  const student = user?.roles.includes("STUDENT") ?? false;
  const teacher = user?.roles.includes("TEACHER") ?? false;
  const gathering = proposal.workflowStatus === "GATHERING_SUPPORT";
  const statusLabel = statusLabels[proposal.workflowStatus] ?? proposal.workflowStatus;

  return (
    <VStack as="article" gap={8}>
      <VStack gap={4}>
        <Button label="제안 목록으로" href="/proposals" variant="ghost" />
        <HStack gap={2} vAlign="center">
          <StatusDot variant={statusVariants[proposal.workflowStatus] ?? "neutral"} label={statusLabel} />
          <Text type="label">{statusLabel}</Text>
        </HStack>
        <Heading level={1} type="display-2" textWrap="balance">{proposal.title}</Heading>
        <MetadataList orientation="horizontal">
          <MetadataListItem label="작성자">{proposal.authorVisibility === "NAMED" ? proposal.authorDisplayName : "익명"}</MetadataListItem>
          <MetadataListItem label="등록">{dateTimeFormatter.format(new Date(proposal.createdAt))}</MetadataListItem>
        </MetadataList>
        {proposal.viewerCanEdit ? (
          <HStack gap={2} wrap="wrap">
            <Button label="제안 수정" href={`/proposals/${publicId}/edit`} variant="secondary" />
            <Button label="제안 철회" variant="destructive" onClick={() => setWithdrawOpen(true)} />
          </HStack>
        ) : null}
        <Text as="p" type="large" className="pre-wrap wrap-anywhere">{proposal.content}</Text>
      </VStack>

      <Section variant="muted" padding={6} aria-labelledby="support-title">
        <VStack gap={4}>
          <HStack hAlign="between" vAlign="center" gap={4} wrap="wrap">
            <VStack gap={1}>
              <Heading level={2} id="support-title">{gathering ? "정식 안건까지" : "정식 안건"}</Heading>
              <Text as="p" color="secondary" hasTabularNumbers>
                {gathering ? `${proposal.supportCount} / ${proposal.supportThreshold}명 동의` : `승격 당시 ${proposal.formalizedSupportCount ?? proposal.supportThreshold}명 · 현재 ${proposal.supportCount}명 동의`}
              </Text>
            </VStack>
            {student ? proposal.viewerSupported && !gathering ? (
              <Text type="supporting">동의함 · 정식 안건 이후에는 철회할 수 없습니다.</Text>
            ) : (
              <Button label={proposal.viewerSupported ? "동의 철회" : "동의하기"} variant={proposal.viewerSupported ? "secondary" : "primary"} isLoading={actionPending} isDisabled={actionPending} clickAction={changeSupport} />
            ) : null}
          </HStack>
          <ProgressBar value={proposal.supportCount} max={proposal.supportThreshold} label={`${proposal.supportCount}명 동의`} hasValueLabel variant={gathering ? "accent" : "success"} />
        </VStack>
      </Section>
      {error ? <Banner status="error" title="동의 상태를 변경할 수 없습니다" description={error} /> : null}
      {notice ? <Banner status="success" title={notice} /> : null}

      <ProposalComments publicId={publicId} canComment={student} />

      {student ? <ProposalReportForm publicId={publicId} onUnavailable={showUnavailable} onSignedOut={showSignedOut} /> : null}

      <VStack as="section" gap={4} aria-labelledby="responses-title">
        <VStack gap={1}>
          <Heading level={2} id="responses-title">학교 공식 답변과 실행 현황</Heading>
          <Text as="p" color="secondary">학교가 남긴 결정, 사유, 후속 계획을 시간순으로 확인할 수 있습니다.</Text>
        </VStack>
        {proposal.officialResponses.length ? (
          <List density="spacious" hasDividers>
            {proposal.officialResponses.map((response, index) => (
              <ListItem
                key={`${response.resultingStatus}-${response.createdAt}-${index}`}
                label={
                  <VStack gap={2}>
                    <HStack hAlign="between" gap={2} wrap="wrap">
                      <Heading level={3}>{statusLabels[response.resultingStatus ?? ""] ?? response.resultingStatus}</Heading>
                      {response.createdAt ? <Text type="supporting" color="secondary">{dateTimeFormatter.format(new Date(response.createdAt))}</Text> : null}
                    </HStack>
                    <Text as="p" className="pre-wrap">{response.content}</Text>
                    <MetadataList columns="single">
                      <MetadataListItem label="결정·변경 사유">{response.decisionReason}</MetadataListItem>
                      {response.followUpPlan ? <MetadataListItem label="후속 계획">{response.followUpPlan}</MetadataListItem> : null}
                    </MetadataList>
                  </VStack>
                }
              />
            ))}
          </List>
        ) : <EmptyState title={gathering ? "공식 답변 전 단계입니다" : "아직 등록된 공식 답변이 없습니다"} isCompact />}
      </VStack>

      {proposal.viewerCanManage ? (
        <ProposalWorkflowPanel
          publicId={publicId}
          workflowStatus={proposal.workflowStatus}
          onUpdated={refreshProposal}
          onUnavailable={showUnavailable}
          onSignedOut={showSignedOut}
        />
      ) : teacher && !gathering ? <Banner status="info" title="담당 교사만 변경할 수 있습니다" description="내부 지정된 담당 교사만 이 안건의 상태와 공식 답변을 변경할 수 있습니다." /> : null}

      <VStack as="section" gap={3} aria-labelledby="history-title">
        <Heading level={2} id="history-title">진행 이력</Heading>
        <List listStyle="decimal" density="balanced" hasDividers>
          {proposal.statusHistory.map((history) => (
            <ListItem
              key={`${history.toStatus}-${history.createdAt}`}
              label={statusLabels[history.toStatus] ?? history.toStatus}
              description={historyDescription(history)}
            />
          ))}
        </List>
      </VStack>

      <AlertDialog
        isOpen={withdrawOpen}
        onOpenChange={setWithdrawOpen}
        title="제안을 철회할까요?"
        description="학생 공개 목록과 직접 조회에서 사라지지만 감사와 보안 기록은 보존됩니다. 철회한 제안은 다시 공개할 수 없습니다."
        cancelLabel="취소"
        actionLabel="제안 철회"
        isActionLoading={withdrawPending}
        onAction={() => void withdrawProposal()}
      />
    </VStack>
  );
}
