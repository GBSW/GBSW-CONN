"use client";

import { AppShell } from "@astryxdesign/core/AppShell";
import { TopNav, TopNavHeading, TopNavItem } from "@astryxdesign/core/TopNav";
import { useMediaQuery } from "@astryxdesign/core/hooks";
import { AuthStatus } from "@/components/auth/auth-status";
import { usePathname } from "next/navigation";
import type { ReactNode } from "react";

export function PublicAppShell({
  children,
  contentPadding = 4,
}: {
  children: ReactNode;
  contentPadding?: 0 | 0.5 | 1 | 1.5 | 2 | 3 | 4 | 5 | 6 | 8 | 10;
}) {
  const pathname = usePathname();
  const isMobile = useMediaQuery("(max-width: 768px)");
  const proposalRoute = pathname.startsWith("/proposals");
  const nav = (
    <TopNav
      label="주요 메뉴"
      heading={<TopNavHeading heading="GBSW 제안" headingHref="/" />}
      startContent={
        <>
          <TopNavItem label="서비스 안내" href="/" isSelected={pathname === "/"} />
          <TopNavItem label="제안 보기" href="/proposals" isSelected={proposalRoute && pathname !== "/proposals/new"} />
          {isMobile ? <AuthStatus /> : null}
        </>
      }
      endContent={
        isMobile ? null : <AuthStatus />
      }
    />
  );

  return (
    <AppShell
      topNav={nav}
      mobileNav={{ breakpoint: "md" }}
      contentPadding={contentPadding}
      height="auto"
      variant="section"
    >
      {children}
    </AppShell>
  );
}
