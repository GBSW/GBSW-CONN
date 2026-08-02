"use client";

import { InternationalizationProvider } from "@astryxdesign/core/i18n";
import { LinkProvider } from "@astryxdesign/core/Link";
import { Theme } from "@astryxdesign/core/theme";
import { neutralTheme } from "@astryxdesign/theme-neutral/built";
import { astryxKoreanOverrides } from "@/lib/astryx-ko";
import NextLink from "next/link";
import type { ReactNode } from "react";

export function AstryxProvider({ children }: { children: ReactNode }) {
  return (
    <Theme theme={neutralTheme} mode="system">
      <InternationalizationProvider locale="ko" dir="ltr" overrides={astryxKoreanOverrides}>
        <LinkProvider component={NextLink}>{children}</LinkProvider>
      </InternationalizationProvider>
    </Theme>
  );
}
