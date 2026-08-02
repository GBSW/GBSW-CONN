import { AdminConsole } from "@/components/admin/admin-console";
import { StaffAppShell } from "@/components/design-system/staff-app-shell";
import { Heading } from "@astryxdesign/core/Heading";
import { VStack } from "@astryxdesign/core/Stack";
import { Text } from "@astryxdesign/core/Text";
import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "새 계정 만들기 | 학교 소통 제안 시스템",
};

export default function CreateAccountPage() {
  return (
    <StaffAppShell>
      <VStack className="page-frame motion-reveal" gap={6} paddingBlock={4}>
        <VStack gap={2} maxWidth="72ch">
          <Heading level={1} type="display-2">새 계정 만들기</Heading>
          <Text as="p" color="secondary">새 계정과 첫 역할을 만든 뒤 한 번만 표시되는 가입 코드를 안전하게 전달합니다.</Text>
        </VStack>
        <AdminConsole mode="create" />
      </VStack>
    </StaffAppShell>
  );
}
