import { ProposalDetail } from "@/components/proposals/proposal-detail";
import { PublicAppShell } from "@/components/design-system/public-app-shell";
import { VStack } from "@astryxdesign/core/Stack";
import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "제안 상세 | 학교 소통 제안 시스템",
};

export default async function ProposalDetailPage({
  params,
}: {
  params: Promise<{ publicId: string }>;
}) {
  const { publicId } = await params;
  return (
    <PublicAppShell>
      <VStack className="page-frame motion-reveal" maxWidth="56rem" paddingBlock={4}>
        <ProposalDetail publicId={publicId} />
      </VStack>
    </PublicAppShell>
  );
}
