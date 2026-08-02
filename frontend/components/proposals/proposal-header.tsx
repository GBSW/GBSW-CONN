import { AuthStatus } from "@/components/auth/auth-status";
import Link from "next/link";

export function ProposalHeader() {
  return (
    <header className="proposal-header">
      <Link className="brand" href="/">학교 소통 제안 시스템</Link>
      <nav aria-label="제안 메뉴">
        <Link href="/proposals">제안 보기</Link>
        <AuthStatus />
      </nav>
    </header>
  );
}
