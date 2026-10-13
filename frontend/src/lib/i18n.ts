import { ApiError } from "./api";
import type { AttendanceKind, DayOfWeek, Grade, Role, Transaction, TransactionType, UserStatus } from "./types";

/** Arabic text for backend ErrorCode values. */
const ERRORS: Record<string, string> = {
  NETWORK_ERROR: "تعذّر الاتصال بالسيرفر، تأكد من الإنترنت وحاول مرة أخرى",
  UNKNOWN: "حصل خطأ غير متوقع",
  VALIDATION_FAILED: "راجع البيانات المدخلة",
  MALFORMED_REQUEST: "طلب غير صالح",
  DATA_CONFLICT: "البيانات تتعارض مع بيانات موجودة",
  UNAUTHENTICATED: "سجّل الدخول أولًا",
  ACCESS_DENIED: "ليس لديك صلاحية لهذه العملية",
  INVALID_CREDENTIALS: "الإيميل أو كلمة السر غير صحيحة",
  EMAIL_ALREADY_REGISTERED: "الإيميل مسجّل بالفعل، سجّل الدخول",
  EMAIL_NOT_VERIFIED: "لازم تأكد إيميلك الأول",
  EMAIL_ALREADY_VERIFIED: "الإيميل متأكد بالفعل، سجّل الدخول",
  INVALID_VERIFICATION_CODE: "الكود غير صحيح",
  VERIFICATION_CODE_EXPIRED: "الكود انتهى، اطلب كود جديد",
  TOO_MANY_ATTEMPTS: "محاولات كتير، اطلب كود جديد",
  RESEND_TOO_SOON: "استنى دقيقة قبل ما تطلب كود جديد",
  EMAIL_SEND_FAILED: "معرفناش نبعت كود التأكيد دلوقتي، حاول تاني بعد شوية",
  GOOGLE_LOGIN_DISABLED: "الدخول بجوجل غير مفعّل حاليًا",
  GOOGLE_TOKEN_INVALID: "تعذّر التحقق من حساب جوجل",
  GOOGLE_EMAIL_NOT_VERIFIED: "إيميل جوجل غير مؤكد",
  USER_NOT_FOUND: "المستخدم غير موجود",
  CANNOT_CHANGE_OWN_ACCESS: "لا يمكنك تغيير صلاحية حسابك",
  CANNOT_CHANGE_OWNER_ACCESS: "لا يمكن تغيير صلاحية حساب المالك",
  STUDENT_ALREADY_LINKED: "المخدوم مربوط بحساب آخر",
  STUDENT_NOT_FOUND: "المخدوم غير موجود",
  STUDENT_ALREADY_EXISTS: "فيه مخدوم بنفس الاسم والصف",
  GROUP_NOT_FOUND: "الجروب غير موجود",
  GROUP_NAME_TAKEN: "اسم الجروب مستخدم",
  INVALID_MEETING_DATE: "اختار يوم اجتماع فات في السنة الحالية",
  MEETING_SKIPPED: "الجمعة دي اتعمل لها سكيب، رجّعها الأول",
  AMOUNT_REQUIRED: "اكتب عدد النقط",
  INSUFFICIENT_BALANCE: "الرصيد مش كفاية للخصم",
  BALANCE_ALREADY_ZERO: "الرصيد صفر بالفعل",
  TRANSACTION_NOT_FOUND: "الحركة دي مش موجودة",
  TRANSACTION_NOT_UNDOABLE: "النقط دي اتشالت قبل كده",
  INVALID_SEASON_RANGE: "نهاية السنة لازم تكون بعد بدايتها",
  REASON_NOT_FOUND: "السبب ده مش موجود",
  REASON_ALREADY_EXISTS: "السبب ده موجود في القايمة بالفعل",
};

/** Arabic text for validation constraint codes on a field. */
const FIELD_ERRORS: Record<string, string> = {
  NotBlank: "الحقل ده مطلوب",
  NotNull: "الحقل ده مطلوب",
  NotEmpty: "الحقل ده مطلوب",
  Email: "الإيميل غير صحيح",
  Size: "الطول غير مناسب",
  Min: "القيمة صغيرة",
  Max: "القيمة كبيرة",
  Pattern: "الصيغة غير صحيحة",
};

