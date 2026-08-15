"use client";

import type { components } from "@/lib/api-schema";
import { apiGet, errorMessage } from "@/lib/api-client";
import { isSignedOutError } from "@/lib/current-user";
import { ProposalCardList } from "@/components/proposals/proposal-card-list";
import { Banner } from "@astryxdesign/core/Banner";
import { Button } from "@astryxdesign/core/Button";
import { Heading } from "@astryxdesign/core/Heading";
import { Spinner } from "@astryxdesign/core/Spinner";
import { HStack, VStack } from "@astryxdesign/core/Stack";
import { Text } from "@astryxdesign/core/Text";
import { useEffect, useState } from "react";

type ProposalPage = components["schemas"]["ProposalPageResponse"];

const cardsPerList = 6;

/**
 * 교사 대시보드의 두 목록.
 *
 * 정식 안건과 아직 임계값에 이르지 못한 검토 대기 제안을 분리해 보여준다.
 * 두 목록 모두 동의수 내림차순이며, 서버가 각각의 범위로 걸러 반환한다.
 */
export function TeacherProposalLists() {
  const [formal, setFormal] = useState<ProposalPage | null>(null);
  const [gathering, setGathering] = useState<ProposalPage | null>(null);
  const [state, setState] = useState<"loading" | "ready" | "error">("loading");
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    const query = (scope: string) =>
      `/api/v1/proposals?scope=${scope}&sort=MOST_SUPPORTED&page=0&size=${cardsPerList}`;
    Promise.all([
      apiGet<ProposalPage>(query("FORMAL_AGENDA")),
      apiGet<ProposalPage>(query("GATHERING_SUPPORT")),
    ])
      .then(([formalPage, gatheringPage]) => {
        if (!active) return;
        setFormal(formalPage);
        setGathering(gatheringPage);
        setState("ready");
      })
      .catch((caught: unknown) => {
        if (!active) return;
        if (!isSignedOutError(caught)) setError(errorMessage(caught));
        setState("error");
      });
    return () => { active = false; };
  }, []);

  if (state === "loading") return <Spinner size="lg" label="제안을 불러오고 있습니다…" />;
  if (state === "error") {
    return <Banner status="error" title="제안을 불러오지 못했습니다" description={error ?? "잠시 후 다시 시도해 주세요."} />;
  }

  // 임계값은 응답 항목에만 실려 온다. 두 목록 모두 비어 있으면 숫자를 말하지 않는다.
  const threshold = [...(formal?.items ?? []), ...(gathering?.items ?? [])][0]?.supportThreshold;

  return (
    <VStack gap={8}>
      <ProposalSection
        title="정식 안건"
        description={threshold === undefined
          ? "학생 동의가 임계값에 도달해 학교가 검토해야 하는 안건입니다."
          : `학생 ${threshold}명 이상의 동의를 받아 학교가 검토해야 하는 안건입니다.`}
        page={formal}
        emptyTitle="정식 안건이 아직 없습니다"
        emptyDescription="동의가 임계값에 도달하면 이 목록에 나타납니다."
        moreHref="/proposals?scope=FORMAL_AGENDA"
      />
      <ProposalSection
        title="검토 대기 제안"
        description={threshold === undefined
          ? "아직 임계값에 이르지 못한 제안입니다. 동의가 많은 순으로 표시합니다."
          : `아직 ${threshold}명에 이르지 못한 제안입니다. 동의가 많은 순으로 표시합니다.`}
        page={gathering}
        emptyTitle="동의를 모으는 중인 제안이 없습니다"
        moreHref="/proposals"
      />
    </VStack>
  );
}

function ProposalSection({
  title,
  description,
  page,
  emptyTitle,
  emptyDescription,
  moreHref,
}: {
  title: string;
  description: string;
  page: ProposalPage | null;
  emptyTitle: string;
  emptyDescription?: string;
  moreHref: string;
}) {
  const items = page?.items ?? [];
  const total = page?.totalElements ?? 0;
  const headingId = `teacher-${title}`;

  return (
    <VStack as="section" gap={4} aria-labelledby={headingId}>
      <HStack hAlign="between" vAlign="end" gap={4} wrap="wrap">
        <VStack gap={1} maxWidth="72ch">
          <Heading level={2} id={headingId}>{title}</Heading>
          <Text as="p" color="secondary">{description}</Text>
        </VStack>
        <Text type="supporting" color="secondary" hasTabularNumbers>{total}건</Text>
      </HStack>
      <ProposalCardList proposals={items} emptyTitle={emptyTitle} emptyDescription={emptyDescription} />
      {total > items.length ? (
        <HStack><Button label="전체 보기" href={moreHref} variant="ghost" /></HStack>
      ) : null}
    </VStack>
  );
}
