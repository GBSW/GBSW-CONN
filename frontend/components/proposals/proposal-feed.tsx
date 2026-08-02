"use client";

import type { components } from "@/lib/api-schema";
import { ApiRequestError, apiGet, errorMessage } from "@/lib/api-client";
import Link from "next/link";
import type { FormEvent } from "react";
import { useEffect, useState } from "react";

type CurrentUser = components["schemas"]["CurrentUserResponse"];
type ProposalPage = components["schemas"]["ProposalPageResponse"];
type ProposalScope = "ALL" | "FORMAL_AGENDA";
type ProposalSort = "LATEST" | "MOST_SUPPORTED";

const dateFormatter = new Intl.DateTimeFormat("ko-KR", {
  timeZone: "Asia/Seoul",
  dateStyle: "medium",
});

function formatDate(value: string): string {
  return dateFormatter.format(new Date(value));
}

export function ProposalFeed() {
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
        const canRead = currentUser.roles.includes("STUDENT") || currentUser.roles.includes("TEACHER");
        setAccess(canRead ? "ready" : "forbidden");
      })
      .catch((caught: unknown) => {
        if (!active) return;
        const signedOut = caught instanceof ApiRequestError
          && ["AUTHENTICATION_REQUIRED", "SESSION_INVALIDATED"].includes(caught.code);
        setAccess(signedOut ? "signed-out" : "error");
      });
    return () => {
      active = false;
    };
  }, []);

  useEffect(() => {
    if (access !== "ready") return;
    let active = true;
    const parameters = new URLSearchParams({
      scope,
      sort,
      page: String(page),
      size: "20",
    });
    if (query) parameters.set("query", query);
    apiGet<ProposalPage>(`/api/v1/proposals?${parameters.toString()}`)
      .then((response) => {
        if (!active) return;
        setResult(response);
        setError(null);
      })
      .catch((caught: unknown) => {
        if (!active) return;
        if (caught instanceof ApiRequestError
          && ["AUTHENTICATION_REQUIRED", "SESSION_INVALIDATED"].includes(caught.code)) {
          setAccess("signed-out");
        } else {
          setError(errorMessage(caught));
        }
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [access, page, query, scope, sort]);

  function search(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setLoading(true);
    setPage(0);
    setQuery(inputQuery.trim());
  }

  if (access === "loading") {
    return <p className="proposal-state" role="status">제안 열람 권한을 확인하고 있습니다…</p>;
  }
  if (access === "signed-out") {
    return (
      <section className="proposal-state">
        <h1>로그인이 필요합니다</h1>
        <p>학교에서 발급받은 계정으로 로그인하면 제안을 확인할 수 있습니다.</p>
        <Link className="primary-link" href="/login">로그인</Link>
      </section>
    );
  }
  if (access === "forbidden") {
    return (
      <section className="proposal-state">
        <h1>제안 열람 권한이 없습니다</h1>
        <p>학생 또는 교사 역할이 있는 계정만 제안 피드를 사용할 수 있습니다.</p>
      </section>
    );
  }
  if (access === "error") {
    return <p className="form-error" role="alert">로그인 상태를 확인하지 못했습니다.</p>;
  }

  const student = user?.roles.includes("STUDENT") ?? false;
  return (
    <>
      <header className="proposal-intro">
        <div>
          <p className="eyebrow">공개 학교 개선 제안</p>
          <h1>{student ? "함께 바꿀 제안을 살펴보세요" : "검토할 정식 안건"}</h1>
          <p>
            {student
              ? "최신 제안을 읽고 공감하는 의견에 동의할 수 있습니다. 50명에 도달하면 정식 안건이 됩니다."
              : "교사 계정에는 50명 이상의 동의를 얻은 정식 안건만 표시됩니다."}
          </p>
        </div>
        {student ? <Link className="primary-link" href="/proposals/new">제안 작성</Link> : null}
      </header>

      <section className="proposal-filters" aria-label="제안 필터">
        <form className="proposal-search" onSubmit={search}>
          <label htmlFor="proposal-query">검색</label>
          <input
            id="proposal-query"
            value={inputQuery}
            onChange={(event) => setInputQuery(event.target.value)}
            maxLength={100}
            placeholder="제목 또는 내용"
          />
          <button className="secondary-button" type="submit">검색</button>
        </form>
        <div className="proposal-filter-controls">
          {student ? (
            <label>
              공개 범위
              <select
                value={scope}
                onChange={(event) => {
                  setLoading(true);
                  setPage(0);
                  setScope(event.target.value as ProposalScope);
                }}
              >
                <option value="ALL">전체 제안</option>
                <option value="FORMAL_AGENDA">정식 안건</option>
              </select>
            </label>
          ) : null}
          <label>
            정렬
            <select
              value={sort}
              onChange={(event) => {
                setLoading(true);
                setPage(0);
                setSort(event.target.value as ProposalSort);
              }}
            >
              <option value="LATEST">최신순</option>
              <option value="MOST_SUPPORTED">동의 많은 순</option>
            </select>
          </label>
        </div>
      </section>

      {error ? <p className="form-error" role="alert">{error}</p> : null}
      <section className="proposal-list" aria-busy={loading} aria-label="제안 목록">
        {result?.items.length ? result.items.map((proposal) => (
          <article className="proposal-item" key={proposal.publicId}>
            <div className="proposal-item-meta">
              <span>{proposal.authorVisibility === "NAMED" ? proposal.authorDisplayName : "익명"}</span>
              <time dateTime={proposal.createdAt}>{formatDate(proposal.createdAt)}</time>
            </div>
            <h2><Link href={`/proposals/${proposal.publicId}`}>{proposal.title}</Link></h2>
            <p>{proposal.excerpt}</p>
            <div className="proposal-progress">
              {proposal.workflowStatus === "GATHERING_SUPPORT" ? (
                <>
                  <strong>동의 모집 중</strong>
                  <span>{proposal.supportCount} / {proposal.supportThreshold}</span>
                </>
              ) : (
                <>
                  <strong>정식 안건</strong>
                  <span>
                    승격 당시 {proposal.formalizedSupportCount ?? proposal.supportThreshold}명
                    {proposal.supportCount !== proposal.formalizedSupportCount
                      ? ` · 현재 ${proposal.supportCount}명`
                      : ""}
                  </span>
                </>
              )}
            </div>
          </article>
        )) : (
          <p className="empty-state">{loading ? "제안을 불러오는 중…" : "조건에 맞는 제안이 없습니다."}</p>
        )}
      </section>

      <div className="pagination" aria-label="제안 목록 페이지">
        <button className="secondary-button" type="button" disabled={loading || page === 0} onClick={() => {
          setLoading(true);
          setPage((value) => value - 1);
        }}>이전</button>
        <span>{(result?.totalPages ?? 0) === 0 ? "0 / 0" : `${page + 1} / ${result?.totalPages}`}</span>
        <button className="secondary-button" type="button" disabled={loading || !result || page + 1 >= result.totalPages} onClick={() => {
          setLoading(true);
          setPage((value) => value + 1);
        }}>다음</button>
      </div>
    </>
  );
}
