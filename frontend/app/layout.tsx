import type { Metadata } from "next";
import type { ReactNode } from "react";
import "./globals.css";

export const metadata: Metadata = {
  title: "학교 소통 제안 시스템",
  description: "학생의 제안이 공동체의 동의와 학교의 공식 답변으로 이어지는 소통 공간",
};

export default function RootLayout({ children }: Readonly<{ children: ReactNode }>) {
  return (
    <html lang="ko">
      <body>{children}</body>
    </html>
  );
}
