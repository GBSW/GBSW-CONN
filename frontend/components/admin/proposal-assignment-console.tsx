"use client";

import type { components } from "@/lib/api-schema";
import { ApiRequestError, apiGet, apiPost, errorMessage } from "@/lib/api-client";
import { Banner } from "@astryxdesign/core/Banner";
import { Button } from "@astryxdesign/core/Button";
import { EmptyState } from "@astryxdesign/core/EmptyState";
import { Heading } from "@astryxdesign/core/Heading";
import { List, ListItem } from "@astryxdesign/core/List";
import { Section } from "@astryxdesign/core/Section";
import { Selector } from "@astryxdesign/core/Selector";
import { Spinner } from "@astryxdesign/core/Spinner";
import { HStack, VStack } from "@astryxdesign/core/Stack";
import { StatusDot } from "@astryxdesign/core/StatusDot";
import { Text } from "@astryxdesign/core/Text";
import { TextInput } from "@astryxdesign/core/TextInput";
import { useCallback, useEffect, useState } from "react";

type CurrentUser = components["schemas"]["CurrentUserResponse"];
type AdminProposal = components["schemas"]["AdminProposalSummaryResponse"];
type EligibleTeacher = components["schemas"]["EligibleProposalTeacherResponse"];
type Assignment = components["schemas"]["ProposalAssignmentResponse"];

const statusLabels: Record<string, string> = {
  FORMAL_AGENDA: "정식 안건", UNDER_REVIEW: "검토 중", ACCEPTED: "채택", ON_HOLD: "보류",
  REJECTED: "반려", IN_PROGRESS: "실행 중", COMPLETED: "완료",
};

