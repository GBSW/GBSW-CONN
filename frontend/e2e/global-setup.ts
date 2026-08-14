import { request, type APIRequestContext, type FullConfig } from "@playwright/test";
import { mkdir, readFile, writeFile } from "node:fs/promises";
import { join } from "node:path";

const password = "E2E-Strong-Password-2026!";

export default async function globalSetup(config: FullConfig) {
  const baseURL = String(config.projects[0]?.use.baseURL ?? process.env.E2E_BASE_URL);
  const bootstrapFile = required("E2E_BOOTSTRAP_FILE");
  const dataFile = required("E2E_DATA_FILE");
  const stateDir = required("E2E_STATE_DIR");
  const deliveryDir = required("E2E_DELIVERY_DIR");
  await mkdir(stateDir, { recursive: true, mode: 0o700 });

  const bootstrap = Object.fromEntries((await readFile(bootstrapFile, "utf8")).trim().split("\n").map((line) => line.split("=", 2)));
  const admin = await request.newContext({ baseURL });
  await write(admin, "POST", "/api/v1/auth/activate", { loginId: bootstrap.loginId, activationCode: bootstrap.activationCode, password });
  await write(admin, "POST", "/api/v1/auth/login", { loginId: bootstrap.loginId, password });
  await admin.storageState({ path: join(stateDir, "admin.json") });
  const approver = await createBootstrapApprover(admin, baseURL, deliveryDir);

  const actors: Array<{ context: APIRequestContext; loginId: string; publicId: string }> = [];
  for (let index = 0; index < 50; index += 1) {
    const loginId = `e2e.student.${String(index).padStart(2, "0")}`;
    actors.push(await createActor(admin, approver.context, baseURL, deliveryDir, { loginId, displayName: `E2E 학생 ${index + 1}`, role: "STUDENT" }));
  }
  const affairs = await createActor(admin, approver.context, baseURL, deliveryDir, { loginId: "e2e.teacher.affairs", displayName: "E2E 학생부장", role: "TEACHER" });
  const teacher = await createActor(admin, approver.context, baseURL, deliveryDir, { loginId: "e2e.teacher.assignee", displayName: "E2E 담당 교사", role: "TEACHER" });

  await executeGovernedChange(admin, approver.context, appointment("STUDENT_AFFAIRS_TEACHER", affairs.publicId, "E2E 학생부장 임명"));
  await executeGovernedChange(admin, approver.context, appointment("STUDENT_COUNCIL_PRESIDENT", actors[1]!.publicId, "E2E 학생회장 임명"));
  await executeGovernedChange(admin, approver.context, appointment("STUDENT_COUNCIL_VICE_PRESIDENT", actors[2]!.publicId, "E2E 학생부회장 임명"));
  for (const actor of [affairs, actors[1]!, actors[2]!]) {
    await write(actor.context, "POST", "/api/v1/auth/login", { loginId: actor.loginId, password });
  }

  const proposalTitle = "기숙사 공용 공간 예약 현황 공개 제안";
  const proposal = await write<{ publicId: string }>(actors[0]!.context, "POST", "/api/v1/proposals", {
    title: proposalTitle,
    content: "공용 공간을 공정하게 이용할 수 있도록 예약 현황과 이용 원칙을 학생들이 확인할 수 있게 해 주세요.",
    authorVisibility: "ANONYMOUS",
  });
  await write(actors[0]!.context, "PUT", `/api/v1/proposals/${proposal.publicId}/support`);
  for (let start = 1; start < actors.length; start += 8) {
    await Promise.all(actors.slice(start, start + 8).map((actor) => write(actor.context, "PUT", `/api/v1/proposals/${proposal.publicId}/support`)));
  }

  await write(admin, "POST", `/api/v1/admin/proposals/${proposal.publicId}/assignments`, { teacherPublicId: teacher.publicId, reason: "E2E 담당 지정" });
  await write(teacher.context, "POST", `/api/v1/proposals/${proposal.publicId}/review-start`, { reason: "실행 가능성을 검토합니다." });
  await write(teacher.context, "POST", `/api/v1/proposals/${proposal.publicId}/decisions/accept`, response("제안을 채택합니다.", "학생 이용 편의를 개선할 수 있습니다."));
  await write(teacher.context, "POST", `/api/v1/proposals/${proposal.publicId}/execution-start`, response("예약 현황 공개 기능을 적용하고 있습니다.", "담당 부서 구현을 시작했습니다."));
  await write(teacher.context, "POST", `/api/v1/proposals/${proposal.publicId}/execution-complete`, response("예약 현황 공개 기능 적용을 완료했습니다.", "학생 대상 확인을 마쳤습니다."));
  await write(actors[1]!.context, "POST", `/api/v1/proposals/${proposal.publicId}/comments`, { content: "공식 답변과 실행 결과를 확인했습니다." });

  const draftTitle = "학생 휴게 공간 이용 원칙 제안";
  const draft = await write<{ publicId: string }>(actors[0]!.context, "POST", "/api/v1/proposals", {
    title: draftTitle,
    content: "학생 휴게 공간을 함께 사용할 수 있도록 기본 이용 원칙을 공개해 주세요.",
    authorVisibility: "NAMED",
  });
  const draftComment = "점심시간 이용 기준도 함께 논의하면 좋겠습니다.";
  await write(actors[1]!.context, "POST", `/api/v1/proposals/${draft.publicId}/comments`, { content: draftComment });

  const report = await write<{ publicId: string }>(actors[1]!.context, "POST", `/api/v1/proposals/${proposal.publicId}/reports`, { reason: "익명 보호 절차 E2E 검증용 신고입니다." });
  const contentCase = await write<{ publicId: string }>(affairs.context, "POST", `/api/v1/moderation/reports/${report.publicId}/cases`, { caseType: "CONTENT_VISIBILITY", reason: "공개 제한 필요성을 별도 검토합니다." });
  const identityCase = await write<{ publicId: string }>(affairs.context, "POST", `/api/v1/moderation/reports/${report.publicId}/cases`, { caseType: "IDENTITY_REVEAL", reason: "신원 확인 필요성을 별도 검토합니다." });
  for (const actor of [affairs, actors[1]!, actors[2]!]) {
    await write(actor.context, "POST", `/api/v1/moderation/cases/${identityCase.publicId}/votes/approve`, { reason: "보호 절차 E2E 검증을 위해 승인합니다." });
  }

  await Promise.all([
    actors[0]!.context.storageState({ path: join(stateDir, "student.json") }),
    actors[1]!.context.storageState({ path: join(stateDir, "president.json") }),
    actors[2]!.context.storageState({ path: join(stateDir, "vice-president.json") }),
    affairs.context.storageState({ path: join(stateDir, "affairs.json") }),
    teacher.context.storageState({ path: join(stateDir, "teacher.json") }),
  ]);
  await writeFile(dataFile, JSON.stringify({
    proposalId: proposal.publicId,
    proposalTitle,
    draftId: draft.publicId,
    draftTitle,
    draftComment,
    contentCaseId: contentCase.publicId,
    identityCaseId: identityCase.publicId,
    password,
  }, null, 2), { mode: 0o600 });
  await Promise.all([...actors.map((actor) => actor.context.dispose()), affairs.context.dispose(), teacher.context.dispose(), approver.context.dispose(), admin.dispose()]);
}

