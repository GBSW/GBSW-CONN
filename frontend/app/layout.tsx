import type { Metadata } from "next";
import type { ReactNode } from "react";
import "@astryxdesign/core/reset.css";
import "@astryxdesign/core/astryx.css";
import "@astryxdesign/theme-neutral/theme.css";
import { AstryxProvider } from "@/components/design-system/astryx-provider";
import "./globals.css";

export const metadata: Metadata = {
  title: "학교 소통 제안 시스템",
  description: "학생의 제안이 공동체의 동의와 학교의 공식 답변으로 이어지는 소통 공간",
};

export default function RootLayout({ children }: Readonly<{ children: ReactNode }>) {
  return (
    <html lang="ko">
      <body data-design-contract="astryx-neutral-topnav-row-feed-system-mode">
        {/* Design contract: A stryx Neutral, system light/dark, public TopNav, staff SideNav, dense record rows. */}
        <AstryxProvider>{children}</AstryxProvider>
      </body>
    </html>
  );
}
