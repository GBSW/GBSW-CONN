import { ProposalFeed } from "@/components/proposals/proposal-feed";
import { ProposalHeader } from "@/components/proposals/proposal-header";
import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "제안 보기 | 학교 소통 제안 시스템",
};

export default function ProposalsPage() {
  return (
    <>
      <ProposalHeader />
      <main className="proposal-page">
        <ProposalFeed />
      </main>
    </>
  );
}
