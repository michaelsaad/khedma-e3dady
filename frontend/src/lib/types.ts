// Mirrors the backend DTOs.

export type Role = "USER" | "ADMIN" | "SUPER_ADMIN";
export type UserStatus = "PENDING" | "APPROVED" | "REJECTED";
export type Grade = "FIRST" | "SECOND" | "THIRD";
export type AttendanceKind = "SERVICE" | "MASS";
export type TransactionType =
  | "MANUAL_ADD"
  | "MANUAL_DEDUCT"
  | "RESET"
  | "ATTENDANCE_AWARD"
  | "ATTENDANCE_REVERSAL";
export type ManualKind = "ADD" | "DEDUCT" | "RESET";
export type DayOfWeek = "SATURDAY" | "SUNDAY" | "MONDAY" | "TUESDAY" | "WEDNESDAY" | "THURSDAY" | "FRIDAY";

export interface Page<T> {
  items: T[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
}

export interface User {
  id: number;
  email: string;
  emailVerified: boolean;
  displayName: string;
  avatarUrl: string | null;
  role: Role;
  status: UserStatus;
  studentId: number | null;
  createdAt: string;
  lastLoginAt: string | null;
}

export interface AuthResponse {
  accessToken: string;
  expiresAt: string;
  user: User;
}

export interface RegistrationResponse {
  email: string;
  codeExpiresAt: string;
}

export interface Student {
  id: number;
  name: string;
  grade: Grade;
  phone: string;
  groupId: number | null;
  groupName: string | null;
  pointsBalance: number;
  createdAt: string;
}

export interface Group {
  id: number;
  name: string;
  pointsBalance: number;
  memberCount: number;
}

export interface Transaction {
  id: number;
  studentId: number | null;
  studentName: string | null;
  groupId: number | null;
  groupName: string | null;
  type: TransactionType;
  amount: number;
  balanceAfter: number;
  notes: string;
  attendanceId: number | null;
  attendanceDate: string | null;
  attendanceKind: AttendanceKind | null;
  reversesId: number | null;
  createdBy: string | null;
  createdAt: string;
}

export interface Settings {
  massPoints: number;
  servicePoints: number;
  seasonStart: string;
  seasonEnd: string;
  meetingDay: DayOfWeek;
  updatedBy: string | null;
  updatedAt: string;
}

export interface AttendanceRow {
  studentId: number;
  studentName: string;
  grade: Grade;
  groupId: number | null;
  service: boolean;
  mass: boolean;
  recorded: boolean;
}

export interface MeetingAttendance {
  date: string;
  previousMeeting: string | null;
  nextMeeting: string | null;
  serviceCount: number;
  massCount: number;
  rows: AttendanceRow[];
}

export interface MeetingsResponse {
  meetings: string[];
  today: string;
}

export interface SaveAttendanceResult {
  date: string;
  saved: number;
  awardsCreated: number;
  reversalsCreated: number;
  netPoints: number;
}

export interface StudentCard {
  id: number;
  name: string;
  grade: Grade;
  groupId: number | null;
  groupName: string | null;
  points: number;
  rank: number;
  serviceCount: number;
  massCount: number;
  meetingsHeld: number;
  serviceRate: number;
  massRate: number;
}

export interface GroupStanding {
  id: number;
  name: string;
  points: number;
  rank: number;
  memberCount: number;
}

export interface DashboardSummary {
  totalStudents: number;
  totalGroups: number;
  meetingsHeld: number;
  lastMeeting: { date: string; serviceCount: number; massCount: number } | null;
  averageServiceRate: number;
  averageMassRate: number;
  topStudents: StudentCard[];
  groups: GroupStanding[];
}

export interface StudentDetail {
  card: StudentCard;
  attendance: { date: string; service: boolean; mass: boolean }[];
  recentTransactions: Transaction[];
}
