import { DashboardHome } from "@/components/dashboard/dashboard-home";
import { PublicAppShell } from "@/components/design-system/public-app-shell";
import { VStack } from "@astryxdesign/core/Stack";
import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "대시보드 | 학교 소통 제안 시스템",
};

export default function DashboardPage() {
  return (
    <PublicAppShell>
      <VStack className="page-frame motion-reveal" paddingBlock={8}>
        <DashboardHome />
      </VStack>
    </PublicAppShell>
  );
}
