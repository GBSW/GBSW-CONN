"use client";

import type { components } from "@/lib/api-schema";
import { ApiRequestError, apiGet, errorMessage } from "@/lib/api-client";
import { Banner } from "@astryxdesign/core/Banner";
import { Button } from "@astryxdesign/core/Button";
import { EmptyState } from "@astryxdesign/core/EmptyState";
import { Heading } from "@astryxdesign/core/Heading";
import { List, ListItem } from "@astryxdesign/core/List";
import { Pagination } from "@astryxdesign/core/Pagination";
import { ProgressBar } from "@astryxdesign/core/ProgressBar";
import { Selector } from "@astryxdesign/core/Selector";
import { Spinner } from "@astryxdesign/core/Spinner";
import { HStack, StackItem, VStack } from "@astryxdesign/core/Stack";
import { StatusDot } from "@astryxdesign/core/StatusDot";
import { Text } from "@astryxdesign/core/Text";
import { TextInput } from "@astryxdesign/core/TextInput";
import { Token } from "@astryxdesign/core/Token";
import { useMediaQuery } from "@astryxdesign/core/hooks";
import type { FormEvent } from "react";
import { useEffect, useState } from "react";

type CurrentUser = components["schemas"]["CurrentUserResponse"];
type ProposalPage = components["schemas"]["ProposalPageResponse"];
type ProposalScope = "ALL" | "FORMAL_AGENDA";
type ProposalSort = "LATEST" | "MOST_SUPPORTED";

const dateFormatter = new Intl.DateTimeFormat("ko-KR", { timeZone: "Asia/Seoul", dateStyle: "medium" });
const scopeOptions = [{ value: "ALL", label: "전체 제안" }, { value: "FORMAL_AGENDA", label: "정식 안건" }];
const sortOptions = [{ value: "LATEST", label: "최신순" }, { value: "MOST_SUPPORTED", label: "동의 많은 순" }];

