import { ModerationCaseDetail } from "@/components/moderation/moderation-case-detail";
import { StaffAppShell } from "@/components/design-system/staff-app-shell";
import { VStack } from "@astryxdesign/core/Stack";
import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "심의 사건 상세 | 학교 소통 제안 시스템",
};

export default async function ModerationCasePage({
  params,
}: {
  params: Promise<{ publicId: string }>;
}) {
  const { publicId } = await params;
  return (
    <StaffAppShell>
      <VStack className="page-frame motion-reveal" maxWidth="56rem" paddingBlock={4}>
        <ModerationCaseDetail publicId={publicId} />
      </VStack>
    </StaffAppShell>
  );
}
