import { ProposalForm } from "@/components/proposals/proposal-form";
import { ProposalHeader } from "@/components/proposals/proposal-header";
import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "공개 제안 작성 | 학교 소통 제안 시스템",
};

export default function NewProposalPage() {
  return (
    <>
      <ProposalHeader />
      <main className="proposal-page narrow-proposal-page">
        <ProposalForm />
      </main>
    </>
  );
}
