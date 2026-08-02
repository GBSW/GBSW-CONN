"use client";

import type { components } from "@/lib/api-schema";
import { ApiRequestError, apiGet, apiPost, errorMessage } from "@/lib/api-client";
import Link from "next/link";
import type { FormEvent, ReactNode } from "react";
import { useEffect, useState } from "react";

type CurrentUser = components["schemas"]["CurrentUserResponse"];
type AccountPage = components["schemas"]["AccountPageResponse"];
type AccountDetail = components["schemas"]["AccountDetailResponse"];
type CreateAccountRequest = components["schemas"]["CreateAccountRequest"];
type OneTimeCode = components["schemas"]["OneTimeCodeResponse"];
type ReauthenticationRequest = components["schemas"]["ReauthenticationRequest"];
type RoleAssignmentRequest = components["schemas"]["RoleAssignmentRequest"];
type OfficeAppointmentRequest = components["schemas"]["OfficeAppointmentRequest"];
type EndAssignmentRequest = components["schemas"]["EndAssignmentRequest"];
type AccountStatus = "" | "PENDING_ACTIVATION" | "ACTIVE" | "SUSPENDED" | "DEACTIVATED";
type AccountRole = CreateAccountRequest["role"];
type OfficeType =
  | "STUDENT_AFFAIRS_TEACHER"
  | "STUDENT_COUNCIL_PRESIDENT"
  | "STUDENT_COUNCIL_VICE_PRESIDENT";

const statusLabels: Record<string, string> = {
  PENDING_ACTIVATION: "활성화 대기",
  ACTIVE: "활성",
  SUSPENDED: "정지",
  DEACTIVATED: "비활성",
};

const roleLabels: Record<AccountRole, string> = {
  STUDENT: "학생",
  TEACHER: "교사",
  SUPER_ADMIN: "슈퍼 어드민",
};

const officeLabels: Record<OfficeType, string> = {
  STUDENT_AFFAIRS_TEACHER: "학생생활 담당 교사",
  STUDENT_COUNCIL_PRESIDENT: "학생회장",
  STUDENT_COUNCIL_VICE_PRESIDENT: "학생부회장",
};

const roles = Object.keys(roleLabels) as AccountRole[];
const offices = Object.keys(officeLabels) as OfficeType[];

function formText(form: FormData, name: string): string {
  return String(form.get(name) ?? "").trim();
}

function optionalInstant(form: FormData, name: string): string | undefined {
  const value = formText(form, name);
  return value ? new Date(value).toISOString() : undefined;
}

function formatInstant(value?: string): string {
  if (!value) return "계속";
  return new Intl.DateTimeFormat("ko-KR", {
    timeZone: "Asia/Seoul",
    dateStyle: "medium",
    timeStyle: "short",
  }).format(new Date(value));
}

function periodText(startsAt?: string, endsAt?: string): string {
  return `${formatInstant(startsAt)} – ${formatInstant(endsAt)}`;
}

function Field({ label, htmlFor, children, help }: {
  label: string;
  htmlFor: string;
  children: ReactNode;
  help?: string;
}) {
  return (
    <div className="field">
      <label htmlFor={htmlFor}>{label}</label>
      {children}
      {help ? <p className="field-help">{help}</p> : null}
    </div>
  );
}

