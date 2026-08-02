import { ProposalForm } from "@/components/proposals/proposal-form";
import { PublicAppShell } from "@/components/design-system/public-app-shell";
import { VStack } from "@astryxdesign/core/Stack";
import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "공개 제안 작성 | 학교 소통 제안 시스템",
};

export default function NewProposalPage() {
  return (
    <PublicAppShell>
      <VStack className="page-frame motion-reveal" maxWidth="48rem" paddingBlock={4}>
        <ProposalForm />
      </VStack>
    </PublicAppShell>
  );
}
