"use client";

import { InternationalizationProvider } from "@astryxdesign/core/i18n";
import { LinkProvider } from "@astryxdesign/core/Link";
import { Theme } from "@astryxdesign/core/theme";
import { neutralTheme } from "@astryxdesign/theme-neutral/built";
import { astryxKoreanOverrides } from "@/lib/astryx-ko";
import NextLink from "next/link";
import { useEffect, type ReactNode } from "react";

export function AstryxProvider({ children }: { children: ReactNode }) {
  useEffect(() => {
    function localizeRequired(root: Node) {
      if (root.nodeType === Node.TEXT_NODE && root.nodeValue?.trim() === "Required") {
        root.nodeValue = root.nodeValue.replace("Required", "필수");
        return;
      }
      const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT);
      let node = walker.nextNode();
      while (node) {
        if (node.nodeValue?.trim() === "Required") {
          node.nodeValue = node.nodeValue.replace("Required", "필수");
        }
        node = walker.nextNode();
      }
    }

    localizeRequired(document.body);
    const observer = new MutationObserver((mutations) => {
      for (const mutation of mutations) {
        mutation.addedNodes.forEach(localizeRequired);
      }
    });
    observer.observe(document.body, { childList: true, subtree: true });
    return () => observer.disconnect();
  }, []);

  return (
    <Theme theme={neutralTheme} mode="system">
      <InternationalizationProvider locale="ko" dir="ltr" overrides={astryxKoreanOverrides}>
        <LinkProvider component={NextLink}>{children}</LinkProvider>
      </InternationalizationProvider>
    </Theme>
  );
}
