import { expect, test, type Page } from "@playwright/test";

const proposalId = "00000000-0000-4000-8000-000000000099";

async function signedInStudent(page: Page) {
  await page.route("**/api/v1/auth/me", (route) => route.fulfill({
    status: 200,
    contentType: "application/json",
    body: JSON.stringify({
      publicId: "00000000-0000-4000-8000-000000000001",
      loginId: "resilience.student",
      displayName: "복구 검증 학생",
      roles: ["STUDENT"],
      offices: [],
      reauthenticatedAt: new Date().toISOString(),
      reauthenticationExpiresAt: new Date(Date.now() + 600_000).toISOString(),
    }),
  }));
}

function proposalBody() {
  return {
    publicId: proposalId,
    title: "복구 가능한 제안 상세",
    content: "일시적 오류 뒤 다시 표시되어야 하는 제안입니다.",
    authorVisibility: "ANONYMOUS",
    workflowStatus: "GATHERING_SUPPORT",
    visibilityStatus: "VISIBLE",
    supportCount: 3,
    supportThreshold: 50,
    viewerSupported: false,
    viewerCanEdit: false,
    viewerCanManage: false,
    formalizedAt: null,
    formalizedSupportCount: null,
    createdAt: new Date().toISOString(),
    officialResponses: [],
    statusHistory: [],
  };
}

test("제안 상세의 일시적 오류를 다시 시도로 복구한다", async ({ page }) => {
  await signedInStudent(page);
  let attempts = 0;
  await page.route(`**/api/v1/proposals/${proposalId}`, (route) => {
    attempts += 1;
    if (attempts === 1) {
      return route.fulfill({
        status: 500,
        contentType: "application/json",
        body: JSON.stringify({ code: "INTERNAL_ERROR", message: "일시적 오류", timestamp: new Date().toISOString(), traceId: "resilience" }),
      });
    }
    return route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(proposalBody()) });
  });

  await page.goto(`/proposals/${proposalId}`);
  await expect(page.getByRole("heading", { name: "제안을 불러오지 못했습니다" })).toBeVisible();
  await page.getByRole("button", { name: "다시 시도" }).click();
  await expect(page.getByRole("heading", { level: 1, name: "복구 가능한 제안 상세" })).toBeVisible();
});

test("필수 입력 표기를 한국어로 제공하면서 aria-required를 유지한다", async ({ page }) => {
  await signedInStudent(page);
  await page.route(`**/api/v1/proposals/${proposalId}`, (route) => route.fulfill({
    status: 200,
    contentType: "application/json",
    body: JSON.stringify(proposalBody()),
  }));

  await page.goto(`/proposals/${proposalId}`);
  await page.getByRole("button", { name: "신고 사유 작성" }).click();
  await expect(page.getByText("Required", { exact: true })).toHaveCount(0);
  await expect(page.getByText(/신고 사유.*필수/)).toBeVisible();
  await expect(page.getByLabel("신고 사유")).toHaveAttribute("aria-required", "true");
});
