"use client";

import type { components } from "@/lib/api-schema";
import { apiGet, apiPost } from "@/lib/api-client";
import { Button } from "@astryxdesign/core/Button";
import { HStack } from "@astryxdesign/core/Stack";
import { Text } from "@astryxdesign/core/Text";
import { useEffect, useState } from "react";

type CurrentUser = components["schemas"]["CurrentUserResponse"];

export function AuthStatus() {
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
      window.location.assign("/");
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
    return (
      <HStack gap={1} vAlign="center" wrap="wrap">
        <Text type="supporting" weight="medium">{user.displayName}</Text>
        <Button label="대시보드" href="/dashboard" variant="ghost" size="sm" />
        {user.roles.includes("STUDENT") ? <Button label="공개 제안 작성" href="/proposals/new" variant="primary" size="sm" /> : null}
        {reviewer ? <Button label="보호 심의" href="/moderation" variant="ghost" size="sm" /> : null}
        {user.roles.includes("SUPER_ADMIN") ? <Button label="계정 관리" href="/admin" variant="ghost" size="sm" /> : null}
        <Button label="로그아웃" variant="ghost" size="sm" clickAction={logout} />
      </HStack>
    );
  }
  return (
    <HStack gap={1} vAlign="center">
      <Button label="계정 활성화" href="/activate" variant="ghost" size="sm" />
      <Button label="로그인" href="/login" variant="secondary" size="sm" />
    </HStack>
  );
}
