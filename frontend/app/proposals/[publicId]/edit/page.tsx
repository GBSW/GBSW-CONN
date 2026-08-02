import { ProposalForm } from "@/components/proposals/proposal-form";
import { PublicAppShell } from "@/components/design-system/public-app-shell";
import { VStack } from "@astryxdesign/core/Stack";
import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "제안 수정 | 학교 소통 제안 시스템",
};

export default async function EditProposalPage({ params }: { params: Promise<{ publicId: string }> }) {
  const { publicId } = await params;
  return (
    <PublicAppShell>
      <VStack className="page-frame motion-reveal" maxWidth="48rem" paddingBlock={4}>
        <ProposalForm publicId={publicId} />
      </VStack>
    </PublicAppShell>
  );
}