export function AdminConsole() {
  const [access, setAccess] = useState<"loading" | "ready" | "signed-out" | "forbidden" | "error">("loading");
  const [currentUser, setCurrentUser] = useState<CurrentUser | null>(null);
  const [inputQuery, setInputQuery] = useState("");
  const [query, setQuery] = useState("");
  const [status, setStatus] = useState<AccountStatus>("");
  const [page, setPage] = useState(0);
  const [accounts, setAccounts] = useState<AccountPage | null>(null);
  const [listLoading, setListLoading] = useState(true);
  const [listError, setListError] = useState<string | null>(null);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [detail, setDetail] = useState<AccountDetail | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [codeNotice, setCodeNotice] = useState<{ title: string; value: OneTimeCode } | null>(null);
  const [showReauthentication, setShowReauthentication] = useState(false);
  const [actionPending, setActionPending] = useState(false);
  const [refreshVersion, setRefreshVersion] = useState(0);

  useEffect(() => {
    let active = true;
    apiGet<CurrentUser>("/api/v1/auth/me")
      .then((user) => {
        if (!active) return;
        setCurrentUser(user);
        setAccess(user.roles.includes("SUPER_ADMIN") ? "ready" : "forbidden");
      })
      .catch((error: unknown) => {
        if (!active) return;
        const signedOut = error instanceof ApiRequestError
          && ["AUTHENTICATION_REQUIRED", "SESSION_INVALIDATED"].includes(error.code);
        setAccess(signedOut ? "signed-out" : "error");
      });
    return () => {
      active = false;
    };
  }, []);

  useEffect(() => {
    if (access !== "ready") return;
    let active = true;
    const parameters = new URLSearchParams({ page: String(page), size: "20" });
    if (query) parameters.set("query", query);
    if (status) parameters.set("status", status);
    apiGet<AccountPage>(`/api/v1/admin/users?${parameters.toString()}`)
      .then((result) => {
        if (active) {
          setAccounts(result);
          setListError(null);
        }
      })
      .catch((error: unknown) => {
        if (!active) return;
        if (error instanceof ApiRequestError
          && ["AUTHENTICATION_REQUIRED", "SESSION_INVALIDATED"].includes(error.code)) {
          setAccess("signed-out");
        } else {
          setListError(errorMessage(error));
        }
      })
      .finally(() => {
        if (active) setListLoading(false);
      });
    return () => {
      active = false;
    };
  }, [access, page, query, refreshVersion, status]);

  useEffect(() => {
    if (access !== "ready" || !selectedId) return;
    let active = true;
    apiGet<AccountDetail>(`/api/v1/admin/users/${selectedId}`)
      .then((result) => {
        if (active) setDetail(result);
      })
      .catch((error: unknown) => {
        if (!active) return;
        if (error instanceof ApiRequestError
          && ["AUTHENTICATION_REQUIRED", "SESSION_INVALIDATED"].includes(error.code)) {
          setAccess("signed-out");
        } else {
          setActionError(errorMessage(error));
        }
      })
      .finally(() => {
        if (active) setDetailLoading(false);
      });
    return () => {
      active = false;
    };
  }, [access, refreshVersion, selectedId]);

  async function execute<T>(
    action: () => Promise<T>,
    successMessage: string,
    onSuccess?: (result: T) => void,
  ) {
    setActionPending(true);
    setActionError(null);
    setNotice(null);
    try {
      const result = await action();
      onSuccess?.(result);
      setNotice(successMessage);
      setListLoading(true);
      if (selectedId) setDetailLoading(true);
      setRefreshVersion((value) => value + 1);
    } catch (error) {
      if (error instanceof ApiRequestError && error.code === "REAUTHENTICATION_REQUIRED") {
        setShowReauthentication(true);
        setActionError("계속하려면 현재 비밀번호로 본인 확인을 다시 해주세요.");
      } else if (error instanceof ApiRequestError && error.code === "SESSION_INVALIDATED") {
        setAccess("signed-out");
      } else {
        setActionError(errorMessage(error));
      }
    } finally {
      setActionPending(false);
    }
  }

  function search(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setListLoading(true);
    setListError(null);
    setPage(0);
    setQuery(inputQuery.trim());
  }

  function reauthenticate(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const formElement = event.currentTarget;
    const form = new FormData(formElement);
    const request: ReauthenticationRequest = { password: formText(form, "password") };
    void execute(
      () => apiPost<CurrentUser>("/api/v1/auth/reauthenticate", request),
      "본인 확인이 완료되었습니다. 중단했던 작업을 다시 실행해 주세요.",
      (user) => {
        setCurrentUser(user);
        setShowReauthentication(false);
        formElement.reset();
      },
    );
  }

  function createAccount(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const formElement = event.currentTarget;
    const form = new FormData(formElement);
    const request: CreateAccountRequest = {
      loginId: formText(form, "loginId"),
      displayName: formText(form, "displayName"),
      role: formText(form, "role") as AccountRole,
      reason: formText(form, "reason"),
    };
    void execute(
      () => apiPost<OneTimeCode>("/api/v1/admin/users", request),
      "계정을 만들었습니다.",
      (result) => {
        setCodeNotice({ title: "가입 코드", value: result });
        if (result.userPublicId) {
          setDetail(null);
          setDetailLoading(true);
          setSelectedId(result.userPublicId);
        }
        formElement.reset();
      },
    );
  }

  function issueCode(kind: "activation" | "password-reset") {
    if (!selectedId) return;
    const activation = kind === "activation";
    void execute(
      () => apiPost<OneTimeCode>(
        `/api/v1/admin/users/${selectedId}/${activation ? "activation-code" : "password-reset-code"}`,
      ),
      activation ? "가입 코드를 재발급했습니다." : "비밀번호 재설정 코드를 발급했습니다.",
      (result) => setCodeNotice({
        title: activation ? "새 가입 코드" : "비밀번호 재설정 코드",
        value: result,
      }),
    );
  }

  function changeStatus(event: FormEvent<HTMLFormElement>, next: "suspensions" | "reactivations") {
    event.preventDefault();
    if (!selectedId) return;
    const formElement = event.currentTarget;
    const form = new FormData(formElement);
    void execute(
      () => apiPost<void>(`/api/v1/admin/users/${selectedId}/${next}`, { reason: formText(form, "reason") }),
      next === "suspensions" ? "계정을 정지했습니다." : "계정을 재활성화했습니다.",
      () => formElement.reset(),
    );
  }

  function assignRole(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!selectedId) return;
    const formElement = event.currentTarget;
    const form = new FormData(formElement);
    const request: RoleAssignmentRequest = {
      role: formText(form, "role") as AccountRole,
      startsAt: optionalInstant(form, "startsAt"),
      endsAt: optionalInstant(form, "endsAt"),
      reason: formText(form, "reason"),
    };
    void execute(
      () => apiPost(`/api/v1/admin/users/${selectedId}/roles`, request),
      "역할 임기를 추가했습니다.",
      () => formElement.reset(),
    );
  }

  function endRole(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!selectedId) return;
    const formElement = event.currentTarget;
    const form = new FormData(formElement);
    const role = formText(form, "role") as AccountRole;
    const request: EndAssignmentRequest = {
      endsAt: optionalInstant(form, "endsAt"),
      reason: formText(form, "reason"),
    };
    void execute(
      () => apiPost(`/api/v1/admin/users/${selectedId}/roles/${role}/end`, request),
      "역할 임기를 종료했습니다.",
      () => formElement.reset(),
    );
  }

  function appointOffice(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!selectedId) return;
    const formElement = event.currentTarget;
    const form = new FormData(formElement);
    const office = formText(form, "office") as OfficeType;
    const request: OfficeAppointmentRequest = {
      userPublicId: selectedId,
      startsAt: optionalInstant(form, "startsAt"),
      endsAt: optionalInstant(form, "endsAt"),
      replaceExistingAtStart: form.get("replaceExistingAtStart") === "on",
      reason: formText(form, "reason"),
    };
    void execute(
      () => apiPost(`/api/v1/admin/offices/${office}/appointments`, request),
      "보직 임기를 추가했습니다.",
      () => formElement.reset(),
    );
  }

  function endOffice(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!selectedId) return;
    const formElement = event.currentTarget;
    const form = new FormData(formElement);
    const office = formText(form, "office") as OfficeType;
    const request: EndAssignmentRequest = {
      endsAt: optionalInstant(form, "endsAt"),
      reason: formText(form, "reason"),
    };
    void execute(
      () => apiPost(`/api/v1/admin/offices/${office}/users/${selectedId}/end`, request),
      "보직 임기를 종료했습니다.",
      () => formElement.reset(),
    );
  }

  if (access === "loading") {
    return <p className="admin-state" role="status">관리 권한을 확인하고 있습니다…</p>;
  }
  if (access === "signed-out") {
    return (
      <section className="admin-state">
        <h2>로그인이 필요합니다</h2>
        <p>계정 관리 화면은 슈퍼 어드민만 사용할 수 있습니다.</p>
        <Link className="primary-link" href="/login">로그인</Link>
      </section>
    );
  }
  if (access === "forbidden") {
    return (
      <section className="admin-state">
        <h2>접근 권한이 없습니다</h2>
        <p>현재 계정에는 슈퍼 어드민 역할이 없습니다.</p>
        <Link href="/">서비스 홈으로 돌아가기</Link>
      </section>
    );
  }
  if (access === "error") {
    return <p className="form-error" role="alert">관리 권한을 확인하지 못했습니다. 잠시 후 다시 시도해 주세요.</p>;
  }

  return (
    <div className="admin-console">
      <section className="admin-session" aria-labelledby="session-title">
        <div>
          <h2 id="session-title">{currentUser?.displayName}</h2>
          <p>
            최근 본인 확인 유효 시각: {formatInstant(currentUser?.reauthenticationExpiresAt)}
          </p>
        </div>
        <button className="secondary-button" type="button" onClick={() => setShowReauthentication((value) => !value)}>
          비밀번호 다시 확인
        </button>
      </section>

      {showReauthentication ? (
        <form className="inline-form reauthentication" onSubmit={reauthenticate}>
          <Field label="현재 비밀번호" htmlFor="reauth-password">
            <input id="reauth-password" name="password" type="password" autoComplete="current-password" required />
          </Field>
          <button className="primary-button" type="submit" disabled={actionPending}>본인 확인</button>
        </form>
      ) : null}

      {actionError ? <p className="form-error" role="alert">{actionError}</p> : null}
      {notice ? <p className="form-notice" role="status">{notice}</p> : null}
      {codeNotice ? (
        <section className="one-time-code" aria-labelledby="code-title">
          <div>
            <p className="eyebrow">한 번만 표시됩니다</p>
            <h2 id="code-title">{codeNotice.title}</h2>
            <code>{codeNotice.value.code}</code>
            <p>만료: {formatInstant(codeNotice.value.expiresAt)}</p>
            <p>안전한 경로로 당사자에게 전달한 뒤 이 화면에서 지워 주세요.</p>
          </div>
          <button className="secondary-button" type="button" onClick={() => setCodeNotice(null)}>코드 지우기</button>
        </section>
      ) : null}

      <section className="admin-section" aria-labelledby="create-title">
        <div className="admin-section-heading">
          <p className="section-index">01</p>
          <h2 id="create-title">새 계정 만들기</h2>
          <p>활성화 대기 계정과 첫 역할을 만들고 가입 코드를 한 번 발급합니다.</p>
        </div>
        <form className="admin-form" onSubmit={createAccount}>
          <Field label="로그인 ID" htmlFor="create-login-id">
            <input id="create-login-id" name="loginId" pattern="[A-Za-z0-9._-]{3,100}" required />
          </Field>
          <Field label="표시 이름" htmlFor="create-display-name">
            <input id="create-display-name" name="displayName" maxLength={100} required />
          </Field>
          <Field label="첫 역할" htmlFor="create-role">
            <select id="create-role" name="role" defaultValue="STUDENT">
              {roles.map((role) => <option value={role} key={role}>{roleLabels[role]}</option>)}
            </select>
          </Field>
          <Field label="발급 사유" htmlFor="create-reason">
            <textarea id="create-reason" name="reason" maxLength={500} required />
          </Field>
          <button className="primary-button" type="submit" disabled={actionPending}>계정 만들기</button>
        </form>
      </section>

      <section className="admin-section" aria-labelledby="accounts-title">
        <div className="admin-section-heading">
          <p className="section-index">02</p>
          <h2 id="accounts-title">계정 찾기</h2>
          <p>로그인 ID나 표시 이름으로 검색하고 관리할 계정을 선택합니다.</p>
        </div>
        <div>
          <form className="search-form" onSubmit={search}>
            <Field label="계정 검색" htmlFor="account-query">
              <input
                id="account-query"
                value={inputQuery}
                onChange={(event) => setInputQuery(event.target.value)}
                maxLength={100}
                placeholder="로그인 ID 또는 이름"
              />
            </Field>
            <Field label="상태" htmlFor="account-status">
              <select
                id="account-status"
                value={status}
                onChange={(event) => {
                  setListLoading(true);
                  setStatus(event.target.value as AccountStatus);
                  setPage(0);
                }}
              >
                <option value="">전체</option>
                {Object.entries(statusLabels).map(([value, label]) => <option value={value} key={value}>{label}</option>)}
              </select>
            </Field>
            <button className="secondary-button" type="submit">검색</button>
          </form>
          {listError ? <p className="form-error" role="alert">{listError}</p> : null}
          <div className="account-list" aria-busy={listLoading}>
            {accounts?.items.length ? accounts.items.map((account) => (
              <button
                className={`account-row${selectedId === account.publicId ? " selected" : ""}`}
                type="button"
                key={account.publicId}
                onClick={() => {
                  setDetail(null);
                  setDetailLoading(true);
                  setSelectedId(account.publicId);
                  setActionError(null);
                  setNotice(null);
                }}
              >
                <span><strong>{account.displayName}</strong><small>{account.loginId}</small></span>
                <span>{account.currentRoles.map((role) => roleLabels[role as AccountRole] ?? role).join(", ") || "역할 없음"}</span>
                <span>{statusLabels[account.status] ?? account.status}</span>
              </button>
            )) : <p className="empty-state">{listLoading ? "불러오는 중…" : "조건에 맞는 계정이 없습니다."}</p>}
          </div>
          <div className="pagination" aria-label="계정 목록 페이지">
            <button className="secondary-button" type="button" disabled={page === 0 || listLoading} onClick={() => {
              setListLoading(true);
              setPage((value) => value - 1);
            }}>이전</button>
            <span>{(accounts?.totalPages ?? 0) === 0 ? "0 / 0" : `${page + 1} / ${accounts?.totalPages}`}</span>
            <button
              className="secondary-button"
              type="button"
              disabled={listLoading || !accounts || page + 1 >= accounts.totalPages}
              onClick={() => {
                setListLoading(true);
                setPage((value) => value + 1);
              }}
            >다음</button>
          </div>
        </div>
      </section>

      {selectedId ? (
        <section className="admin-section selected-account" aria-labelledby="selected-title">
          <div className="admin-section-heading">
            <p className="section-index">03</p>
            <h2 id="selected-title">선택 계정 관리</h2>
            {detail ? (
              <p><strong>{detail.displayName}</strong><br />{detail.loginId}<br />{statusLabels[detail.status] ?? detail.status}</p>
            ) : <p>{detailLoading ? "계정 정보를 불러오는 중…" : "계정 정보를 불러오지 못했습니다."}</p>}
          </div>
          {detail ? (
            <div className="account-actions">
              <section className="action-group" aria-labelledby="codes-title">
                <h3 id="codes-title">계정 코드와 상태</h3>
                <div className="button-row">
                  {detail.status === "PENDING_ACTIVATION" ? (
                    <button className="secondary-button" type="button" disabled={actionPending} onClick={() => issueCode("activation")}>가입 코드 재발급</button>
                  ) : null}
                  {detail.status === "ACTIVE" ? (
                    <button className="secondary-button" type="button" disabled={actionPending} onClick={() => issueCode("password-reset")}>재설정 코드 발급</button>
                  ) : null}
                </div>
                {detail.status === "ACTIVE" || detail.status === "SUSPENDED" ? (
                  <form className="compact-form" onSubmit={(event) => changeStatus(event, detail.status === "ACTIVE" ? "suspensions" : "reactivations")}>
                    <Field label={detail.status === "ACTIVE" ? "정지 사유" : "재활성화 사유"} htmlFor="status-reason">
                      <textarea id="status-reason" name="reason" maxLength={500} required />
                    </Field>
                    <button className={detail.status === "ACTIVE" ? "danger-button" : "primary-button"} type="submit" disabled={actionPending}>
                      {detail.status === "ACTIVE" ? "계정 정지" : "계정 재활성화"}
                    </button>
                  </form>
                ) : null}
              </section>

              <section className="action-group" aria-labelledby="roles-title">
                <h3 id="roles-title">역할 임기</h3>
                <form className="compact-form" onSubmit={assignRole}>
                  <Field label="추가할 역할" htmlFor="assign-role">
                    <select id="assign-role" name="role">{roles.map((role) => <option value={role} key={role}>{roleLabels[role]}</option>)}</select>
                  </Field>
                  <div className="date-fields">
                    <Field label="시작 시각(선택)" htmlFor="role-start"><input id="role-start" name="startsAt" type="datetime-local" /></Field>
                    <Field label="종료 시각(선택)" htmlFor="role-end"><input id="role-end" name="endsAt" type="datetime-local" /></Field>
                  </div>
                  <Field label="추가 사유" htmlFor="role-reason"><textarea id="role-reason" name="reason" maxLength={500} required /></Field>
                  <button className="primary-button" type="submit" disabled={actionPending}>역할 추가</button>
                </form>
                <form className="compact-form end-form" onSubmit={endRole}>
                  <Field label="종료할 역할" htmlFor="end-role"><select id="end-role" name="role">{roles.map((role) => <option value={role} key={role}>{roleLabels[role]}</option>)}</select></Field>
                  <Field label="종료 시각(선택)" htmlFor="end-role-at"><input id="end-role-at" name="endsAt" type="datetime-local" /></Field>
                  <Field label="종료 사유" htmlFor="end-role-reason"><textarea id="end-role-reason" name="reason" maxLength={500} required /></Field>
                  <button className="danger-button" type="submit" disabled={actionPending}>역할 종료</button>
                </form>
                <HistoryList items={detail.roles.map((item) => ({
                  key: `${item.role}-${item.startsAt}`,
                  title: roleLabels[item.role as AccountRole] ?? item.role ?? "역할",
                  period: periodText(item.startsAt, item.endsAt),
                  reason: item.endReason ?? item.reason,
                }))} />
              </section>

              <section className="action-group" aria-labelledby="offices-title">
                <h3 id="offices-title">보직 임기</h3>
                <p className="field-help">학생회 보직은 학생 역할, 학생생활 담당은 교사 역할의 전체 임기가 먼저 필요합니다.</p>
                <form className="compact-form" onSubmit={appointOffice}>
                  <Field label="추가할 보직" htmlFor="appoint-office"><select id="appoint-office" name="office">{offices.map((office) => <option value={office} key={office}>{officeLabels[office]}</option>)}</select></Field>
                  <div className="date-fields">
                    <Field label="시작 시각(선택)" htmlFor="office-start"><input id="office-start" name="startsAt" type="datetime-local" /></Field>
                    <Field label="종료 시각(선택)" htmlFor="office-end"><input id="office-end" name="endsAt" type="datetime-local" /></Field>
                  </div>
                  <label className="checkbox-field"><input name="replaceExistingAtStart" type="checkbox" /> 시작 시점의 현임자 임기를 종료하고 교체</label>
                  <Field label="임명 사유" htmlFor="office-reason"><textarea id="office-reason" name="reason" maxLength={500} required /></Field>
                  <button className="primary-button" type="submit" disabled={actionPending}>보직 추가</button>
                </form>
                <form className="compact-form end-form" onSubmit={endOffice}>
                  <Field label="종료할 보직" htmlFor="end-office"><select id="end-office" name="office">{offices.map((office) => <option value={office} key={office}>{officeLabels[office]}</option>)}</select></Field>
                  <Field label="종료 시각(선택)" htmlFor="end-office-at"><input id="end-office-at" name="endsAt" type="datetime-local" /></Field>
                  <Field label="종료 사유" htmlFor="end-office-reason"><textarea id="end-office-reason" name="reason" maxLength={500} required /></Field>
                  <button className="danger-button" type="submit" disabled={actionPending}>보직 종료</button>
                </form>
                <HistoryList items={detail.offices.map((item) => ({
                  key: `${item.office}-${item.startsAt}`,
                  title: officeLabels[item.office as OfficeType] ?? item.office ?? "보직",
                  period: periodText(item.startsAt, item.endsAt),
                  reason: item.endReason ?? item.reason,
                }))} />
              </section>
            </div>
          ) : null}
        </section>
      ) : null}
    </div>
  );
}

function HistoryList({ items }: { items: Array<{ key: string; title: string; period: string; reason?: string }> }) {
  if (!items.length) return <p className="empty-state">저장된 임기 이력이 없습니다.</p>;
  return (
    <ul className="history-list">
      {items.map((item) => (
        <li key={item.key}>
          <strong>{item.title}</strong>
          <span>{item.period}</span>
          {item.reason ? <small>{item.reason}</small> : null}
        </li>
      ))}
    </ul>
  );
}
