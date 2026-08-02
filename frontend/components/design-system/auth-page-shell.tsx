import { AppShell } from "@astryxdesign/core/AppShell";
import { Button } from "@astryxdesign/core/Button";
import { Card } from "@astryxdesign/core/Card";
import { Center } from "@astryxdesign/core/Center";
import { Heading } from "@astryxdesign/core/Heading";
import { Icon } from "@astryxdesign/core/Icon";
import { HStack, VStack } from "@astryxdesign/core/Stack";
import { Text } from "@astryxdesign/core/Text";
import type { ReactNode } from "react";

export function AuthPageShell({
  title,
  description,
  children,
}: {
  title: string;
  description: string;
  children: ReactNode;
}) {
  return (
    <AppShell contentPadding={4} height="auto" variant="wash">
      <Center minHeight="85dvh" width="100%">
        <VStack className="auth-frame motion-reveal" gap={4}>
          <Card padding={8} width="100%" elevation="low">
            <VStack gap={5}>
              <VStack gap={2}>
                <Heading level={1}>{title}</Heading>
                <Text as="p" color="secondary">{description}</Text>
              </VStack>
              {children}
              <HStack>
                <Button
                  label="처음으로"
                  href="/"
                  variant="ghost"
                  size="sm"
                  icon={<Icon icon="chevronLeft" size="sm" color="secondary" />}
                />
              </HStack>
            </VStack>
          </Card>
        </VStack>
      </Center>
    </AppShell>
  );
}
