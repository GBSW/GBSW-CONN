import type { components, paths } from "@/lib/api-schema";

type CurrentUser = components["schemas"]["CurrentUserResponse"];

/** 생성된 OpenAPI 타입에서 파생시켜 백엔드 열거형이 바뀌면 컴파일이 깨지도록 한다. */
export type AccountRole = components["schemas"]["CreateAccountRequest"]["role"];
export type OfficeType = paths["/api/v1/admin/offices/{office}/users/{publicId}/end"]["post"]["parameters"]["path"]["office"];

/** 공식 역할 명칭. 화면마다 다르게 표기하지 않도록 이 파일에서만 정의한다. */
export const roleLabels: Record<AccountRole, string> = {
  STUDENT: "학생",
  TEACHER: "교사",
  SUPER_ADMIN: "슈퍼 어드민",
};

/** 공식 보직 명칭. 기능구현 명세서의 표기를 따른다. */
export const officeLabels: Record<OfficeType, string> = {
  STUDENT_AFFAIRS_TEACHER: "학생부장교사",
  STUDENT_COUNCIL_PRESIDENT: "학생회장",
  STUDENT_COUNCIL_VICE_PRESIDENT: "학생부회장",
};

/** 보직 임명에 필요한 기본 역할. */
export const officeRequiredRole: Record<OfficeType, AccountRole> = {
  STUDENT_AFFAIRS_TEACHER: "TEACHER",
  STUDENT_COUNCIL_PRESIDENT: "STUDENT",
  STUDENT_COUNCIL_VICE_PRESIDENT: "STUDENT",
};

export const roles = Object.keys(roleLabels) as AccountRole[];
export const offices = Object.keys(officeLabels) as OfficeType[];

/** 사건 심의를 맡는 세 보직. */
export const reviewerOffices: OfficeType[] = offices;

export function roleLabel(role: string): string {
  return roleLabels[role as AccountRole] ?? role;
}

export function officeLabel(office: string): string {
  return officeLabels[office as OfficeType] ?? office;
}

export function hasRole(user: CurrentUser | null, role: AccountRole): boolean {
  return user?.roles.includes(role) ?? false;
}

export function isStudent(user: CurrentUser | null): boolean {
  return hasRole(user, "STUDENT");
}

export function isTeacher(user: CurrentUser | null): boolean {
  return hasRole(user, "TEACHER");
}

export function isSuperAdmin(user: CurrentUser | null): boolean {
  return hasRole(user, "SUPER_ADMIN");
}

/** 세 고정 보직 중 하나라도 맡고 있으면 심의자다. */
export function isReviewer(user: CurrentUser | null): boolean {
  return user?.offices.some((office) => reviewerOffices.includes(office as OfficeType)) ?? false;
}
