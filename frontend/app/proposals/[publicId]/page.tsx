import { ProposalDetail } from "@/components/proposals/proposal-detail";
import { ProposalHeader } from "@/components/proposals/proposal-header";
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
    <>
      <ProposalHeader />
      <main className="proposal-page narrow-proposal-page">
        <ProposalDetail publicId={publicId} />
      </main>
    </>
  );
}
