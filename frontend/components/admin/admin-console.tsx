"use client";

import type { components } from "@/lib/api-schema";
import { ApiRequestError, apiGet, apiPost, errorMessage } from "@/lib/api-client";
import type { AccountRole, OfficeType } from "@/lib/roles";
import { officeLabels, offices, roleLabels, roles } from "@/lib/roles";
import { GovernanceRequestConsole } from "@/components/admin/governance-request-console";
import { Banner } from "@astryxdesign/core/Banner";
import { Button } from "@astryxdesign/core/Button";
import { CheckboxInput } from "@astryxdesign/core/CheckboxInput";
import { Collapsible, CollapsibleGroup } from "@astryxdesign/core/Collapsible";
import { DateTimeInput, type ISODateTimeString } from "@astryxdesign/core/DateTimeInput";
import { EmptyState } from "@astryxdesign/core/EmptyState";
import { Heading } from "@astryxdesign/core/Heading";
import { List, ListItem } from "@astryxdesign/core/List";
import { MetadataList, MetadataListItem } from "@astryxdesign/core/MetadataList";
import { Pagination } from "@astryxdesign/core/Pagination";
import { Section } from "@astryxdesign/core/Section";
import { Selector } from "@astryxdesign/core/Selector";
import { Spinner } from "@astryxdesign/core/Spinner";
import { HStack, VStack } from "@astryxdesign/core/Stack";
import { StatusDot } from "@astryxdesign/core/StatusDot";
import { Text } from "@astryxdesign/core/Text";
import { TextArea } from "@astryxdesign/core/TextArea";
import { TextInput } from "@astryxdesign/core/TextInput";
import type { FormEvent } from "react";
import { useEffect, useState } from "react";

type CurrentUser = components["schemas"]["CurrentUserResponse"];
type AccountPage = components["schemas"]["AccountPageResponse"];
type AccountDetail = components["schemas"]["AccountDetailResponse"];
type CreateAccountRequest = components["schemas"]["CreateAccountRequest"];
type ReauthenticationRequest = components["schemas"]["ReauthenticationRequest"];
type RoleAssignmentRequest = components["schemas"]["RoleAssignmentRequest"];
type OfficeAppointmentRequest = components["schemas"]["OfficeAppointmentRequest"];
type EndAssignmentRequest = components["schemas"]["EndAssignmentRequest"];
type AccountStatus = "" | "PENDING_ACTIVATION" | "ACTIVE" | "SUSPENDED" | "DEACTIVATED";
type AdminConsoleMode = "accounts" | "create" | "offices";
type GovernanceRequestResponse = { publicId: string; changeType: string; status: string; expiresAt: string };

const statusLabels: Record<string, string> = {
  PENDING_ACTIVATION: "활성화 대기",
  ACTIVE: "활성",
  SUSPENDED: "정지",
  DEACTIVATED: "비활성",
};

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

const roleOptions = roles.map((role) => ({ value: role, label: roleLabels[role] }));
const officeOptions = offices.map((office) => ({ value: office, label: officeLabels[office] }));

const statusOptions = [{ value: "", label: "전체" }, ...Object.entries(statusLabels).map(([value, label]) => ({ value, label }))];

function FormText({ label, name, type = "text", description }: { label: string; name: string; type?: "text" | "password"; description?: string }) {
  const [value, setValue] = useState("");
  return <TextInput label={label} description={description} htmlName={name} type={type} value={value} onChange={setValue} isRequired width="100%" />;
}

function FormArea({ label, name, description }: { label: string; name: string; description?: string }) {
  const [value, setValue] = useState("");
  return <TextArea label={label} description={description} htmlName={name} value={value} onChange={setValue} maxLength={500} rows={4} isRequired width="100%" />;
}

function FormSelect({ label, name, options, initial }: { label: string; name: string; options: Array<{ value: string; label: string }>; initial?: string }) {
  const [value, setValue] = useState(initial ?? options[0]?.value ?? "");
  return <Selector label={label} htmlName={name} options={options} value={value} onChange={setValue} width="100%" />;
}

