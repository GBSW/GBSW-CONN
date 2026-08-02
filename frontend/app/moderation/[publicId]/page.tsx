import { ModerationCaseDetail } from "@/components/moderation/moderation-case-detail";
import { ProposalHeader } from "@/components/proposals/proposal-header";
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
    <>
      <ProposalHeader />
      <main className="moderation-page narrow-moderation-page">
        <ModerationCaseDetail publicId={publicId} />
      </main>
    </>
  );
}
