import { expect, test, type Browser, type Page } from "@playwright/test";
import { mkdir, readFile } from "node:fs/promises";
import { join } from "node:path";

type SeedData = {
  proposalId: string;
  proposalTitle: string;
  draftId: string;
  draftTitle: string;
  draftComment: string;
  contentCaseId: string;
  identityCaseId: string;
  password: string;
};
const stateDir = process.env.E2E_STATE_DIR ?? "";
const dataFile = process.env.E2E_DATA_FILE ?? "";

test("공개·인증 화면과 시스템 색상 모드가 동작한다", async ({ browser }) => {
  const context = await browser.newContext({ colorScheme: "dark", viewport: { width: 390, height: 844 } });
  const page = await context.newPage();
  await page.goto("/");
  await expect(page.getByRole("heading", { level: 1, name: "학생의 의견이 학교의 답변으로 이어지도록" })).toBeVisible();
  await expect(page.locator("html")).toHaveAttribute("lang", "ko");
  await assertNoHorizontalOverflow(page);
  await expect(page.getByRole("button", { name: "메뉴 열기" })).toBeVisible();
  await qaScreenshot(page, "home-mobile-dark.png");
  await page.setViewportSize({ width: 320, height: 844 });
  await assertNoHorizontalOverflow(page);
  await page.setViewportSize({ width: 390, height: 844 });
  for (const [path, heading] of [["/login", "로그인"], ["/activate", "계정 활성화"], ["/password-reset", "비밀번호 재설정"]] as const) {
    await page.goto(path);
    await expect(page.getByRole("heading", { level: 1, name: heading })).toBeVisible();
    await assertNoHorizontalOverflow(page);
  }
  await context.close();
});

test("학생이 제안 피드·작성·상세 기록을 확인한다", async ({ browser }) => {
  const data = await seedData();
  const page = await rolePage(browser, "student.json");
  await page.goto("/");
  await expect(page).toHaveURL(/\/dashboard$/);
  await expect(page.getByRole("heading", { level: 1, name: "E2E 학생 1님의 대시보드" })).toBeVisible();
  await page.goto("/proposals");
  await expect(page.getByRole("heading", { level: 1, name: "함께 바꿀 제안" })).toBeVisible();
  await expect(page.getByText(data.proposalTitle)).toBeVisible();
  await qaScreenshot(page, "proposals-desktop.png");

  // 필터와 정렬 선택지가 명세대로 제공되어야 한다.
  await page.getByRole("combobox", { name: "범위" }).click();
  await expect(page.getByRole("option")).toHaveText(["전체 제안", "정식 안건", "채택 안 됨"]);
  await page.keyboard.press("Escape");
  await page.getByRole("combobox", { name: "정렬" }).click();
  await expect(page.getByRole("option")).toHaveText(["최신순", "날짜순", "동의 많은 순", "동의 적은 순"]);
  await page.keyboard.press("Escape");

  await page.setViewportSize({ width: 390, height: 844 });
  await expect(page.getByTestId("proposal-support-mobile").first()).toBeVisible();
  await assertNoHorizontalOverflow(page);
  await qaScreenshot(page, "proposals-mobile.png");
  await page.goto("/proposals/new");
  await expect(page.getByRole("heading", { level: 1, name: "공개 제안 작성" })).toBeVisible();
  await page.goto(`/proposals/${data.proposalId}`);
  await expect(page.getByRole("heading", { level: 1, name: data.proposalTitle })).toBeVisible();
  await expect(page.getByText("학교 공식 답변과 실행 현황")).toBeVisible();
  await expect(page.getByText("공식 답변과 실행 결과를 확인했습니다.")).toBeVisible();

  // 진행 이력은 전이 시점의 유효 동의 수를 숫자로 보여야 한다.
  // 정식 안건 전환·검토 시작·공식 답변을 거친 제안에서 확인한다.
  const statusHistory = page.getByRole("region", { name: "진행 이력" });
  await expect(statusHistory).toBeVisible();
  const historyText = await statusHistory.innerText();
  expect(historyText).toMatch(/전환 당시 유효 동의 \d+명/);
  for (const placeholder of ["null", "undefined", "NaN"]) {
    expect(historyText, `진행 이력에 ${placeholder}가 노출됨`).not.toContain(placeholder);
  }

  await page.goto(`/proposals/${data.draftId}`);
  await expect(page.getByRole("heading", { level: 1, name: data.draftTitle })).toBeVisible();
  await expect(page.getByText(data.draftComment)).toBeVisible();
  await expect(page.getByRole("link", { name: "제안 수정" })).toBeVisible();
  await page.goto(`/proposals/${data.draftId}/edit`);
  await expect(page.getByRole("heading", { level: 1, name: "제안 수정" })).toBeVisible();
  await page.getByLabel("제목").fill("학생 휴게 공간 이용 원칙 수정안");
  await page.getByRole("button", { name: "수정 저장" }).click();
  await expect(page.getByRole("heading", { level: 1, name: "학생 휴게 공간 이용 원칙 수정안" })).toBeVisible();
});