export function errorMessage(error: unknown): string {
  if (error instanceof ApiError) return ERRORS[error.code] ?? ERRORS.UNKNOWN;
  return ERRORS.UNKNOWN;
}

export function fieldErrors(error: unknown): Record<string, string> {
  if (!(error instanceof ApiError)) return {};
  return Object.fromEntries(error.fieldErrors.map((f) => [f.field, FIELD_ERRORS[f.code] ?? "قيمة غير صحيحة"]));
}

export const GRADES: Grade[] = ["FIRST", "SECOND", "THIRD"];
export const gradeLabel: Record<Grade, string> = { FIRST: "أولى", SECOND: "تانية", THIRD: "تالتة" };
export const gradeLong: Record<Grade, string> = {
  FIRST: "أولى إعدادي",
  SECOND: "تانية إعدادي",
  THIRD: "تالتة إعدادي",
};

export const roleLabel: Record<Role, string> = { USER: "مستخدم", ADMIN: "أدمن", SUPER_ADMIN: "سوبر أدمن" };
export const statusLabel: Record<UserStatus, string> = {
  PENDING: "في الانتظار",
  APPROVED: "مفعّل",
  REJECTED: "مرفوض",
};

const kindLabel: Record<AttendanceKind, string> = { SERVICE: "حضور خدمة", MASS: "حضور قداس" };

export const typeLabel: Record<TransactionType, string> = {
  MANUAL_ADD: "إضافة",
  MANUAL_DEDUCT: "خصم",
  RESET: "تصفير",
  ATTENDANCE_AWARD: "حضور",
  ATTENDANCE_REVERSAL: "إلغاء حضور",
};

export const DAYS: DayOfWeek[] = ["SATURDAY", "SUNDAY", "MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY"];
export const dayLabel: Record<DayOfWeek, string> = {
  SATURDAY: "السبت",
  SUNDAY: "الأحد",
  MONDAY: "الإثنين",
  TUESDAY: "الثلاثاء",
  WEDNESDAY: "الأربعاء",
  THURSDAY: "الخميس",
  FRIDAY: "الجمعة",
};

/** Arabic description of a transaction; attendance notes are rebuilt from structured fields. */
export function transactionNote(t: Transaction): string {
  if (t.attendanceKind && t.attendanceDate) {
    const base = `${kindLabel[t.attendanceKind]} — ${formatDate(t.attendanceDate)}`;
    if (t.type !== "ATTENDANCE_REVERSAL") return base;
    return t.notes.includes("(meeting skipped)") ? `إلغاء ${base} (سكيب)` : `إلغاء ${base}`;
  }
  // An undone meeting bonus keeps the original reason as its note.
  if (t.type === "MANUAL_DEDUCT" && t.reversesId) return `تراجع: ${t.notes}`;
  return t.notes;
}

const dateFmt = new Intl.DateTimeFormat("ar-EG", { weekday: "long", day: "numeric", month: "long" });
const dateYearFmt = new Intl.DateTimeFormat("ar-EG", { day: "numeric", month: "long", year: "numeric" });
const shortFmt = new Intl.DateTimeFormat("ar-EG", { day: "numeric", month: "short" });
const dateTimeFmt = new Intl.DateTimeFormat("ar-EG", {
  day: "numeric",
  month: "short",
  hour: "numeric",
  minute: "2-digit",
});
const numberFmt = new Intl.NumberFormat("ar-EG");

/** Dates from the API are plain "YYYY-MM-DD"; parse at noon UTC so no timezone shifts the day. */
function parseDay(iso: string) {
  return new Date(`${iso}T12:00:00Z`);
}

export const formatDate = (iso: string) => dateFmt.format(parseDay(iso));
export const formatDateYear = (iso: string) => dateYearFmt.format(parseDay(iso));
export const formatShortDate = (iso: string) => shortFmt.format(parseDay(iso));
export const formatDateTime = (iso: string) => dateTimeFmt.format(new Date(iso));
export const formatNumber = (n: number) => numberFmt.format(n);
/** Signed number wrapped in an LTR isolate so the sign stays in front inside Arabic text. */
export const formatSigned = (n: number) => `⁦${n > 0 ? "+" : ""}${numberFmt.format(n)}⁩`;