export function ProposalAssignmentConsole() {
  const [access, setAccess] = useState<"loading" | "ready" | "signed-out" | "forbidden" | "error">("loading");
  const [proposals, setProposals] = useState<AdminProposal[]>([]);
  const [teachers, setTeachers] = useState<EligibleTeacher[]>([]);
  const [loading, setLoading] = useState(true);
  const [pendingId, setPendingId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const loadData = useCallback(async () => {
    setLoading(true);
    const [proposalResult, teacherResult] = await Promise.all([
      apiGet<AdminProposal[]>("/api/v1/admin/proposals?size=100"),
      apiGet<EligibleTeacher[]>("/api/v1/admin/proposals/eligible-teachers?size=100"),
    ]);
    setProposals(proposalResult);
    setTeachers(teacherResult.filter((teacher) => teacher.publicId && teacher.displayName));
    setLoading(false);
  }, []);

  useEffect(() => {
    let active = true;
    apiGet<CurrentUser>("/api/v1/auth/me")
      .then(async (user) => {
        if (!active) return;
        if (!user.roles.includes("SUPER_ADMIN")) { setAccess("forbidden"); return; }
        setAccess("ready");
        await loadData();
      })
      .catch((caught: unknown) => {
        if (!active) return;
        const signedOut = caught instanceof ApiRequestError && ["AUTHENTICATION_REQUIRED", "SESSION_INVALIDATED"].includes(caught.code);
        setAccess(signedOut ? "signed-out" : "error");
        setLoading(false);
      });
    return () => { active = false; };
  }, [loadData]);

  async function assign(publicId: string, teacherPublicId: string, reason: string) {
    setPendingId(publicId);
    setError(null);
    setNotice(null);
    try {
      await apiPost<Assignment>(`/api/v1/admin/proposals/${publicId}/assignments`, { teacherPublicId, reason: reason.trim() });
      await loadData();
      setNotice("담당 교사를 내부 지정했습니다. 이 정보는 학생 화면에 공개되지 않습니다.");
    } catch (caught) {
      if (caught instanceof ApiRequestError && caught.code === "REAUTHENTICATION_REQUIRED") setError("최근 본인 확인이 필요합니다. 계정 관리 화면에서 비밀번호를 다시 확인한 뒤 재시도해 주세요.");
      else setError(errorMessage(caught));
    } finally {
      setPendingId(null);
    }
  }

  if (access === "loading") return <Spinner size="lg" label="관리 권한을 확인하고 있습니다…" />;
  if (access === "signed-out") return <EmptyState title="로그인이 필요합니다" actions={<Button label="로그인" href="/login" variant="primary" />} />;
  if (access === "forbidden") return <EmptyState title="접근 권한이 없습니다" description="슈퍼 어드민만 담당 교사를 내부 지정할 수 있습니다." />;
  if (access === "error") return <Banner status="error" title="관리 정보를 불러오지 못했습니다" />;

  const teacherOptions = teachers.map((teacher) => ({ value: teacher.publicId ?? "", label: teacher.displayName ?? "이름 없음" }));
  return (
    <VStack gap={5}>
      <Section variant="muted" padding={5}>
        <HStack hAlign="between" vAlign="center" gap={4} wrap="wrap">
          <VStack gap={1} maxWidth="72ch">
            <Heading level={2}>최소 공개 원칙</Heading>
            <Text as="p" color="secondary">이 화면에는 제안 본문을 표시하지 않습니다. 담당 교사 정보도 학생 API와 화면에 전달되지 않습니다.</Text>
          </VStack>
          <Button label="본인 확인·계정 관리" href="/admin" variant="secondary" />
        </HStack>
      </Section>
      {error ? <Banner status="error" title="담당 교사를 지정할 수 없습니다" description={error} /> : null}
      {notice ? <Banner status="success" title={notice} /> : null}
      {loading ? <Spinner label="정식 안건을 불러오고 있습니다…" /> : null}
      {!loading && teachers.length === 0 ? <Banner status="warning" title="지정 가능한 활성 교사가 없습니다" /> : null}
      {!loading && proposals.length === 0 ? <EmptyState title="담당 지정이 필요한 정식 안건이 없습니다" /> : (
        <List hasDividers density="spacious">
          {proposals.map((proposal) => proposal.publicId ? (
            <ListItem
              key={proposal.publicId}
              label={
                <VStack gap={2}>
                  <HStack gap={2} vAlign="center">
                    <StatusDot variant="accent" label={statusLabels[proposal.workflowStatus ?? ""] ?? "안건 상태"} />
                    <Text type="supporting">{statusLabels[proposal.workflowStatus ?? ""] ?? proposal.workflowStatus}</Text>
                  </HStack>
                  <Heading level={2}>{proposal.title}</Heading>
                  <Text as="p" color="secondary">현재 담당: {proposal.assignment?.teacherDisplayName ?? "미지정"}</Text>
                  {!["REJECTED", "COMPLETED"].includes(proposal.workflowStatus ?? "") ? (
                    <AssignmentForm
                      proposal={proposal}
                      teacherOptions={teacherOptions}
                      isPending={pendingId === proposal.publicId}
                      isDisabled={teachers.length === 0}
                      onAssign={assign}
                    />
                  ) : <Text type="supporting">종결된 안건은 담당자를 변경하지 않습니다.</Text>}
                </VStack>
              }
            />
          ) : null)}
        </List>
      )}
    </VStack>
  );
}

function AssignmentForm({ proposal, teacherOptions, isPending, isDisabled, onAssign }: {
  proposal: AdminProposal;
  teacherOptions: Array<{ value: string; label: string }>;
  isPending: boolean;
  isDisabled: boolean;
  onAssign: (publicId: string, teacherPublicId: string, reason: string) => Promise<void>;
}) {
  const [teacherPublicId, setTeacherPublicId] = useState("");
  const [reason, setReason] = useState("");
  return (
    <form onSubmit={(event) => { event.preventDefault(); if (proposal.publicId) void onAssign(proposal.publicId, teacherPublicId, reason); }}>
      <HStack gap={2} vAlign="end" wrap="wrap">
        <Selector label="담당 교사" options={teacherOptions} value={teacherPublicId} onChange={setTeacherPublicId} placeholder="교사를 선택하세요" width="16rem" isDisabled={isDisabled} disabledMessage="지정 가능한 교사가 없습니다." />
        <TextInput label="지정·변경 사유" value={reason} onChange={setReason} width="min(100%, 24rem)" />
        <Button label={proposal.assignment ? "담당 변경" : "담당 지정"} type="submit" variant="primary" isLoading={isPending} isDisabled={isPending || isDisabled || !teacherPublicId || !reason.trim()} />
      </HStack>
    </form>
  );
}
