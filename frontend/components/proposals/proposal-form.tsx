"use client";

import type { components } from "@/lib/api-schema";
import { ApiRequestError, apiGet, apiPost, apiPut, errorMessage } from "@/lib/api-client";
import { Banner } from "@astryxdesign/core/Banner";
import { Button } from "@astryxdesign/core/Button";
import { EmptyState } from "@astryxdesign/core/EmptyState";
import { FormLayout } from "@astryxdesign/core/FormLayout";
import { Heading } from "@astryxdesign/core/Heading";
import { RadioList, RadioListItem } from "@astryxdesign/core/RadioList";
import { Spinner } from "@astryxdesign/core/Spinner";
import { HStack, VStack } from "@astryxdesign/core/Stack";
import { Text } from "@astryxdesign/core/Text";
import { TextArea } from "@astryxdesign/core/TextArea";
import { TextInput } from "@astryxdesign/core/TextInput";
import { useRouter } from "next/navigation";
import type { FormEvent } from "react";
import { useEffect, useState } from "react";

type CurrentUser = components["schemas"]["CurrentUserResponse"];
type CreateProposalRequest = components["schemas"]["CreateProposalRequest"];
type UpdateProposalRequest = components["schemas"]["UpdateProposalRequest"];
type ProposalDetail = components["schemas"]["ProposalDetailResponse"];

export function ProposalForm({ publicId }: { publicId?: string }) {
  const router = useRouter();
  const editing = publicId !== undefined;
  const [access, setAccess] = useState<"loading" | "ready" | "signed-out" | "forbidden" | "not-found" | "error">("loading");
  const [title, setTitle] = useState("");
  const [content, setContent] = useState("");
  const [authorVisibility, setAuthorVisibility] = useState<CreateProposalRequest["authorVisibility"]>("ANONYMOUS");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    const proposalRequest = publicId
      ? apiGet<ProposalDetail>(`/api/v1/proposals/${publicId}`)
      : Promise.resolve(null);
    Promise.all([apiGet<CurrentUser>("/api/v1/auth/me"), proposalRequest])
      .then(([user, proposal]) => {
        if (!active) return;
        if (!user.roles.includes("STUDENT") || (proposal && !proposal.viewerCanEdit)) {
          setAccess("forbidden");
          return;
        }
        if (proposal) {
          setTitle(proposal.title);
          setContent(proposal.content);
          setAuthorVisibility(proposal.authorVisibility as CreateProposalRequest["authorVisibility"]);
        }
        setAccess("ready");
      })
      .catch((caught: unknown) => {
        if (!active) return;
        if (caught instanceof ApiRequestError && caught.code === "PROPOSAL_NOT_FOUND") setAccess("not-found");
        else if (caught instanceof ApiRequestError && ["AUTHENTICATION_REQUIRED", "SESSION_INVALIDATED"].includes(caught.code)) setAccess("signed-out");
        else setAccess("error");
      });
    return () => { active = false; };
  }, [publicId]);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      const saved = editing
        ? await apiPut<ProposalDetail>(`/api/v1/proposals/${publicId}`, {
            title: title.trim(),
            content: content.trim(),
          } satisfies UpdateProposalRequest)
        : await apiPost<ProposalDetail>("/api/v1/proposals", {
            title: title.trim(),
            content: content.trim(),
            authorVisibility,
          } satisfies CreateProposalRequest);
      router.replace(`/proposals/${saved.publicId}`);
      router.refresh();
    } catch (caught) {
      setError(errorMessage(caught));
      setSubmitting(false);
    }
  }

  if (access === "loading") return <Spinner size="lg" label={editing ? "수정 권한을 확인하고 있습니다…" : "작성 권한을 확인하고 있습니다…"} />;
  if (access === "signed-out") return <EmptyState title="로그인이 필요합니다" description="학생 계정으로 로그인하면 공개 제안을 작성할 수 있습니다." actions={<Button label="로그인" href="/login" variant="primary" />} headingLevel={1} />;
  if (access === "not-found") return <EmptyState title="제안을 찾을 수 없습니다" actions={<Button label="제안 목록으로" href="/proposals" />} headingLevel={1} />;
  if (access === "forbidden") return <EmptyState title={editing ? "이 제안은 수정할 수 없습니다" : "학생만 제안을 작성할 수 있습니다"} description={editing ? "작성자가 아니거나 이미 정식 안건이 된 제안입니다." : undefined} actions={<Button label="제안 목록으로" href="/proposals" />} headingLevel={1} />;
  if (access === "error") return <Banner status="error" title="권한을 확인하지 못했습니다" description="잠시 후 다시 시도해 주세요." />;

  return (
    <VStack as="section" gap={6} aria-labelledby="compose-title">
      <VStack gap={3} className="reading-measure">
        <Button label={editing ? "제안으로 돌아가기" : "제안 목록으로"} href={editing ? `/proposals/${publicId}` : "/proposals"} variant="ghost" />
        <Heading level={1} id="compose-title">{editing ? "제안 수정" : "공개 제안 작성"}</Heading>
        <Text as="p" color="secondary">
          {editing
            ? "동의 모집 중에는 제목과 내용을 고칠 수 있습니다. 작성자 표시 방식과 이미 받은 동의는 유지됩니다."
            : "등록한 내용은 학생들에게 공개되며 작성자의 동의 1표가 자동으로 포함됩니다. 개인 고충이나 안전 제보는 이 양식에 작성하지 마세요."}
        </Text>
      </VStack>
      {editing
        ? <Banner status="info" title="수정 이력은 감사 기록에 남습니다" description="정식 안건으로 승격된 뒤에는 제안을 수정하거나 철회할 수 없습니다." />
        : <Banner status="warning" title="공개해도 되는 내용인지 확인하세요" description="개인정보, 학교폭력, 자해 위험, 성희롱·차별 피해는 학교가 안내한 보호 채널로 전달해야 합니다." />}
      <form onSubmit={(event) => void submit(event)}>
        <VStack gap={5}>
          <FormLayout>
            <TextInput label="제목" value={title} onChange={setTitle} htmlName="title" isRequired width="100%" />
            <TextArea label="내용" description="첨부파일과 HTML 실행은 지원하지 않습니다." value={content} onChange={setContent} htmlName="content" maxLength={10000} rows={12} isRequired width="100%" />
          </FormLayout>
          {!editing ? (
            <RadioList label="작성자 표시" value={authorVisibility} onChange={(value) => setAuthorVisibility(value as CreateProposalRequest["authorVisibility"])} htmlName="authorVisibility" isRequired>
              <RadioListItem label="익명으로 공개" value="ANONYMOUS" description="다른 학생과 교사에게 이름을 표시하지 않습니다." />
              <RadioListItem label="이름 공개" value="NAMED" description="현재 계정의 표시 이름을 함께 공개합니다." />
            </RadioList>
          ) : null}
          <Text type="supporting" as="p" color="secondary">
            익명을 선택해도 계정 남용 대응을 위한 작성자 연결정보는 암호화된 보호 영역에 분리 보관됩니다.
          </Text>
          {error ? <Banner status="error" title={editing ? "제안을 수정할 수 없습니다" : "제안을 등록할 수 없습니다"} description={error} /> : null}
          <HStack gap={2} hAlign="end" wrap="wrap">
            <Button label="취소" href={editing ? `/proposals/${publicId}` : "/proposals"} variant="ghost" />
            <Button label={editing ? "수정 저장" : "제안 등록"} type="submit" variant="primary" isLoading={submitting} isDisabled={submitting || !title.trim() || !content.trim()} />
          </HStack>
        </VStack>
      </form>
    </VStack>
  );
}
