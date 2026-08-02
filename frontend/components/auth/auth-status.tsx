"use client";

import type { components } from "@/lib/api-schema";
import { apiGet, apiPost } from "@/lib/api-client";
import Link from "next/link";
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
    return <span className="auth-status-placeholder" aria-label="로그인 상태 확인 중" />;
  }
  if (user) {
    const reviewer = user.offices.some((office) => [
      "STUDENT_AFFAIRS_TEACHER",
      "STUDENT_COUNCIL_PRESIDENT",
      "STUDENT_COUNCIL_VICE_PRESIDENT",
    ].includes(office));
    return (
      <div className="auth-status">
        <span>{user.displayName}</span>
        {user.roles.some((role) => role === "STUDENT" || role === "TEACHER") ? <Link href="/proposals">제안 보기</Link> : null}
        {reviewer ? <Link href="/moderation">보호 심의</Link> : null}
        {user.roles.includes("SUPER_ADMIN") ? <Link href="/admin">계정 관리</Link> : null}
        <button className="link-button" type="button" onClick={() => void logout()}>
          로그아웃
        </button>
      </div>
    );
  }
  return (
    <div className="auth-status">
      <Link href="/activate">계정 활성화</Link>
      <Link href="/login">로그인</Link>
    </div>
  );
}
