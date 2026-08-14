import { ProposalAssignmentConsole } from "@/components/admin/proposal-assignment-console";
import { StaffAppShell } from "@/components/design-system/staff-app-shell";
import { Button } from "@astryxdesign/core/Button";
import { Heading } from "@astryxdesign/core/Heading";
import { Section } from "@astryxdesign/core/Section";
import { HStack, VStack } from "@astryxdesign/core/Stack";
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
        <Section variant="muted" padding={4}>
          <VStack gap={3} maxWidth="72ch">
            <VStack gap={1}>
              <Heading level={2}>신고된 제안 확인</Heading>
              <Text as="p" color="secondary">
                신고 접수와 보호 심의는 담당 지정 업무와 분리되어 있습니다. 신고 사유와 제안 원문은 현임 학생부장교사 계정에서만 확인할 수 있습니다.
              </Text>
              <Text as="p" type="supporting" color="secondary">
                교사 계정을 준비한 뒤 학생부장교사 보직을 지정하고, 해당 계정으로 로그인해 신고 접수함을 여세요.
              </Text>
            </VStack>
            <HStack gap={2} wrap="wrap">
              <Button label="교사 계정 만들기" href="/admin/accounts/new" variant="secondary" />
              <Button label="학생부장교사 보직 지정" href="/admin/offices" variant="secondary" />
              <Button label="신고 접수함 열기" href="/moderation#reports-title" variant="primary" />
            </HStack>
          </VStack>
        </Section>
        <ProposalAssignmentConsole />
      </VStack>
    </StaffAppShell>
  );
}
