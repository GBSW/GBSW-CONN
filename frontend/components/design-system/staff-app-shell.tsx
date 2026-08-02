"use client";

import { AppShell } from "@astryxdesign/core/AppShell";
import { Button } from "@astryxdesign/core/Button";
import { SideNav, SideNavHeading, SideNavItem, SideNavSection } from "@astryxdesign/core/SideNav";
import { TopNav, TopNavHeading } from "@astryxdesign/core/TopNav";
import { useMediaQuery } from "@astryxdesign/core/hooks";
import { AuthStatus } from "@/components/auth/auth-status";
import { usePathname } from "next/navigation";
import type { ReactNode } from "react";

export function StaffAppShell({ children }: { children: ReactNode }) {
  const pathname = usePathname();
  const isMobile = useMediaQuery("(max-width: 768px)");
  const sideNav = (
    <SideNav
      header={<SideNavHeading heading="업무 도구" headingHref="/" subheading="GBSW 제안" />}
      collapsible={{ buttonLabel: "사이드 메뉴 접기" }}
      resizable={{ defaultWidth: 240, minWidth: 200, maxWidth: 360, autoSaveId: "gbsw-staff-nav" }}
      footer={<Button label="서비스 홈으로" href="/" variant="ghost" width="100%" />}
    >
      <SideNavSection title="관리">
        <SideNavItem label="계정 관리" href="/admin" isSelected={pathname === "/admin"} />
        <SideNavItem label="새 계정 만들기" href="/admin/accounts/new" isSelected={pathname === "/admin/accounts/new"} />
        <SideNavItem label="보직 관리" href="/admin/offices" isSelected={pathname === "/admin/offices"} />
        <SideNavItem label="정식 안건 담당 지정" href="/admin/proposals" isSelected={pathname === "/admin/proposals"} />
      </SideNavSection>
      <SideNavSection title="보호 심의">
        <SideNavItem label="심의 사건" href="/moderation" isSelected={pathname.startsWith("/moderation")} />
      </SideNavSection>
    </SideNav>
  );

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
      {children}
    </AppShell>
  );
}
