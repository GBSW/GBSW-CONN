import { expect, test, type Page } from "@playwright/test";

/**
 * 앱 셸의 역할별 노출 규칙과 랜딩 앵커를 검증한다.
 * 화면 조립 규칙만 다루므로 `/api/v1/auth/me`를 스텁해 역할별 경우를 모두 확인한다.
 * 서버가 실제로 데이터를 막는지는 백엔드 통합 테스트와 `product-flows`에서 검증한다.
 */

const staffMenuLabels = [
  "업무 도구",
  "새 계정 만들기",
  "보직 관리",
  "정식 안건 담당 지정",
  "심의 사건",
];

const staffPaths = ["/admin", "/admin/offices", "/admin/proposals", "/admin/accounts/new", "/moderation"];

async function signedOut(page: Page) {
  await page.route("**/api/v1/auth/me", (route) =>
    route.fulfill({
      status: 401,
      contentType: "application/json",
      body: JSON.stringify({
        code: "AUTHENTICATION_REQUIRED",
        message: "로그인이 필요합니다.",
        timestamp: new Date().toISOString(),
        traceId: "e2e-public-shell",
      }),
    }),
  );
}

async function signedInAs(page: Page, roles: string[], offices: string[] = []) {
  const now = new Date();
  await page.route("**/api/v1/auth/me", (route) =>
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        publicId: "00000000-0000-4000-8000-000000000001",
        loginId: "shell.fixture",
        displayName: "셸 검증 사용자",
        roles,
        offices,
        reauthenticatedAt: now.toISOString(),
        reauthenticationExpiresAt: new Date(now.getTime() + 600_000).toISOString(),
      }),
    }),
  );
}

async function expectNoStaffMenu(page: Page, path: string) {
  const body = await page.locator("body").innerText();
  for (const label of staffMenuLabels) {
    expect(body, `${path}에서 "${label}"가 노출됨`).not.toContain(label);
  }
}

test("랜딩 상단 서비스 안내가 진행 방식 섹션으로 스크롤한다", async ({ page }) => {
  await signedOut(page);
  await page.goto("/proposals");

  await page.getByRole("link", { name: "서비스 안내" }).click();
  await expect(page).toHaveURL(/#process$/);

  const target = page.locator("#process");
  await expect(target).toBeVisible();

  // URL만 바뀌는 것은 통과가 아니다. 대상 섹션이 실제로 화면 안으로 들어와야 한다.
  await expect
    .poll(async () => page.evaluate(() => Math.round(window.scrollY)), { timeout: 5_000 })
    .toBeGreaterThan(100);

  const box = await target.boundingBox();
  const viewport = page.viewportSize();
  expect(box).not.toBeNull();
  expect(box!.y).toBeLessThan(viewport!.height);
});

test("라우트 전환에서 scroll-behavior 경고가 나오지 않는다", async ({ page }) => {
  const warnings: string[] = [];
  page.on("console", (message) => {
    if (message.text().includes("scroll-behavior")) warnings.push(message.text());
  });

  await signedOut(page);
  await page.goto("/");
  await expect(page.locator("html")).toHaveAttribute("data-scroll-behavior", "smooth");

  await page.getByRole("link", { name: "제안 보기" }).first().click();
  await expect(page).toHaveURL(/\/proposals$/);
  await page.goBack();
  await expect(page.locator("#process")).toBeVisible();

  expect(warnings).toEqual([]);
});

for (const path of staffPaths) {
  test(`비로그인 상태의 ${path}에 업무 메뉴가 렌더되지 않는다`, async ({ page }) => {
    await signedOut(page);
    await page.goto(path);
    await expect(page.getByRole("heading", { name: "로그인이 필요합니다" })).toBeVisible();
    // 상단바에도 로그인 링크가 있으므로 안내 화면 본문으로 한정한다.
    await expect(page.getByRole("main").getByRole("link", { name: "로그인" })).toBeVisible();
    await expectNoStaffMenu(page, path);
  });

  test(`학생 계정의 ${path}에 업무 메뉴가 렌더되지 않는다`, async ({ page }) => {
    await signedInAs(page, ["STUDENT"]);
    await page.goto(path);
    await expect(page.getByRole("heading", { name: "접근 권한이 없습니다" })).toBeVisible();
    await expect(page.getByRole("link", { name: "내 대시보드" })).toBeVisible();
    await expectNoStaffMenu(page, path);
  });
}

test("심의자에게는 보호 심의 메뉴만 보이고 관리 메뉴는 보이지 않는다", async ({ page }) => {
  await signedInAs(page, ["TEACHER"], ["STUDENT_AFFAIRS_TEACHER"]);
  await page.goto("/moderation");

  await expect(page.getByRole("link", { name: "심의 사건" })).toBeVisible();
  const body = await page.locator("body").innerText();
  for (const label of ["새 계정 만들기", "보직 관리", "정식 안건 담당 지정"]) {
    expect(body, `심의자에게 "${label}"가 노출됨`).not.toContain(label);
  }
});

test("슈퍼 어드민에게는 관리 메뉴만 보이고 보호 심의 메뉴는 보이지 않는다", async ({ page }) => {
  await signedInAs(page, ["SUPER_ADMIN"]);
  await page.goto("/admin");

  await expect(page.getByRole("link", { name: "보직 관리" })).toBeVisible();
  await expect(page.getByRole("link", { name: "정식 안건 담당 지정" })).toBeVisible();
  const body = await page.locator("body").innerText();
  expect(body, "슈퍼 어드민에게 보호 심의 메뉴가 노출됨").not.toContain("심의 사건");
});

test("슈퍼 어드민 전용 계정은 대시보드에서 관리 화면으로 이동한다", async ({ page }) => {
  await signedInAs(page, ["SUPER_ADMIN"]);
  await page.goto("/dashboard");
  await expect(page).toHaveURL(/\/admin$/);
});

test("학생 역할을 겸한 슈퍼 어드민은 공통 대시보드에 남는다", async ({ page }) => {
  await signedInAs(page, ["SUPER_ADMIN", "STUDENT"]);
  await page.goto("/dashboard");
  await expect(page).toHaveURL(/\/dashboard$/);
  await expect(page.getByRole("heading", { name: /님의 대시보드$/ })).toBeVisible();
});