function FormDateTime({ label, name }: { label: string; name: string }) {
  const [value, setValue] = useState<ISODateTimeString | undefined>();
  return (
    <VStack gap={1}>
      <DateTimeInput label={label} value={value} onChange={setValue} hourFormat="24h" hasClear isOptional width="100%" timeLabel={`${label} 시각`} />
      <input type="hidden" name={name} value={value ?? ""} />
    </VStack>
  );
}

function FormCheckbox({ label, name }: { label: string; name: string }) {
  const [value, setValue] = useState(false);
  return <CheckboxInput label={label} htmlName={name} value={value} onChange={setValue} />;
}

export function AdminConsole({ mode = "accounts" }: { mode?: AdminConsoleMode }) {
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
    if (access !== "ready" || mode === "create") return;
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
  }, [access, mode, page, query, refreshVersion, status]);

  useEffect(() => {
    if (access !== "ready" || mode === "create" || !selectedId) return;
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
  }, [access, mode, refreshVersion, selectedId]);

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
      () => apiPost<GovernanceRequestResponse>("/api/v1/admin/governance/requests", {
        changeType: "CREATE_ACCOUNT",
        ...request,
      }),
      "계정 생성 승인 요청을 등록했습니다.",
      () => {
        formElement.reset();
      },
    );
  }

  function issueCode(kind: "activation" | "password-reset") {
    if (!selectedId) return;
    const activation = kind === "activation";
    void execute(
      () => apiPost<GovernanceRequestResponse>("/api/v1/admin/governance/requests", {
        changeType: activation ? "REISSUE_ACTIVATION_CODE" : "ISSUE_PASSWORD_RESET_CODE",
        targetUserPublicId: selectedId,
        reason: activation ? "가입 코드 재발급" : "비밀번호 재설정 코드 발급",
      }),
      activation ? "가입 코드 재발급 승인 요청을 등록했습니다." : "비밀번호 재설정 코드 발급 승인 요청을 등록했습니다.",
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
      () => apiPost("/api/v1/admin/governance/requests", { changeType: "ASSIGN_ROLE", targetUserPublicId: selectedId, ...request }),
      "역할 추가 승인 요청을 등록했습니다.",
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
      () => apiPost("/api/v1/admin/governance/requests", { changeType: "END_ROLE", targetUserPublicId: selectedId, role, ...request }),
      "역할 종료 승인 요청을 등록했습니다.",
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
      () => apiPost("/api/v1/admin/governance/requests", { changeType: "APPOINT_OFFICE", office, targetUserPublicId: selectedId, ...request }),
      "보직 임명 승인 요청을 등록했습니다.",
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
      () => apiPost("/api/v1/admin/governance/requests", { changeType: "END_OFFICE", office, targetUserPublicId: selectedId, ...request }),
      "보직 종료 승인 요청을 등록했습니다.",
      () => formElement.reset(),
    );
  }

  if (access === "loading") return <Spinner size="lg" label="관리 권한을 확인하고 있습니다…" />;
  if (access === "signed-out") return <EmptyState title="로그인이 필요합니다" description="계정 관리 화면은 슈퍼 어드민만 사용할 수 있습니다." actions={<Button label="로그인" href="/login" variant="primary" />} />;
  if (access === "forbidden") return <EmptyState title="접근 권한이 없습니다" description="현재 계정에는 슈퍼 어드민 역할이 없습니다." actions={<Button label="서비스 홈" href="/" />} />;
  if (access === "error") return <Banner status="error" title="관리 권한을 확인하지 못했습니다" description="잠시 후 다시 시도해 주세요." />;

  return (
    <VStack gap={8}>
      <Section variant="muted" padding={5} aria-labelledby="session-title">
        <HStack hAlign="between" vAlign="center" gap={4} wrap="wrap">
          <VStack gap={1}>
            <Heading level={2} id="session-title">{currentUser?.displayName}</Heading>
            <Text as="p" color="secondary">최근 본인 확인 유효 시각: {formatInstant(currentUser?.reauthenticationExpiresAt)}</Text>
          </VStack>
          <Button label="비밀번호 다시 확인" variant="secondary" onClick={() => setShowReauthentication((value) => !value)} />
        </HStack>
      </Section>

      {showReauthentication ? (
        <Section variant="section" padding={5}>
          <form onSubmit={reauthenticate}>
            <HStack gap={2} vAlign="end" wrap="wrap">
              <FormText label="현재 비밀번호" name="password" type="password" />
              <Button label="본인 확인" type="submit" variant="primary" isLoading={actionPending} isDisabled={actionPending} />
            </HStack>
          </form>
        </Section>
      ) : null}

      {actionError ? <Banner status="error" title="작업을 완료할 수 없습니다" description={actionError} /> : null}
      {notice ? <Banner status="success" title={notice} /> : null}
      <GovernanceRequestConsole />

      {mode === "create" ? <VStack as="section" gap={4} aria-labelledby="create-title">
        <VStack gap={1} maxWidth="72ch">
          <Heading level={2} id="create-title">새 계정 만들기</Heading>
          <Text as="p" color="secondary">계정 생성 요청은 다른 슈퍼 어드민의 승인 후 실행되며 가입 코드는 검증된 수신자 채널로만 전달됩니다.</Text>
        </VStack>
        <Section variant="section" padding={5}>
          <form onSubmit={createAccount}>
            <VStack gap={4}>
              <HStack gap={3} vAlign="start" wrap="wrap">
                <FormText label="로그인 ID" name="loginId" description="영문, 숫자, 마침표, 밑줄, 하이픈을 사용합니다." />
                <FormText label="표시 이름" name="displayName" />
                <FormSelect label="첫 역할" name="role" options={roleOptions} initial="STUDENT" />
              </HStack>
              <FormArea label="발급 사유" name="reason" />
              <Button label="계정 생성 승인 요청" type="submit" variant="primary" isLoading={actionPending} isDisabled={actionPending} />
            </VStack>
          </form>
        </Section>
      </VStack> : null}

      {mode !== "create" ? <VStack as="section" gap={4} aria-labelledby="accounts-title">
        <VStack gap={1} maxWidth="72ch">
          <Heading level={2} id="accounts-title">계정 찾기</Heading>
          <Text as="p" color="secondary">로그인 ID나 표시 이름으로 검색하고 관리할 계정을 선택합니다.</Text>
        </VStack>
        <form onSubmit={search}>
          <HStack gap={2} vAlign="end" wrap="wrap">
            <TextInput label="계정 검색" value={inputQuery} onChange={setInputQuery} placeholder="로그인 ID 또는 이름" hasClear width="min(100%, 28rem)" />
            <Selector label="상태" options={statusOptions} value={status} onChange={(value) => { setListLoading(true); setStatus(value as AccountStatus); setPage(0); }} width="12rem" />
            <Button label="검색" type="submit" variant="secondary" isLoading={listLoading} />
          </HStack>
        </form>
        {listError ? <Banner status="error" title="계정 목록을 불러오지 못했습니다" description={listError} /> : null}
        {listLoading && !accounts ? <Spinner label="계정을 불러오는 중…" /> : accounts?.items.length ? (
          <List hasDividers density="balanced" aria-busy={listLoading}>
            {accounts.items.map((account) => {
              const variant = account.status === "ACTIVE" ? "success" : account.status === "SUSPENDED" ? "warning" : account.status === "DEACTIVATED" ? "neutral" : "accent";
              return (
                <ListItem
                  key={account.publicId}
                  isSelected={selectedId === account.publicId}
                  onClick={() => { setDetail(null); setDetailLoading(true); setSelectedId(account.publicId); setActionError(null); setNotice(null); }}
                  label={
                    <HStack hAlign="between" vAlign="center" gap={4} wrap="wrap">
                      <VStack gap={1}>
                        <Heading level={3}>{account.displayName}</Heading>
                        <Text type="supporting" color="secondary">{account.loginId}</Text>
                      </VStack>
                      <Text type="supporting">{account.currentRoles.map((role) => roleLabels[role as AccountRole] ?? role).join(", ") || "역할 없음"}</Text>
                    </HStack>
                  }
                  endContent={<HStack gap={1} vAlign="center"><StatusDot variant={variant} label={statusLabels[account.status] ?? account.status} /><Text type="supporting">{statusLabels[account.status] ?? account.status}</Text></HStack>}
                />
              );
            })}
          </List>
        ) : <EmptyState title="조건에 맞는 계정이 없습니다" isCompact />}
        {accounts && accounts.totalPages > 1 ? (
          <HStack hAlign="center"><Pagination page={page + 1} totalPages={accounts.totalPages} onChange={(value) => { setListLoading(true); setPage(value - 1); }} isDisabled={listLoading} label="계정 목록 페이지" /></HStack>
        ) : null}
      </VStack> : null}

      {mode !== "create" && selectedId ? (
        <VStack as="section" gap={5} aria-labelledby="selected-title">
          <Heading level={2} id="selected-title">선택 계정 관리</Heading>
          {detail ? (
            <>
              <Section variant="muted" padding={5}>
                <MetadataList orientation="horizontal">
                  <MetadataListItem label="이름">{detail.displayName}</MetadataListItem>
                  <MetadataListItem label="로그인 ID">{detail.loginId}</MetadataListItem>
                  <MetadataListItem label="상태">{statusLabels[detail.status] ?? detail.status}</MetadataListItem>
                </MetadataList>
              </Section>

              {mode === "accounts" ? (
                <CollapsibleGroup type="single" defaultValue="codes" hasDividers density="spacious">
                  <Collapsible trigger="계정 코드 발급" value="codes">
                    <HStack gap={2} wrap="wrap">
                      {detail.status === "PENDING_ACTIVATION" ? <Button label="가입 코드 재발급" variant="secondary" isDisabled={actionPending} onClick={() => issueCode("activation")} /> : null}
                      {detail.status === "ACTIVE" ? <Button label="재설정 코드 발급" variant="secondary" isDisabled={actionPending} onClick={() => issueCode("password-reset")} /> : null}
                      {detail.status !== "PENDING_ACTIVATION" && detail.status !== "ACTIVE" ? <Text color="secondary">현재 상태에서는 발급할 수 있는 코드가 없습니다.</Text> : null}
                    </HStack>
                  </Collapsible>
                  <Collapsible trigger="계정 상태 변경" value="status">
                    {detail.status === "ACTIVE" || detail.status === "SUSPENDED" ? (
                      <Section variant="section" padding={4}>
                        <form onSubmit={(event) => changeStatus(event, detail.status === "ACTIVE" ? "suspensions" : "reactivations")}>
                          <VStack gap={3}>
                            <FormArea label={detail.status === "ACTIVE" ? "정지 사유" : "재활성화 사유"} name="reason" />
                            <Button label={detail.status === "ACTIVE" ? "계정 정지" : "계정 재활성화"} type="submit" variant={detail.status === "ACTIVE" ? "destructive" : "primary"} isLoading={actionPending} isDisabled={actionPending} />
                          </VStack>
                        </form>
                      </Section>
                    ) : <Text color="secondary">활성 또는 정지 계정만 상태를 변경할 수 있습니다.</Text>}
                  </Collapsible>
                  <Collapsible trigger="역할 추가" value="role-add">
                    <Section variant="section" padding={4}>
                      <form onSubmit={assignRole}><VStack gap={3}>
                        <FormSelect label="추가할 역할" name="role" options={roleOptions} />
                        <HStack gap={3} wrap="wrap"><FormDateTime label="시작 시각" name="startsAt" /><FormDateTime label="종료 시각" name="endsAt" /></HStack>
                        <FormArea label="추가 사유" name="reason" />
                        <Button label="역할 추가" type="submit" variant="primary" isLoading={actionPending} isDisabled={actionPending} />
                      </VStack></form>
                    </Section>
                  </Collapsible>
                  <Collapsible trigger="역할 종료" value="role-end">
                    <Section variant="muted" padding={4}>
                      <form onSubmit={endRole}><VStack gap={3}>
                        <FormSelect label="종료할 역할" name="role" options={roleOptions} />
                        <FormDateTime label="종료 시각" name="endsAt" />
                        <FormArea label="종료 사유" name="reason" />
                        <Button label="역할 종료" type="submit" variant="destructive" isLoading={actionPending} isDisabled={actionPending} />
                      </VStack></form>
                    </Section>
                  </Collapsible>
                  <Collapsible trigger="역할 이력" value="role-history">
                    <HistoryList items={detail.roles.map((item) => ({ key: `${item.role}-${item.startsAt}`, title: roleLabels[item.role as AccountRole] ?? item.role ?? "역할", period: periodText(item.startsAt, item.endsAt), reason: item.endReason ?? item.reason }))} />
                  </Collapsible>
                </CollapsibleGroup>
              ) : null}

              {mode === "offices" ? (
                <VStack gap={4}>
                  <Text as="p" color="secondary">학생부장교사, 학생회장, 학생부회장 세 보직만 임명합니다. 먼저 위에서 대상 계정을 선택해 주세요.</Text>
                  <CollapsibleGroup type="single" defaultValue="office-appoint" hasDividers density="spacious">
                    <Collapsible trigger="보직 임명" value="office-appoint">
                      <Section variant="section" padding={4}>
                        <form onSubmit={appointOffice}><VStack gap={3}>
                          <FormSelect label="고정 보직" name="office" options={officeOptions} />
                          <HStack gap={3} wrap="wrap"><FormDateTime label="시작 시각" name="startsAt" /><FormDateTime label="종료 시각" name="endsAt" /></HStack>
                          <FormCheckbox label="시작 시점의 현임자 임기를 종료하고 교체" name="replaceExistingAtStart" />
                          <FormArea label="임명 사유" name="reason" />
                          <Button label="보직 임명" type="submit" variant="primary" isLoading={actionPending} isDisabled={actionPending} />
                        </VStack></form>
                      </Section>
                    </Collapsible>
                    <Collapsible trigger="보직 종료" value="office-end">
                      <Section variant="muted" padding={4}>
                        <form onSubmit={endOffice}><VStack gap={3}>
                          <FormSelect label="종료할 고정 보직" name="office" options={officeOptions} />
                          <FormDateTime label="종료 시각" name="endsAt" />
                          <FormArea label="종료 사유" name="reason" />
                          <Button label="보직 종료" type="submit" variant="destructive" isLoading={actionPending} isDisabled={actionPending} />
                        </VStack></form>
                      </Section>
                    </Collapsible>
                    <Collapsible trigger="보직 이력" value="office-history">
                      <HistoryList items={detail.offices.map((item) => ({ key: `${item.office}-${item.startsAt}`, title: officeLabels[item.office as OfficeType] ?? item.office ?? "보직", period: periodText(item.startsAt, item.endsAt), reason: item.endReason ?? item.reason }))} />
                    </Collapsible>
                  </CollapsibleGroup>
                </VStack>
              ) : null}
            </>
          ) : detailLoading ? <Spinner label="계정 정보를 불러오는 중…" /> : <Banner status="error" title="계정 정보를 불러오지 못했습니다" />}
        </VStack>
      ) : null}
    </VStack>
  );
}

function HistoryList({ items }: { items: Array<{ key: string; title: string; period: string; reason?: string }> }) {
  if (!items.length) return <EmptyState title="저장된 임기 이력이 없습니다" isCompact />;
  return (
    <List hasDividers density="compact">
      {items.map((item) => <ListItem key={item.key} label={item.title} description={`${item.period}${item.reason ? ` · ${item.reason}` : ""}`} />)}
    </List>
  );
}
