import { ModerationConsole } from "@/components/moderation/moderation-console";
import { StaffAppShell } from "@/components/design-system/staff-app-shell";
import { VStack } from "@astryxdesign/core/Stack";
import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "보호 심의 | 학교 소통 제안 시스템",
};

export default function ModerationPage() {
  return (
    <StaffAppShell requires="reviewer">
      <VStack className="page-frame motion-reveal" paddingBlock={4}>
        <ModerationConsole />
      </VStack>
    </StaffAppShell>
  );
}
