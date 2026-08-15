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
    <StaffAppShell requires="admin">
      <VStack className="page-frame motion-reveal" gap={6} paddingBlock={4}>
        <VStack gap={2} maxWidth="72ch">
          <Heading level={1} type="display-2">새 계정 만들기</Heading>
          <Text as="p" color="secondary">새 계정 요청은 다른 관리자의 승인 후 실행되며 가입 코드는 검증된 수신자 채널로만 전달됩니다.</Text>
        </VStack>
        <AdminConsole mode="create" />
      </VStack>
    </StaffAppShell>
  );
}
