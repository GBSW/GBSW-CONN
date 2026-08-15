import { StatusDot } from "@astryxdesign/core/StatusDot";
import { HStack } from "@astryxdesign/core/Stack";
import { Text } from "@astryxdesign/core/Text";

export function CredentialDeliveryStatus({ status }: { status?: string | null }) {
  if (!status) return <Text type="supporting" color="secondary">자격증명 전달 없음</Text>;
  const delivered = status === "DELIVERED";
  return (
    <HStack gap={1} vAlign="center">
      <StatusDot variant={delivered ? "success" : "warning"} label={delivered ? "수신자 전달 완료" : "전달 확인 필요"} />
      <Text type="supporting">{delivered ? "수신자 전달 완료" : status}</Text>
    </HStack>
  );
}
