"use client";

import type { components } from "@/lib/api-schema";
import { Button } from "@astryxdesign/core/Button";
import { Card } from "@astryxdesign/core/Card";
import { EmptyState } from "@astryxdesign/core/EmptyState";
import { Heading } from "@astryxdesign/core/Heading";
import { ProgressBar } from "@astryxdesign/core/ProgressBar";
import { HStack, VStack } from "@astryxdesign/core/Stack";
import { StatusDot } from "@astryxdesign/core/StatusDot";
import { Text } from "@astryxdesign/core/Text";

type ProposalSummary = components["schemas"]["ProposalSummaryResponse"];

const dateFormatter = new Intl.DateTimeFormat("ko-KR", { timeZone: "Asia/Seoul", dateStyle: "medium" });

const statusLabels: Record<string, string> = {
  GATHERING_SUPPORT: "동의 모집 중", FORMAL_AGENDA: "정식 안건", UNDER_REVIEW: "검토 중", ACCEPTED: "채택",
  ON_HOLD: "보류", REJECTED: "반려", IN_PROGRESS: "실행 중", COMPLETED: "완료",
};
const statusVariants: Record<string, "accent" | "success" | "warning" | "error" | "neutral"> = {
  GATHERING_SUPPORT: "accent", FORMAL_AGENDA: "success", UNDER_REVIEW: "accent", ACCEPTED: "success",
  ON_HOLD: "warning", REJECTED: "error", IN_PROGRESS: "accent", COMPLETED: "success",
};

/** 제안 요약을 카드로 보여준다. 대시보드의 여러 목록이 같은 카드를 쓴다. */
export function ProposalCardList({
  proposals,
  emptyTitle,
  emptyDescription,
}: {
  proposals: ProposalSummary[];
  emptyTitle: string;
  emptyDescription?: string;
}) {
  if (proposals.length === 0) {
    return <EmptyState title={emptyTitle} description={emptyDescription} isCompact />;
  }
  return (
    <HStack gap={4} wrap="wrap" vAlign="stretch">
      {proposals.map((proposal) => (
        <ProposalCard key={proposal.publicId} proposal={proposal} />
      ))}
    </HStack>
  );
}

function ProposalCard({ proposal }: { proposal: ProposalSummary }) {
  const gathering = proposal.workflowStatus === "GATHERING_SUPPORT";
  const statusLabel = statusLabels[proposal.workflowStatus] ?? proposal.workflowStatus;
  const author = proposal.authorVisibility === "NAMED" ? proposal.authorDisplayName : "익명";

  return (
    <Card padding={5} width="min(100%, 26rem)">
      <VStack gap={3} height="100%">
        <HStack gap={1} vAlign="center">
          <StatusDot variant={statusVariants[proposal.workflowStatus] ?? "neutral"} label={statusLabel} />
          <Text type="supporting" weight="medium">{statusLabel}</Text>
        </HStack>
        <Heading level={3} maxLines={2}>{proposal.title}</Heading>
        <Text as="p" color="secondary" maxLines={2}>{proposal.excerpt}</Text>
        <VStack gap={1}>
          <Text type="supporting" weight="medium" hasTabularNumbers>
            {gathering
              ? `${proposal.supportCount} / ${proposal.supportThreshold}명 동의`
              : `현재 ${proposal.supportCount}명 동의`}
          </Text>
          <ProgressBar
            value={proposal.supportCount}
            max={proposal.supportThreshold}
            label={`${proposal.supportCount}명 동의`}
            isLabelHidden
            variant={gathering ? "accent" : "success"}
          />
        </VStack>
        <Text type="supporting" color="secondary">
          {author} · {dateFormatter.format(new Date(proposal.createdAt))}
        </Text>
        <Button label="제안 열기" href={`/proposals/${proposal.publicId}`} variant="secondary" width="100%" />
      </VStack>
    </Card>
  );
}
