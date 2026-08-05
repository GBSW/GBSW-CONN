"use client";

import { AppShell } from "@astryxdesign/core/AppShell";
import { Banner } from "@astryxdesign/core/Banner";
import { Button } from "@astryxdesign/core/Button";
import { EmptyState } from "@astryxdesign/core/EmptyState";
import { SideNav, SideNavHeading, SideNavItem, SideNavSection } from "@astryxdesign/core/SideNav";
import { Spinner } from "@astryxdesign/core/Spinner";
import { HStack, VStack } from "@astryxdesign/core/Stack";
import { TopNav, TopNavHeading } from "@astryxdesign/core/TopNav";
import { useMediaQuery } from "@astryxdesign/core/hooks";
import { AuthStatus } from "@/components/auth/auth-status";
import { useCurrentUser } from "@/lib/current-user";
import { isReviewer, isSuperAdmin } from "@/lib/roles";
import { usePathname } from "next/navigation";
import type { ReactNode } from "react";

/** 업무 화면에 들어갈 수 있는 자격. 화면마다 필요한 쪽을 지정한다. */
export type StaffAccess = "admin" | "reviewer";

export function StaffAppShell({ children, requires }: { children: ReactNode; requires: StaffAccess }) {
  const pathname = usePathname();
  const isMobile = useMediaQuery("(max-width: 768px)");
  const { user, status } = useCurrentUser();

  // 권한을 확인하기 전에는 메뉴도 본문도 렌더하지 않는다. 메뉴 이름과 화면 설명
  // 자체가 내부 업무 구조를 알려주므로 데이터만 가리는 것으로는 충분하지 않다.
  const resolved = status === "ready";
  const admin = resolved && isSuperAdmin(user);
  const reviewer = resolved && isReviewer(user);
  const allowed = requires === "admin" ? admin : reviewer;

  const sideNav = admin || reviewer ? (
    <SideNav
      header={<SideNavHeading heading="업무 도구" headingHref="/" subheading="GBSW 제안" />}
      collapsible={{ buttonLabel: "사이드 메뉴 접기" }}
      resizable={{ defaultWidth: 240, minWidth: 200, maxWidth: 360, autoSaveId: "gbsw-staff-nav" }}
      footer={<Button label="서비스 홈으로" href="/" variant="ghost" width="100%" />}
    >
      {admin ? (
        <SideNavSection title="관리">
          <SideNavItem label="계정 관리" href="/admin" isSelected={pathname === "/admin"} />
          <SideNavItem label="새 계정 만들기" href="/admin/accounts/new" isSelected={pathname === "/admin/accounts/new"} />
          <SideNavItem label="보직 관리" href="/admin/offices" isSelected={pathname === "/admin/offices"} />
          <SideNavItem label="정식 안건 담당 지정" href="/admin/proposals" isSelected={pathname === "/admin/proposals"} />
        </SideNavSection>
      ) : null}
      {reviewer ? (
        <SideNavSection title="보호 심의">
          <SideNavItem label="심의 사건" href="/moderation" isSelected={pathname.startsWith("/moderation")} />
        </SideNavSection>
      ) : null}
    </SideNav>
  ) : undefined;

  return (
    <AppShell
      topNav={
        <TopNav
          label="업무 화면 메뉴"
          heading={<TopNavHeading heading="GBSW 제안" headingHref="/" />}
          startContent={isMobile ? <AuthStatus /> : null}
          endContent={isMobile ? null : <AuthStatus />}
        />
      }
      sideNav={sideNav}
      mobileNav={{ breakpoint: "md" }}
      contentPadding={4}
      height="fill"
      variant="section"
    >
      {allowed ? children : <StaffAccessNotice status={status} requires={requires} />}
    </AppShell>
  );
}

function StaffAccessNotice({ status, requires }: { status: string; requires: StaffAccess }) {
  const homeButton = <Button label="서비스 홈" href="/" variant="secondary" />;

  if (status === "loading") {
    return (
      <VStack className="page-frame" paddingBlock={8}>
        <Spinner size="lg" label="접근 권한을 확인하고 있습니다…" />
      </VStack>
    );
  }
  if (status === "error") {
    return (
      <VStack className="page-frame" paddingBlock={8}>
        <Banner status="error" title="접근 권한을 확인하지 못했습니다" description="잠시 후 다시 시도해 주세요." />
      </VStack>
    );
  }
  if (status === "signed-out") {
    return (
      <VStack className="page-frame" paddingBlock={8}>
        <EmptyState
          title="로그인이 필요합니다"
          description="학교에서 발급받은 업무 계정으로 로그인해 주세요."
          headingLevel={1}
          actions={
            <HStack gap={2} wrap="wrap">
              <Button label="로그인" href="/login" variant="primary" />
              {homeButton}
            </HStack>
          }
        />
      </VStack>
    );
  }
  return (
    <VStack className="page-frame" paddingBlock={8}>
      <EmptyState
        title="접근 권한이 없습니다"
        description={
          requires === "admin"
            ? "이 화면은 슈퍼 어드민 계정만 사용할 수 있습니다."
            : "이 화면은 학생부장교사·학생회장·학생부회장 보직만 사용할 수 있습니다."
        }
        headingLevel={1}
        actions={
          <HStack gap={2} wrap="wrap">
            <Button label="내 대시보드" href="/dashboard" variant="primary" />
            {homeButton}
          </HStack>
        }
      />
    </VStack>
  );
}
