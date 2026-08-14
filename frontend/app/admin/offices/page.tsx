import { AdminConsole } from "@/components/admin/admin-console";
import { StaffAppShell } from "@/components/design-system/staff-app-shell";
import { Heading } from "@astryxdesign/core/Heading";
import { VStack } from "@astryxdesign/core/Stack";
import { Text } from "@astryxdesign/core/Text";
import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "보직 관리 | 학교 소통 제안 시스템",
};

export default function OfficeAdministrationPage() {
  return (
    <StaffAppShell requires="admin">
      <VStack className="page-frame motion-reveal" gap={6} paddingBlock={4}>
        <VStack gap={2} maxWidth="72ch">
          <Heading level={1} type="display-2">보직 관리</Heading>
          <Text as="p" color="secondary">고정 심의자 보직 변경은 충돌 검증과 다른 관리자의 승인을 거쳐 실행됩니다.</Text>
        </VStack>
        <AdminConsole mode="offices" />
      </VStack>
    </StaffAppShell>
  );
}
