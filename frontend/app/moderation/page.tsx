import { ModerationConsole } from "@/components/moderation/moderation-console";
import { ProposalHeader } from "@/components/proposals/proposal-header";
import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "보호 심의 | 학교 소통 제안 시스템",
};

export default function ModerationPage() {
  return (
    <>
      <ProposalHeader />
      <main className="moderation-page">
        <ModerationConsole />
      </main>
    </>
  );
}