test("교사·관리자 업무 화면이 실제 기록을 표시한다", async ({ browser }) => {
  const data = await seedData();
  const teacher = await rolePage(browser, "teacher.json");
  await teacher.goto("/dashboard");
  await expect(teacher.getByRole("heading", { level: 1, name: "E2E 담당 교사님의 대시보드" })).toBeVisible();
  await expect(teacher.getByRole("link", { name: "공개 제안 작성" })).toHaveCount(0);
  await teacher.goto(`/proposals/${data.proposalId}`);
  await expect(teacher.getByText("예약 현황 공개 기능 적용을 완료했습니다.")).toBeVisible();

  const admin = await rolePage(browser, "admin.json");
  await admin.goto("/admin");
  await expect(admin.getByRole("heading", { level: 1, name: "계정 관리" })).toBeVisible();
  await expect(admin.getByRole("heading", { level: 2, name: "새 계정 만들기" })).toHaveCount(0);
  await admin.goto("/admin/accounts/new");
  await expect(admin.getByRole("heading", { level: 1, name: "새 계정 만들기" })).toBeVisible();
  await admin.goto("/admin/offices");
  await expect(admin.getByRole("heading", { level: 1, name: "보직 관리" })).toBeVisible();
  await admin.goto("/admin/proposals");
  await expect(admin.getByRole("heading", { level: 1, name: "정식 안건 담당 지정" })).toBeVisible();
  await expect(admin.getByText(data.proposalTitle)).toBeVisible();
  await qaScreenshot(admin, "admin-proposals-desktop.png");
});

test("고정 심의자가 승인 사건을 보고 학생부장이 신원을 한 번만 확인한다", async ({ browser }) => {
  const data = await seedData();
  const president = await rolePage(browser, "president.json");
  await president.goto("/moderation");
  await expect(president.getByRole("heading", { level: 1, name: "보호 심의" })).toBeVisible();
  await president.goto(`/moderation/${data.identityCaseId}`);
  await expect(president.getByRole("img", { name: "승인" })).toBeVisible();
  await qaScreenshot(president, "moderation-approved-desktop.png");

  const affairs = await rolePage(browser, "affairs.json");
  await affairs.goto(`/moderation/${data.identityCaseId}`);
  await affairs.getByLabel("현재 비밀번호").fill(data.password);
  await affairs.getByLabel("확인 사유").fill("Playwright E2E 일회 확인 검증");
  await affairs.getByRole("button", { name: "재인증하고 한 번 확인" }).click();
  await expect(affairs.getByRole("heading", { name: /확인된 작성자/ })).toBeVisible();
  await expect(affairs.getByText("e2e.student.00")).toBeVisible();
});

async function rolePage(browser: Browser, stateName: string): Promise<Page> {
  const context = await browser.newContext({ storageState: join(stateDir, stateName) });
  return await context.newPage();
}

async function seedData(): Promise<SeedData> {
  return JSON.parse(await readFile(dataFile, "utf8")) as SeedData;
}

async function qaScreenshot(page: Page, name: string) {
  const directory = join(process.cwd(), "test-results", "qa");
  await mkdir(directory, { recursive: true });
  await page.screenshot({ path: join(directory, name), fullPage: true });
}

async function assertNoHorizontalOverflow(page: Page) {
  await expect.poll(async () => await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
}
