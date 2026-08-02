import { HomeLanding } from "@/components/home/home-landing";
import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "서비스 안내 | 학교 소통 제안 시스템",
};

export default function Home() {
  return <HomeLanding />;
}
