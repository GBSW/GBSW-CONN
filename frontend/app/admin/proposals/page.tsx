import { ProposalAssignmentConsole } from "@/components/admin/proposal-assignment-console";
import { StaffAppShell } from "@/components/design-system/staff-app-shell";
import { Heading } from "@astryxdesign/core/Heading";
import { VStack } from "@astryxdesign/core/Stack";
import { Text } from "@astryxdesign/core/Text";
import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "정식 안건 담당 지정 | 학교 소통 제안 시스템",
};

export default function AdminProposalAssignmentsPage() {
  return (
    <StaffAppShell requires="admin">
      <VStack className="page-frame motion-reveal" gap={6} paddingBlock={4}>
        <VStack gap={2} maxWidth="72ch">
          <Heading level={1} type="display-2">정식 안건 담당 지정</Heading>
          <Text as="p" color="secondary">학생 화면에 노출되지 않는 내부 담당 교사를 최소한의 정보만 보고 지정합니다.</Text>
        </VStack>
        <ProposalAssignmentConsole />
      </VStack>
    </StaffAppShell>
  );
}
