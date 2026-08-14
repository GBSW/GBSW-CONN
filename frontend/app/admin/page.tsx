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
    <StaffAppShell requires="admin">
      <VStack className="page-frame motion-reveal" gap={6} paddingBlock={4}>
        <VStack gap={2} maxWidth="72ch">
          <Heading level={1} type="display-2">계정 관리</Heading>
          <Text as="p" color="secondary">민감한 계정·역할 변경은 2인 승인으로 실행되며 자격증명 원문은 관리자에게 표시되지 않습니다.</Text>
        </VStack>
        <AdminConsole mode="accounts" />
      </VStack>
    </StaffAppShell>
  );
}
