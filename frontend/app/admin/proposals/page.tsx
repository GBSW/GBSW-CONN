import { ProposalAssignmentConsole } from "@/components/admin/proposal-assignment-console";
import type { Metadata } from "next";
import Link from "next/link";

export const metadata: Metadata = {
  title: "정식 안건 담당 지정 | 학교 소통 제안 시스템",
};

export default function AdminProposalAssignmentsPage() {
  return (
    <main className="admin-page">
      <header className="admin-header">
        <div>
          <p className="eyebrow">슈퍼 어드민 · 내부 정보</p>
          <h1>정식 안건 담당 지정</h1>
        </div>
        <nav aria-label="관리 화면">
          <Link href="/admin">계정 관리</Link>
          <Link href="/">서비스 홈</Link>
        </nav>
      </header>
      <ProposalAssignmentConsole />
    </main>
  );
}