async function createBootstrapApprover(admin: APIRequestContext, baseURL: string, deliveryDir: string) {
  const actor = { loginId: "e2e.admin.approver", displayName: "E2E 승인 관리자", role: "SUPER_ADMIN" as const };
  const change = await requestGovernedChange(admin, { changeType: "CREATE_ACCOUNT", ...actor, reason: "초기 2인 승인 정족수 구성" });
  await approveGovernedChange(admin, change.publicId);
  const delivery = await deliveredCredential(deliveryDir, actor.loginId);
  const context = await request.newContext({ baseURL });
  await write(context, "POST", "/api/v1/auth/activate", { loginId: actor.loginId, activationCode: delivery.oneTimeCode, password });
  await write(context, "POST", "/api/v1/auth/login", { loginId: actor.loginId, password });
  return { context, loginId: actor.loginId, publicId: delivery.userPublicId };
}

async function createActor(
  requester: APIRequestContext,
  approver: APIRequestContext,
  baseURL: string,
  deliveryDir: string,
  actor: { loginId: string; displayName: string; role: "STUDENT" | "TEACHER" },
) {
  const change = await requestGovernedChange(requester, { changeType: "CREATE_ACCOUNT", ...actor, reason: "Playwright E2E 격리 데이터" });
  await approveGovernedChange(approver, change.publicId);
  const delivery = await deliveredCredential(deliveryDir, actor.loginId);
  const context = await request.newContext({ baseURL });
  await write(context, "POST", "/api/v1/auth/activate", { loginId: actor.loginId, activationCode: delivery.oneTimeCode, password });
  await write(context, "POST", "/api/v1/auth/login", { loginId: actor.loginId, password });
  return { context, loginId: actor.loginId, publicId: delivery.userPublicId };
}

function appointment(office: string, targetUserPublicId: string, reason: string) {
  return { changeType: "APPOINT_OFFICE", office, targetUserPublicId, replaceExistingAtStart: false, reason };
}

async function executeGovernedChange(requester: APIRequestContext, approver: APIRequestContext, change: unknown) {
  const requested = await requestGovernedChange(requester, change);
  return await approveGovernedChange(approver, requested.publicId);
}

async function requestGovernedChange(context: APIRequestContext, change: unknown) {
  return await write<{ publicId: string }>(context, "POST", "/api/v1/admin/governance/requests", change);
}

async function approveGovernedChange(context: APIRequestContext, publicId: string) {
  return await write(context, "POST", `/api/v1/admin/governance/requests/${publicId}/approve`, { reason: "E2E 독립 승인" });
}

async function deliveredCredential(deliveryDir: string, loginId: string) {
  const value = await readFile(join(deliveryDir, `${encodeURIComponent(loginId)}.json`), "utf8");
  return JSON.parse(value) as { userPublicId: string; oneTimeCode: string };
}

function response(content: string, decisionReason: string) {
  return { content, decisionReason, followUpPlan: "진행 상황을 제안 상세에 계속 기록합니다." };
}

async function write<T = unknown>(context: APIRequestContext, method: "POST" | "PUT" | "DELETE", path: string, data?: unknown): Promise<T> {
  const csrfResponse = await context.get("/api/v1/auth/csrf");
  if (!csrfResponse.ok()) throw new Error(`CSRF request failed: ${csrfResponse.status()} ${await csrfResponse.text()}`);
  const csrf = await csrfResponse.json() as { headerName: string; token: string };
  const response = await context.fetch(path, { method, data, headers: { [csrf.headerName]: csrf.token, Accept: "application/json" } });
  if (!response.ok()) throw new Error(`${method} ${path} failed: ${response.status()} ${await response.text()}`);
  return response.status() === 204 ? undefined as T : await response.json() as T;
}

function required(name: string): string {
  const value = process.env[name];
  if (!value) throw new Error(`${name} is required`);
  return value;
}
