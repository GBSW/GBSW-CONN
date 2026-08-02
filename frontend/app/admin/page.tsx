import { AdminConsole } from "@/components/admin/admin-console";
import { StaffAppShell } from "@/components/design-system/staff-app-shell";
import { Heading } from "@astryxdesign/core/Heading";
import { VStack } from "@astryxdesign/core/Stack";
import { Text } from "@astryxdesign/core/Text";
import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "계정 관리 | 학교 소통 제안 시스템",
};

export default function AdminPage() {
  return (
    <StaffAppShell>
      <VStack className="page-frame motion-reveal" gap={6} paddingBlock={4}>
        <VStack gap={2} maxWidth="72ch">
          <Heading level={1} type="display-2">계정 관리</Heading>
          <Text as="p" color="secondary">기존 계정을 찾고 코드 발급, 상태, 역할 임기를 기능별로 펼쳐 관리합니다.</Text>
        </VStack>
        <AdminConsole mode="accounts" />
      </VStack>
    </StaffAppShell>
  );
}
