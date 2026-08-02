"use client";

import type { components } from "@/lib/api-schema";
import { apiDelete, apiGet, apiPost, errorMessage } from "@/lib/api-client";
import { Banner } from "@astryxdesign/core/Banner";
import { Button } from "@astryxdesign/core/Button";
import { EmptyState } from "@astryxdesign/core/EmptyState";
import { Heading } from "@astryxdesign/core/Heading";
import { List, ListItem } from "@astryxdesign/core/List";
import { Spinner } from "@astryxdesign/core/Spinner";
import { HStack, VStack } from "@astryxdesign/core/Stack";
import { Text } from "@astryxdesign/core/Text";
import { TextArea } from "@astryxdesign/core/TextArea";
import type { FormEvent } from "react";
import { useEffect, useState } from "react";

type Comment = components["schemas"]["ProposalCommentResponse"];
type CommentRequest = components["schemas"]["CreateProposalCommentRequest"];

const dateTimeFormatter = new Intl.DateTimeFormat("ko-KR", {
  timeZone: "Asia/Seoul",
  dateStyle: "medium",
  timeStyle: "short",
});

export function ProposalComments({ publicId, canComment }: { publicId: string; canComment: boolean }) {
  const [comments, setComments] = useState<Comment[]>([]);
  const [content, setContent] = useState("");
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [deletingId, setDeletingId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    apiGet<Comment[]>(`/api/v1/proposals/${publicId}/comments`)
      .then((response) => {
        if (active) setComments(response);
      })
      .catch((caught) => {
        if (active) setError(errorMessage(caught));
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => { active = false; };
  }, [publicId]);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      const request: CommentRequest = { content: content.trim() };
      const created = await apiPost<Comment>(`/api/v1/proposals/${publicId}/comments`, request);
      setComments((current) => [...current, created]);
      setContent("");
    } catch (caught) {
      setError(errorMessage(caught));
    } finally {
      setSubmitting(false);
    }
  }

  async function remove(commentPublicId: string) {
    setDeletingId(commentPublicId);
    setError(null);
    try {
      await apiDelete<void>(`/api/v1/proposals/${publicId}/comments/${commentPublicId}`);
      setComments((current) => current.filter((comment) => comment.publicId !== commentPublicId));
    } catch (caught) {
      setError(errorMessage(caught));
    } finally {
      setDeletingId(null);
    }
  }

  return (
    <VStack as="section" gap={4} aria-labelledby="comments-title">
      <VStack gap={1}>
        <Heading level={2} id="comments-title">학생 댓글</Heading>
        <Text as="p" color="secondary">제안의 보완점과 의견을 서로 존중하며 나눌 수 있습니다.</Text>
      </VStack>

      {canComment ? (
        <form onSubmit={(event) => void submit(event)}>
          <VStack gap={3}>
            <TextArea label="댓글" value={content} onChange={setContent} maxLength={2000} rows={4} isRequired width="100%" />
            <HStack hAlign="end">
              <Button label="댓글 등록" type="submit" variant="primary" isLoading={submitting} isDisabled={submitting || !content.trim()} />
            </HStack>
          </VStack>
        </form>
      ) : null}

      {error ? <Banner status="error" title="댓글 작업을 완료할 수 없습니다" description={error} /> : null}
      {loading ? <Spinner label="댓글을 불러오는 중…" /> : comments.length ? (
        <List hasDividers density="spacious">
          {comments.map((comment) => (
            <ListItem
              key={comment.publicId}
              label={
                <VStack gap={2}>
                  <HStack hAlign="between" gap={3} wrap="wrap">
                    <Heading level={3}>{comment.authorDisplayName}</Heading>
                    <Text type="supporting" color="secondary">{dateTimeFormatter.format(new Date(comment.createdAt))}</Text>
                  </HStack>
                  <Text as="p" className="pre-wrap wrap-anywhere">{comment.content}</Text>
                </VStack>
              }
              endContent={comment.viewerCanDelete ? (
                <Button label="댓글 삭제" variant="destructive" size="sm" isLoading={deletingId === comment.publicId} isDisabled={deletingId !== null} clickAction={() => remove(comment.publicId)} />
              ) : undefined}
            />
          ))}
        </List>
      ) : <EmptyState title="아직 댓글이 없습니다" description={canComment ? "첫 댓글로 의견을 남겨 보세요." : undefined} isCompact />}
    </VStack>
  );
}