export function ProposalFeed() {
  const isMobile = useMediaQuery("(max-width: 768px)");
  const [user, setUser] = useState<CurrentUser | null>(null);
  const [access, setAccess] = useState<"loading" | "ready" | "signed-out" | "forbidden" | "error">("loading");
  const [inputQuery, setInputQuery] = useState("");
  const [query, setQuery] = useState("");
  const [scope, setScope] = useState<ProposalScope>("ALL");
  const [sort, setSort] = useState<ProposalSort>("LATEST");
  const [page, setPage] = useState(0);
  const [result, setResult] = useState<ProposalPage | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    apiGet<CurrentUser>("/api/v1/auth/me")
      .then((currentUser) => {
        if (!active) return;
        setUser(currentUser);
        setAccess(currentUser.roles.includes("STUDENT") || currentUser.roles.includes("TEACHER") ? "ready" : "forbidden");
      })
      .catch((caught: unknown) => {
        if (!active) return;
        const signedOut = caught instanceof ApiRequestError && ["AUTHENTICATION_REQUIRED", "SESSION_INVALIDATED"].includes(caught.code);
        setAccess(signedOut ? "signed-out" : "error");
      });
    return () => { active = false; };
  }, []);

  useEffect(() => {
    if (access !== "ready") return;
    let active = true;
    const parameters = new URLSearchParams({ scope, sort, page: String(page), size: "20" });
    if (query) parameters.set("query", query);
    apiGet<ProposalPage>(`/api/v1/proposals?${parameters.toString()}`)
      .then((response) => {
        if (!active) return;
        setResult(response);
        setError(null);
      })
      .catch((caught: unknown) => {
        if (!active) return;
        if (caught instanceof ApiRequestError && ["AUTHENTICATION_REQUIRED", "SESSION_INVALIDATED"].includes(caught.code)) setAccess("signed-out");
        else setError(errorMessage(caught));
      })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [access, page, query, scope, sort]);

  function search(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setLoading(true);
    setPage(0);
    setQuery(inputQuery.trim());
  }

  if (access === "loading") return <Spinner size="lg" label="제안 열람 권한을 확인하고 있습니다…" />;
  if (access === "signed-out") return <EmptyState title="로그인이 필요합니다" description="학교에서 발급받은 계정으로 로그인하면 제안을 확인할 수 있습니다." actions={<Button label="로그인" href="/login" variant="primary" />} headingLevel={1} />;
  if (access === "forbidden") return <EmptyState title="제안 열람 권한이 없습니다" description="학생 또는 교사 역할이 있는 계정만 제안 피드를 사용할 수 있습니다." headingLevel={1} />;
  if (access === "error") return <Banner status="error" title="로그인 상태를 확인하지 못했습니다" description="잠시 후 다시 시도해 주세요." />;

  const student = user?.roles.includes("STUDENT") ?? false;
  return (
    <VStack gap={6}>
      <HStack hAlign="between" vAlign="end" gap={4} wrap="wrap">
        <VStack gap={2} maxWidth="72ch">
          <Heading level={1} type="display-2">{student ? "함께 바꿀 제안" : "검토할 정식 안건"}</Heading>
          <Text as="p" color="secondary">
            {student ? "최신 제안을 읽고 공감하는 의견에 동의하세요. 50명에 도달하면 정식 안건이 됩니다." : "교사 계정에는 50명 이상의 동의를 얻은 정식 안건만 표시됩니다."}
          </Text>
        </VStack>
        {student ? <Button label="공개 제안 작성" href="/proposals/new" variant="primary" size="lg" /> : null}
      </HStack>

      <form onSubmit={search} aria-label="제안 검색">
        <HStack gap={2} vAlign="end" wrap="wrap">
          <StackItem size="fill">
            <TextInput label="검색" value={inputQuery} onChange={setInputQuery} placeholder="제목 또는 내용" hasClear width="100%" />
          </StackItem>
          {student ? <Selector label="범위" options={scopeOptions} value={scope} onChange={(value) => { setLoading(true); setPage(0); setScope(value as ProposalScope); }} width="12rem" /> : null}
          <Selector label="정렬" options={sortOptions} value={sort} onChange={(value) => { setLoading(true); setPage(0); setSort(value as ProposalSort); }} width="12rem" />
          <Button label="검색" type="submit" variant="secondary" isLoading={loading} />
        </HStack>
      </form>

      {query ? <HStack gap={2} vAlign="center"><Token label={`검색: ${query}`} onRemove={() => { setInputQuery(""); setQuery(""); setPage(0); }} /><Text type="supporting" color="secondary">검색 결과</Text></HStack> : null}
      {error ? <Banner status="error" title="제안을 불러오지 못했습니다" description={error} /> : null}

      {loading && !result ? <Spinner size="lg" label="제안을 불러오는 중…" /> : result?.items.length ? (
        <List density="spacious" hasDividers aria-busy={loading}>
          {result.items.map((proposal) => {
            const gathering = proposal.workflowStatus === "GATHERING_SUPPORT";
            return (
              <ListItem
                key={proposal.publicId}
                href={`/proposals/${proposal.publicId}`}
                label={
                  <VStack gap={1}>
                    <HStack gap={2} vAlign="center" wrap="wrap">
                      <Heading level={2} maxLines={2}>{proposal.title}</Heading>
                      <HStack gap={1} vAlign="center">
                        <StatusDot variant={gathering ? "accent" : "success"} label={gathering ? "동의 모집 중" : "정식 안건"} />
                        <Text type="supporting" weight="medium">{gathering ? "동의 모집 중" : "정식 안건"}</Text>
                      </HStack>
                    </HStack>
                    <Text as="p" color="secondary" maxLines={2}>{proposal.excerpt}</Text>
                    <Text type="supporting" color="secondary">{proposal.authorVisibility === "NAMED" ? proposal.authorDisplayName : "익명"} · {dateFormatter.format(new Date(proposal.createdAt))}</Text>
                    {isMobile ? <ProposalSupport proposal={proposal} gathering={gathering} isMobile /> : null}
                  </VStack>
                }
                endContent={isMobile ? null : <ProposalSupport proposal={proposal} gathering={gathering} />}
              />
            );
          })}
        </List>
      ) : <EmptyState title={loading ? "제안을 불러오는 중입니다" : "조건에 맞는 제안이 없습니다"} description={loading ? undefined : "검색어나 필터를 바꿔 다시 확인해 보세요."} />}

      <CenterPagination page={page} result={result} loading={loading} onChange={(next) => { setLoading(true); setPage(next); }} />
    </VStack>
  );
}

function ProposalSupport({ proposal, gathering, isMobile = false }: { proposal: ProposalPage["items"][number]; gathering: boolean; isMobile?: boolean }) {
  return (
    <VStack gap={2} width={isMobile ? "100%" : "14rem"} data-testid={isMobile ? "proposal-support-mobile" : undefined}>
      <Text type="supporting" weight="medium" hasTabularNumbers>
        {gathering ? `${proposal.supportCount} / ${proposal.supportThreshold}명` : `현재 ${proposal.supportCount}명 동의`}
      </Text>
      <ProgressBar value={proposal.supportCount} max={proposal.supportThreshold} label={`${proposal.supportCount}명 동의`} isLabelHidden variant={gathering ? "accent" : "success"} />
    </VStack>
  );
}

function CenterPagination({ page, result, loading, onChange }: { page: number; result: ProposalPage | null; loading: boolean; onChange: (page: number) => void }) {
  if (!result || result.totalPages <= 1) return null;
  return (
    <HStack hAlign="center">
      <Pagination page={page + 1} totalPages={result.totalPages} onChange={(value) => onChange(value - 1)} isDisabled={loading} label="제안 목록 페이지" />
    </HStack>
  );
}
