import { AdminConsole } from "@/components/admin/admin-console";
import type { Metadata } from "next";
import Link from "next/link";

export const metadata: Metadata = {
  title: "계정 관리 | 학교 소통 제안 시스템",
};

export default function AdminPage() {
  return (
    <main className="admin-page">
      <header className="admin-header">
        <div>
          <p className="eyebrow">슈퍼 어드민</p>
          <h1>계정 관리</h1>
        </div>
        <nav aria-label="관리 화면">
          <Link href="/admin/proposals">정식 안건 담당 지정</Link>
          <Link href="/">서비스 홈</Link>
        </nav>
      </header>
      <AdminConsole />
    </main>
  );
}
