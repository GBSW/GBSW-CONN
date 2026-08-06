"use client";

import type { components } from "@/lib/api-schema";
import { ApiRequestError, apiGet, apiPost, errorMessage } from "@/lib/api-client";
import { officeLabel } from "@/lib/roles";
import { Banner } from "@astryxdesign/core/Banner";
import { Button } from "@astryxdesign/core/Button";
import { EmptyState } from "@astryxdesign/core/EmptyState";
import { Heading } from "@astryxdesign/core/Heading";
import { List, ListItem } from "@astryxdesign/core/List";
import { MetadataList, MetadataListItem } from "@astryxdesign/core/MetadataList";
import { Section } from "@astryxdesign/core/Section";
import { Selector } from "@astryxdesign/core/Selector";
import { Spinner } from "@astryxdesign/core/Spinner";
import { HStack, VStack } from "@astryxdesign/core/Stack";
import { StatusDot } from "@astryxdesign/core/StatusDot";
import { Text } from "@astryxdesign/core/Text";
import { TextArea } from "@astryxdesign/core/TextArea";
import { FormEvent, useEffect, useState } from "react";

type CurrentUser = components["schemas"]["CurrentUserResponse"];
type ReportItem = components["schemas"]["ReportInboxItemResponse"];
type ModerationCase = components["schemas"]["ModerationCaseResponse"];

const caseTypeLabels: Record<string, string> = { CONTENT_VISIBILITY: "공개 제한 심의", IDENTITY_REVEAL: "신원 확인 심의" };
const statusLabels: Record<string, string> = { PENDING: "심의 중", APPROVED: "승인", REJECTED: "반려" };
const statusVariants: Record<string, "accent" | "success" | "error"> = { PENDING: "accent", APPROVED: "success", REJECTED: "error" };
const dateTimeFormatter = new Intl.DateTimeFormat("ko-KR", { timeZone: "Asia/Seoul", dateStyle: "medium", timeStyle: "short" });

