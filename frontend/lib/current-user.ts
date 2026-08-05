"use client";

import type { components } from "@/lib/api-schema";
import { ApiRequestError, apiGet } from "@/lib/api-client";
import { useEffect, useState } from "react";

type CurrentUser = components["schemas"]["CurrentUserResponse"];

export type CurrentUserStatus = "loading" | "ready" | "signed-out" | "error";

export type CurrentUserState = {
  user: CurrentUser | null;
  status: CurrentUserStatus;
};

const signedOutCodes = ["AUTHENTICATION_REQUIRED", "SESSION_INVALIDATED"];

export function isSignedOutError(error: unknown): boolean {
  return error instanceof ApiRequestError && signedOutCodes.includes(error.code);
}

/**
 * 로그인한 사용자를 조회한다. 역할과 보직에 따라 화면을 나누는 컴포넌트가
 * 각자 다른 판단을 내리지 않도록 조회와 상태 구분을 한곳에 둔다.
 */
export function useCurrentUser(): CurrentUserState {
  const [state, setState] = useState<CurrentUserState>({ user: null, status: "loading" });

  useEffect(() => {
    let active = true;
    apiGet<CurrentUser>("/api/v1/auth/me")
      .then((user) => {
        if (active) setState({ user, status: "ready" });
      })
      .catch((error: unknown) => {
        if (active) setState({ user: null, status: isSignedOutError(error) ? "signed-out" : "error" });
      });
    return () => {
      active = false;
    };
  }, []);

  return state;
}
