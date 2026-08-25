"use client";

import type { components } from "@/lib/api-schema";
import { apiGet, apiPost } from "@/lib/api-client";
import { Button } from "@astryxdesign/core/Button";
import { DropdownMenu } from "@astryxdesign/core/DropdownMenu";
import { HStack } from "@astryxdesign/core/Stack";
import { Text } from "@astryxdesign/core/Text";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";

type CurrentUser = components["schemas"]["CurrentUserResponse"];

export function AuthStatus() {
  const router = useRouter();
  const [user, setUser] = useState<CurrentUser | null>(null);
  const [loaded, setLoaded] = useState(false);

  useEffect(() => {
    let active = true;
    apiGet<CurrentUser>("/api/v1/auth/me")
      .then((currentUser) => {
        if (active) setUser(currentUser);
      })
      .catch(() => undefined)
      .finally(() => {
        if (active) setLoaded(true);
      });
    return () => {
      active = false;
    };
  }, []);

  async function logout() {
    try {
      await apiPost<void>("/api/v1/auth/logout");
    } finally {
      setUser(null);
      router.replace("/");
    }
  }

  if (!loaded) {
    return <Text type="supporting">로그인 확인 중</Text>;
  }
  if (user) {
    const reviewer = user.offices.some((office) => [
      "STUDENT_AFFAIRS_TEACHER",
      "STUDENT_COUNCIL_PRESIDENT",
      "STUDENT_COUNCIL_VICE_PRESIDENT",
    ].includes(office));
    const navigate = (href: string) => () => router.push(href);
    const items = [
      { label: user.displayName, isDisabled: true },
      { label: "대시보드", onClick: navigate("/dashboard") },
      ...(user.roles.includes("STUDENT") ? [{ label: "공개 제안 작성", onClick: navigate("/proposals/new") }] : []),
      ...(reviewer ? [{ label: "보호 심의", onClick: navigate("/moderation") }] : []),
      ...(user.roles.includes("SUPER_ADMIN") ? [{ label: "계정 관리", onClick: navigate("/admin") }] : []),
      { label: "로그아웃", onClick: () => void logout() },
    ];
    return <DropdownMenu button={{ label: "계정 메뉴", variant: "ghost", size: "sm" }} items={items} menuWidth={220} />;
  }
  return (
    <HStack gap={1} vAlign="center">
      <Button label="계정 활성화" href="/activate" variant="ghost" size="sm" />
      <Button label="로그인" href="/login" variant="secondary" size="sm" />
    </HStack>
  );
}