export function ModerationConsole() {
  const [user, setUser] = useState<CurrentUser | null>(null);
  const [reports, setReports] = useState<ReportItem[]>([]);
  const [cases, setCases] = useState<ModerationCase[]>([]);
  const [state, setState] = useState<"loading" | "ready" | "signed-out" | "error">("loading");
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    apiGet<CurrentUser>("/api/v1/auth/me")
      .then(async (currentUser) => {
        const reviewerCases = await apiGet<ModerationCase[]>("/api/v1/moderation/cases?size=100");
        const reportItems = currentUser.offices.includes("STUDENT_AFFAIRS_TEACHER") ? await apiGet<ReportItem[]>("/api/v1/moderation/reports?size=100") : [];
        if (!active) return;
        setUser(currentUser);
        setCases(reviewerCases);
        setReports(reportItems);
        setState("ready");
      })
      .catch((caught: unknown) => {
        if (!active) return;
        if (caught instanceof ApiRequestError && ["AUTHENTICATION_REQUIRED", "SESSION_INVALIDATED"].includes(caught.code)) setState("signed-out");
        else { setError(errorMessage(caught)); setState("error"); }
      });
    return () => { active = false; };
  }, []);

  function created(reportPublicId: string, createdCase: ModerationCase) {
    setCases((current) => [createdCase, ...current]);
    setReports((current) => current.map((report) => report.publicId === reportPublicId ? {
      ...report, existingCaseTypes: [...(report.existingCaseTypes ?? []), createdCase.caseType ?? ""],
    } : report));
  }

  if (state === "loading") return <Spinner size="lg" label="심의 사건함을 불러오고 있습니다…" />;
  if (state === "signed-out") return <EmptyState title="로그인이 필요합니다" actions={<Button label="로그인" href="/login" variant="primary" />} headingLevel={1} />;
  if (state === "error") return <Banner status="error" title="심의 사건함을 불러오지 못했습니다" description={error ?? undefined} />;

  const studentAffairs = user?.offices.includes("STUDENT_AFFAIRS_TEACHER") ?? false;
  return (
    <VStack gap={8}>
      <VStack gap={2} maxWidth="72ch">
        <Heading level={1} type="display-2">보호 심의</Heading>
        <Text as="p" color="secondary">신고와 결정을 분리합니다. 공개 제한과 신원 확인은 각각 별도 사건으로 의결하며, 세 명의 고정 심의자가 같은 자료를 봅니다.</Text>
      </VStack>

      {studentAffairs ? (
        <VStack as="section" gap={4} aria-labelledby="reports-title">
          <VStack gap={1}>
            <Heading level={2} id="reports-title">접수된 신고</Heading>
            <Text as="p" color="secondary">신고가 쌓이면 일반 사용자에게서 임시로 가려지지만 되돌릴 수 있는 상태입니다. 되돌릴 수 없는 공개 제한과 신원 확인은 각각 심의로 결정하세요.</Text>
          </VStack>
          {reports.length === 0 ? <EmptyState title="접수된 신고가 없습니다" isCompact /> : (
            <List hasDividers density="spacious">
              {reports.map((report) => (
                <ListItem
                  key={report.publicId}
                  label={
                    <VStack gap={3}>
                      <HStack hAlign="between" gap={2} wrap="wrap">
                        <Text type="supporting" color="secondary">{report.createdAt ? dateTimeFormatter.format(new Date(report.createdAt)) : "시간 정보 없음"}</Text>
                        <Text type="supporting">{(report.existingCaseTypes ?? []).length}개 사건 생성됨</Text>
                      </HStack>
                      <Heading level={3}>{report.proposalTitle ?? "제목 없는 제안"}</Heading>
                      <Text as="p" className="pre-wrap" maxLines={4}>{report.proposalContent}</Text>
                      <MetadataList columns="single"><MetadataListItem label="신고 사유">{report.reason}</MetadataListItem></MetadataList>
                      {report.publicId ? <CreateCaseForm key={`${report.publicId}-${(report.existingCaseTypes ?? []).join("-")}`} report={report} onCreated={(createdCase) => created(report.publicId ?? "", createdCase)} /> : null}
                    </VStack>
                  }
                />
              ))}
            </List>
          )}
        </VStack>
      ) : null}

      <VStack as="section" gap={4} aria-labelledby="cases-title">
        <VStack gap={1}>
          <Heading level={2} id="cases-title">내 심의 사건</Heading>
          <Text as="p" color="secondary">본인에게 고정 배정된 사건만 표시됩니다.</Text>
        </VStack>
        {cases.length === 0 ? <EmptyState title="배정된 심의 사건이 없습니다" isCompact /> : (
          <List hasDividers density="spacious">
            {cases.map((moderationCase) => {
              const status = moderationCase.caseStatus ?? "";
              return (
                <ListItem
                  key={moderationCase.publicId}
                  href={moderationCase.publicId ? `/moderation/${moderationCase.publicId}` : undefined}
                  label={
                    <VStack gap={2}>
                      <HStack gap={2} vAlign="center" wrap="wrap">
                        <StatusDot variant={statusVariants[status] ?? "accent"} label={statusLabels[status] ?? status} />
                        <Text type="supporting" weight="medium">{caseTypeLabels[moderationCase.caseType ?? ""] ?? moderationCase.caseType} · {statusLabels[status] ?? status}</Text>
                      </HStack>
                      <Heading level={3}>{moderationCase.proposalTitle ?? "제목 없는 제안"}</Heading>
                      <Text as="p" color="secondary">{officeLabel(moderationCase.viewerOffice ?? "")} 자격 · {(moderationCase.votes ?? []).length}/3 의결</Text>
                    </VStack>
                  }
                  endContent={<Button label="사건 상세" href={moderationCase.publicId ? `/moderation/${moderationCase.publicId}` : undefined} variant="secondary" size="sm" />}
                />
              );
            })}
          </List>
        )}
      </VStack>
    </VStack>
  );
}

function CreateCaseForm({ report, onCreated }: { report: ReportItem; onCreated: (createdCase: ModerationCase) => void }) {
  const existing = report.existingCaseTypes ?? [];
  const available = ["CONTENT_VISIBILITY", "IDENTITY_REVEAL"].filter((type) => !existing.includes(type));
  const [caseType, setCaseType] = useState(available[0] ?? "");
  const [reason, setReason] = useState("");
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!report.publicId || !caseType) return;
    setPending(true);
    setError(null);
    try {
      const created = await apiPost<ModerationCase>(`/api/v1/moderation/reports/${report.publicId}/cases`, { caseType, reason });
      setReason("");
      onCreated(created);
    } catch (caught) {
      setError(errorMessage(caught));
    } finally {
      setPending(false);
    }
  }

  if (available.length === 0) return <Banner status="success" title="두 심의 사건이 모두 생성되었습니다" />;
  const options = available.map((type) => ({ value: type, label: caseTypeLabels[type] }));
  return (
    <Section variant="muted" padding={4}>
      <form onSubmit={(event) => void submit(event)}>
        <VStack gap={3}>
          <Selector label="심의 유형" options={options} value={caseType} onChange={setCaseType} width="100%" />
          <TextArea label="사건 생성 사유" value={reason} onChange={setReason} maxLength={2000} rows={4} isRequired width="100%" />
          {error ? <Banner status="error" title="심의 사건을 만들 수 없습니다" description={error} /> : null}
          <Button label="심의 사건 생성" type="submit" variant="primary" isLoading={pending} isDisabled={pending || !reason.trim()} />
        </VStack>
      </form>
    </Section>
  );
}
