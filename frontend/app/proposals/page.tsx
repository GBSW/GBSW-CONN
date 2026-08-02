import { ProposalFeed } from "@/components/proposals/proposal-feed";
import { PublicAppShell } from "@/components/design-system/public-app-shell";
import { VStack } from "@astryxdesign/core/Stack";
import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "제안 보기 | 학교 소통 제안 시스템",
};

export default function ProposalsPage() {
  return (
    <PublicAppShell contentPadding={0}>
      <VStack className="page-frame motion-reveal" paddingInline={4} paddingBlock={6}>
        <ProposalFeed />
      </VStack>
    </PublicAppShell>
  );
}
