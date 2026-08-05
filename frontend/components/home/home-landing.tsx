"use client";

import { PublicAppShell } from "@/components/design-system/public-app-shell";
import type { components } from "@/lib/api-schema";
import { apiGet } from "@/lib/api-client";
import { Button } from "@astryxdesign/core/Button";
import { Card } from "@astryxdesign/core/Card";
import { Heading } from "@astryxdesign/core/Heading";
import { List, ListItem } from "@astryxdesign/core/List";
import { Section } from "@astryxdesign/core/Section";
import { Spinner } from "@astryxdesign/core/Spinner";
import { HStack, VStack } from "@astryxdesign/core/Stack";
import { Text } from "@astryxdesign/core/Text";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";

type CurrentUser = components["schemas"]["CurrentUserResponse"];

const publicProposalSteps = [
  "학생이 제안을 등록합니다.",
  "다른 학생들이 제안을 읽고 동의합니다.",
  "50명 이상이 동의하면 정식 안건이 됩니다.",
  "학교가 검토 상태와 공식 답변을 남깁니다.",
];

export function HomeLanding() {
  const router = useRouter();
  const [ready, setReady] = useState(false);

  useEffect(() => {
    let active = true;
    apiGet<CurrentUser>("/api/v1/auth/me")
      .then(() => router.replace("/dashboard"))
      .catch(() => {
        if (active) setReady(true);
      });
    return () => { active = false; };
  }, [router]);

  // 안내 내용은 로그인 상태를 확인한 뒤에 그려지므로 라우터가 최초 해시 스크롤을
  // 시도하는 시점에는 대상 섹션이 아직 없다. 준비된 뒤 한 번 더 이동시킨다.
  useEffect(() => {
    if (!ready) return;
    const hash = window.location.hash;
    if (!hash || hash === "#") return;
    document.getElementById(hash.slice(1))?.scrollIntoView();
  }, [ready]);

  if (!ready) {
    return (
      <PublicAppShell>
        <VStack className="page-frame" minHeight="70dvh" vAlign="center" hAlign="center">
          <Spinner size="lg" label="사용자 상태를 확인하고 있습니다…" />
        </VStack>
      </PublicAppShell>
    );
  }

  return (
    <PublicAppShell>
      <VStack className="page-frame motion-reveal" gap={10} paddingBlock={8}>
        <VStack as="section" gap={4} maxWidth="72ch" aria-labelledby="intro-title">
          <Heading level={1} type="display-1" id="intro-title" textWrap="balance">
            학생의 의견이 학교의 답변으로 이어지도록
          </Heading>
          <Text type="large" as="p" color="secondary">
            학교생활을 더 낫게 만들 제안을 함께 살펴보고, 공감하는 제안이 정식 안건이 되도록
            동의할 수 있습니다.
          </Text>
          <HStack gap={2} wrap="wrap">
            <Button label="제안 살펴보기" href="/proposals" variant="primary" size="lg" />
            <Button label="진행 방식 알아보기" href="#process" variant="secondary" size="lg" />
          </HStack>
        </VStack>

        <Card id="process" variant="muted" padding={8} aria-labelledby="process-title">
          <VStack gap={5}>
            <VStack gap={2} maxWidth="72ch">
              <Heading level={2} id="process-title">제안이 정식 안건이 되는 과정</Heading>
              <Text as="p" color="secondary">공개 제안은 학생 공동체의 동의를 거쳐 학교의 검토와 공식 기록으로 이어집니다.</Text>
            </VStack>
            <List listStyle="decimal" density="spacious">
              {publicProposalSteps.map((step) => <ListItem key={step} label={step} />)}
            </List>
          </VStack>
        </Card>

        <VStack as="section" id="paths" gap={5} aria-labelledby="paths-title">
          <VStack gap={2} maxWidth="72ch">
            <Heading level={2} id="paths-title">내용에 맞는 경로를 선택하세요</Heading>
            <Text as="p" color="secondary">공개 토론에 적합한 제안과 보호가 필요한 개인 사안은 분리해서 다룹니다.</Text>
          </VStack>
          <HStack gap={4} wrap="wrap" vAlign="stretch">
            <Card padding={6} width="min(100%, 34rem)">
              <VStack gap={3}>
                <Heading level={3}>학교 개선 제안</Heading>
                <Text as="p" color="secondary">
                  수업, 기숙사, 시설, 학생자치, 정보 공개처럼 학생들이 함께 논의할 내용을
                  제안합니다. 학생들에게 공개되고 동의를 받습니다.
                </Text>
                <Button label="공개 제안 작성" href="/proposals/new" variant="primary" />
              </VStack>
            </Card>
            <Card padding={6} width="min(100%, 34rem)" variant="muted">
              <VStack gap={3}>
                <Heading level={3}>개인 고충·안전 제보</Heading>
                <Text as="p" color="secondary">
                  학교폭력, 자해 위험, 개인정보, 성희롱, 차별처럼 공개 투표로 다루면 안 되는
                  내용은 지정된 담당자에게 별도로 전달합니다.
                </Text>
                <Text type="supporting" as="p">이 서비스에는 비공개 제보 접수 기능이 포함되지 않습니다. 학교가 안내한 보호 채널을 이용하세요.</Text>
              </VStack>
            </Card>
          </HStack>
        </VStack>

        <footer>
          <Section variant="transparent" dividers={["top"]} paddingBlock={4}>
            <Text type="supporting" as="p" color="secondary">서비스 정식 명칭과 자체 로고·브랜드 컬러는 추후 확정됩니다.</Text>
          </Section>
        </footer>
      </VStack>
    </PublicAppShell>
  );
}
