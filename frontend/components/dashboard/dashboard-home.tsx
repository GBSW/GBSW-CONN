"use client";

import { Banner } from "@astryxdesign/core/Banner";
import { Button } from "@astryxdesign/core/Button";
import { EmptyState } from "@astryxdesign/core/EmptyState";
import { Heading } from "@astryxdesign/core/Heading";
import { List, ListItem } from "@astryxdesign/core/List";
import { Spinner } from "@astryxdesign/core/Spinner";
import { HStack, VStack } from "@astryxdesign/core/Stack";
import { Text } from "@astryxdesign/core/Text";
import { useCurrentUser } from "@/lib/current-user";
import { isReviewer, isStudent, isSuperAdmin, isTeacher } from "@/lib/roles";
import { useRouter } from "next/navigation";
import { useEffect } from "react";

export function DashboardHome() {
  const router = useRouter();
  const { user, status } = useCurrentUser();

  const student = isStudent(user);
  const teacher = isTeacher(user);
  const admin = isSuperAdmin(user);
  const reviewer = isReviewer(user);

  // 슈퍼 어드민에게는 별도 대시보드를 두지 않는다. 다만 학생이나 교사 역할을
  // 함께 가진 계정은 본인 활동을 확인해야 하므로 공통 대시보드에 남긴다.
  const adminOnly = admin && !student && !teacher;

  useEffect(() => {
    if (status === "ready" && adminOnly) router.replace("/admin");
  }, [adminOnly, router, status]);

  if (status === "loading") return <Spinner size="lg" label="대시보드를 불러오고 있습니다…" />;
  if (status === "signed-out") {
    return <EmptyState title="로그인이 필요합니다" actions={<Button label="로그인" href="/login" variant="primary" />} headingLevel={1} />;
  }
  if (status === "error" || !user) {
    return <Banner status="error" title="대시보드를 불러오지 못했습니다" description="잠시 후 다시 시도해 주세요." />;
  }
  if (adminOnly) return <Spinner size="lg" label="관리 화면으로 이동하고 있습니다…" />;

  return (
    <VStack gap={8}>
      <VStack gap={3} maxWidth="72ch">
        <Heading level={1} type="display-2">{user.displayName}님의 대시보드</Heading>
        <Text as="p" color="secondary">현재 역할에서 필요한 제안과 업무 화면으로 이동할 수 있습니다.</Text>
        <HStack gap={2} wrap="wrap">
          <Button label="제안 보기" href="/proposals" variant="primary" />
          {student ? <Button label="새 제안 작성" href="/proposals/new" variant="secondary" /> : null}
        </HStack>
      </VStack>

      <VStack as="section" gap={3} aria-labelledby="dashboard-actions-title">
        <Heading level={2} id="dashboard-actions-title">내 업무</Heading>
        <List hasDividers density="spacious">
          {student ? <ListItem href="/proposals" label="학생 제안 살펴보기" description="공개 제안에 동의하고 진행 상태와 학교의 답변을 확인합니다." /> : null}
          {teacher ? <ListItem href="/proposals" label="정식 안건 검토하기" description="50명 이상의 동의를 받은 정식 안건과 공식 기록을 확인합니다." /> : null}
          {reviewer ? <ListItem href="/moderation" label="보호 심의" description="지정된 심의 사건과 아직 필요한 의결을 확인합니다." /> : null}
          {admin ? <ListItem href="/admin" label="관리자 업무" description="계정, 보직, 정식 안건 담당 지정을 각각 관리합니다." /> : null}
        </List>
      </VStack>
    </VStack>
  );
}
