import type { components } from "@/lib/api-schema";

type ApiErrorBody = components["schemas"]["ErrorResponse"];
type CsrfTokenResponse = components["schemas"]["CsrfTokenResponse"];

export class ApiRequestError extends Error {
  readonly code: string;
  readonly fieldErrors: ApiErrorBody["fieldErrors"];

  constructor(body: ApiErrorBody) {
    super(body.message);
    this.name = "ApiRequestError";
    this.code = body.code;
    this.fieldErrors = body.fieldErrors;
  }
}

async function parseError(response: Response): Promise<ApiRequestError> {
  try {
    const body = (await response.json()) as ApiErrorBody;
    if (typeof body.code === "string" && typeof body.message === "string") {
      return new ApiRequestError(body);
    }
  } catch {
    // The safe fallback below is used when an intermediary returned a non-API response.
  }
  return new ApiRequestError({
    code: "NETWORK_RESPONSE_ERROR",
    message: "요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.",
    timestamp: new Date().toISOString(),
    traceId: "unavailable",
  });
}

export async function apiGet<TResponse>(path: string): Promise<TResponse> {
  const response = await fetch(path, {
    credentials: "same-origin",
    headers: { Accept: "application/json" },
  });
  if (!response.ok) {
    throw await parseError(response);
  }
  return (await response.json()) as TResponse;
}

async function apiWrite<TResponse>(
  method: "POST" | "PUT" | "DELETE",
  path: string,
  body?: unknown,
): Promise<TResponse> {
  const csrf = await apiGet<CsrfTokenResponse>("/api/v1/auth/csrf");
  const response = await fetch(path, {
    method,
    credentials: "same-origin",
    headers: {
      Accept: "application/json",
      "Content-Type": "application/json",
      [csrf.headerName]: csrf.token,
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  if (!response.ok) {
    throw await parseError(response);
  }
  if (response.status === 204) {
    return undefined as TResponse;
  }
  return (await response.json()) as TResponse;
}

export async function apiPost<TResponse>(path: string, body?: unknown): Promise<TResponse> {
  return apiWrite("POST", path, body);
}

export async function apiPut<TResponse>(path: string, body?: unknown): Promise<TResponse> {
  return apiWrite("PUT", path, body);
}

export async function apiDelete<TResponse>(path: string, body?: unknown): Promise<TResponse> {
  return apiWrite("DELETE", path, body);
}

export function errorMessage(error: unknown): string {
  if (error instanceof ApiRequestError) {
    return error.message;
  }
  return "서버에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요.";
}
