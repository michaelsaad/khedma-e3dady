import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { CalendarDays, CalendarOff, Check, ChevronLeft, ChevronRight, Church, HandHeart, RotateCcw, Search, Users } from "lucide-react";
import { useDeferredValue, useMemo, useState } from "react";
import { useSearchParams } from "react-router";
import { toast } from "sonner";
import { Badge, Button, Card, Dialog, EmptyState, ErrorState, Field, Input, PageHeader, Segmented, Spinner } from "@/components/ui";
import { api } from "@/lib/api";
import { errorMessage, formatDate, formatDateYear, formatNumber, formatShortDate, formatSigned, gradeLabel, GRADES } from "@/lib/i18n";
import type { AttendanceRow, Grade, MeetingAttendance, MeetingsResponse, SaveAttendanceResult } from "@/lib/types";
import { cn } from "@/lib/utils";

type Kind = "service" | "mass";

export default function AttendancePage() {
  const [params, setParams] = useSearchParams();
  const meetings = useQuery({ queryKey: ["meetings"], queryFn: () => api.get<MeetingsResponse>("/attendance/meetings") });
  const list = meetings.data?.meetings ?? [];
  const date = params.get("date") ?? list[list.length - 1] ?? null;
  const goTo = (next: string) => setParams({ date: next }, { replace: true });

  if (meetings.isPending) return <Spinner />;
  if (meetings.isError) return <ErrorState message={errorMessage(meetings.error)} onRetry={() => meetings.refetch()} />;

  return (
    <>
      <PageHeader title="الحضور" subtitle="اضغط على خدمة أو قداس، والحفظ والنقط بيحصلوا على طول." />
      {!date ? (
        <Card>
          <EmptyState icon={<CalendarDays className="size-10" />} title="لسه مفيش اجتماعات" text="أول اجتماع في السنة لسه مجاش." />
        </Card>
      ) : (
        <MeetingView date={date} meetings={list} skipped={meetings.data?.skipped ?? []} onNavigate={goTo} />
      )}
    </>
  );
}

function MeetingView({
  date,
  meetings,
  skipped,
  onNavigate,
}: {
  date: string;
  meetings: string[];
  skipped: string[];
  onNavigate: (d: string) => void;
}) {
  const [skipOpen, setSkipOpen] = useState(false);
  const queryClient = useQueryClient();
  const key = ["attendance", date];
  const meeting = useQuery({ queryKey: key, queryFn: () => api.get<MeetingAttendance>(`/attendance/${date}`) });
  const [search, setSearch] = useState("");
  const [grade, setGrade] = useState<Grade | "ALL">("ALL");
  const [pickerOpen, setPickerOpen] = useState(false);
  const [saving, setSaving] = useState<Set<number>>(new Set());
  const q = useDeferredValue(search.trim());

  const save = useMutation({
    mutationFn: (row: AttendanceRow) =>
      api.put<SaveAttendanceResult>(`/attendance/${date}/students/${row.studentId}`, { service: row.service, mass: row.mass }),
    onMutate: async (row) => {
      setSaving((s) => new Set(s).add(row.studentId));
      await queryClient.cancelQueries({ queryKey: key });
      const previous = queryClient.getQueryData<MeetingAttendance>(key);
      queryClient.setQueryData<MeetingAttendance>(key, (data) => data && withRow(data, row));
      return { previous };
    },
    onError: (error, _row, context) => {
      if (context?.previous) queryClient.setQueryData(key, context.previous);
      toast.error(errorMessage(error));
    },
    onSuccess: (result, row) => {
      if (result.netPoints !== 0) {
        toast.success(`${row.studentName}: ${formatSigned(result.netPoints)} نقطة`, { duration: 1800 });
      }
      queryClient.invalidateQueries({ queryKey: ["dashboard"] });
    },
    onSettled: (_r, _e, row) =>
      setSaving((s) => {
        const next = new Set(s);
        next.delete(row.studentId);
        return next;
      }),
  });

  const toggle = (row: AttendanceRow, kind: Kind) => save.mutate({ ...row, [kind]: !row[kind] });

  const rows = useMemo(() => {
    const all = meeting.data?.rows ?? [];
    return all.filter((r) => (grade === "ALL" || r.grade === grade) && (!q || r.studentName.includes(q)));
  }, [meeting.data, grade, q]);

  const isLatest = date === meetings[meetings.length - 1];

  return (
    <div className="space-y-4">
      {/* Meeting navigator */}
      <Card className="flex items-center gap-2 p-2">
        <Button
          variant="ghost"
          size="icon"
          disabled={!meeting.data?.previousMeeting}
          onClick={() => meeting.data?.previousMeeting && onNavigate(meeting.data.previousMeeting)}
          aria-label="الاجتماع اللي قبله"
        >
          <ChevronRight className="size-5" />
        </Button>
        <button onClick={() => setPickerOpen(true)} className="flex flex-1 flex-col items-center rounded-xl py-1.5 hover:bg-stone-50">
          <span className="font-semibold">{formatDate(date)}</span>
          <span className="flex items-center gap-1 text-xs text-stone-500">
            <CalendarDays className="size-3.5" />
            {meeting.data?.skipped ? "متلغية" : isLatest ? "آخر اجتماع" : "اضغط لاختيار اجتماع تاني"}
          </span>
        </button>
        <Button
          variant="ghost"
          size="icon"
          disabled={!meeting.data?.nextMeeting}
          onClick={() => meeting.data?.nextMeeting && onNavigate(meeting.data.nextMeeting)}
          aria-label="الاجتماع اللي بعده"
        >
          <ChevronLeft className="size-5" />
        </Button>
      </Card>

      {meeting.isPending ? (
        <Spinner />
      ) : meeting.isError ? (
        <ErrorState message={errorMessage(meeting.error)} onRetry={() => meeting.refetch()} />
      ) : meeting.data.skipped ? (
        <SkippedBanner date={date} reason={meeting.data.skipReason} />
      ) : (
        <>
          <div className="grid grid-cols-3 gap-2">
            <Counter label="المخدومين" value={meeting.data.rows.length} icon={<Users className="size-4" />} />
            <Counter label="الخدمة" value={meeting.data.serviceCount} icon={<HandHeart className="size-4" />} tone="brand" />
            <Counter label="القداس" value={meeting.data.massCount} icon={<Church className="size-4" />} tone="blue" />
          </div>

          <div className="flex flex-col gap-2 sm:flex-row">
            <div className="relative flex-1">
              <Search className="pointer-events-none absolute start-3 top-1/2 size-4 -translate-y-1/2 text-stone-400" />
              <Input placeholder="دوّر باسم المخدوم" className="ps-9" value={search} onChange={(e) => setSearch(e.target.value)} />
            </div>
            <Segmented
              value={grade}
              onChange={setGrade}
              options={[{ value: "ALL" as const, label: "الكل" }, ...GRADES.map((g) => ({ value: g, label: gradeLabel[g] }))]}
            />
          </div>

          <Card>
            {rows.length === 0 ? (
              <EmptyState icon={<Users className="size-10" />} title="مفيش مخدومين" text="ضيف مخدومين من صفحة المخدومين، أو غيّر البحث." />
            ) : (
              <ul className="divide-y divide-stone-100">
                {rows.map((row) => (
                  <li key={row.studentId} className="flex items-center gap-3 px-4 py-3">
                    <div className="min-w-0 flex-1">
                      <p className="truncate font-medium">{row.studentName}</p>
                      <Badge className="mt-0.5">{gradeLabel[row.grade]}</Badge>
                    </div>
                    <ToggleChip
                      label="خدمة"
                      active={row.service}
                      tone="brand"
                      disabled={saving.has(row.studentId)}
                      onClick={() => toggle(row, "service")}
                    />
                    <ToggleChip
                      label="قداس"
                      active={row.mass}
                      tone="blue"
                      disabled={saving.has(row.studentId)}
                      onClick={() => toggle(row, "mass")}
                    />
                  </li>
                ))}
              </ul>
            )}
          </Card>

          <button
            onClick={() => setSkipOpen(true)}
            className="mx-auto flex items-center gap-1.5 rounded-xl px-3 py-2 text-sm text-stone-500 hover:bg-stone-100 hover:text-red-600"
          >
            <CalendarOff className="size-4" />
            مفيش اجتماع الجمعة دي؟ إلغاء الجمعة
          </button>
        </>
      )}

      <SkipDialog date={date} open={skipOpen} onClose={() => setSkipOpen(false)} />

      <Dialog open={pickerOpen} onClose={() => setPickerOpen(false)} title="اختار الاجتماع">
        <MeetingPicker
          meetings={meetings}
          skipped={skipped}
          selected={date}
          onSelect={(d) => {
            onNavigate(d);
            setPickerOpen(false);
          }}
        />
      </Dialog>
    </div>
  );
}

function withRow(data: MeetingAttendance, row: AttendanceRow): MeetingAttendance {
  const rows = data.rows.map((r) => (r.studentId === row.studentId ? { ...row, recorded: true } : r));
  return {
    ...data,
    rows,
    serviceCount: rows.filter((r) => r.service).length,
    massCount: rows.filter((r) => r.mass).length,
  };
}

function ToggleChip({
  label,
  active,
  tone,
  disabled,
  onClick,
}: {
  label: string;
  active: boolean;
  tone: "brand" | "blue";
  disabled: boolean;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      aria-pressed={active}
      className={cn(
        "flex h-10 min-w-[4.5rem] items-center justify-center gap-1 rounded-xl border px-3 text-sm font-medium transition-all active:scale-95",
        "disabled:opacity-60",
        active
          ? tone === "brand"
            ? "border-brand-600 bg-brand-600 text-white"
            : "border-sky-500 bg-sky-500 text-white"
          : "border-stone-200 bg-white text-stone-500 hover:border-stone-300",
      )}
    >
      {active && <Check className="size-4" />}
      {label}
    </button>
  );
}

function Counter({ label, value, icon, tone = "stone" }: { label: string; value: number; icon: React.ReactNode; tone?: "stone" | "brand" | "blue" }) {
  const tones = { stone: "text-stone-600", brand: "text-brand-700", blue: "text-sky-700" };
  return (
    <Card className="px-3 py-2.5">
      <p className={cn("flex items-center gap-1 text-xs", tones[tone])}>
        {icon}
        {label}
      </p>
      <p className="mt-0.5 text-xl font-bold tabular">{formatNumber(value)}</p>
    </Card>
  );
}

/** All past meetings grouped by month, newest first — quicker than a long dropdown. */
function MeetingPicker({
  meetings,
  skipped,
  selected,
  onSelect,
}: {
  meetings: string[];
  skipped: string[];
  selected: string;
  onSelect: (d: string) => void;
}) {
  const months = useMemo(() => {
    const groups = new Map<string, string[]>();
    for (const d of [...meetings].reverse()) {
      const key = d.slice(0, 7);
      groups.set(key, [...(groups.get(key) ?? []), d]);
    }
    return [...groups.entries()];
  }, [meetings]);
  const monthFmt = new Intl.DateTimeFormat("ar-EG", { month: "long", year: "numeric" });

  return (
    <div className="space-y-5">
      {months.map(([month, days]) => (
        <div key={month}>
          <p className="mb-2 text-sm font-semibold text-stone-500">{monthFmt.format(new Date(`${month}-15T12:00:00Z`))}</p>
          <div className="grid grid-cols-3 gap-2 sm:grid-cols-4">
            {days.map((d) => (
              <button
                key={d}
                onClick={() => onSelect(d)}
                title={skipped.includes(d) ? `${formatDateYear(d)} (متلغية)` : formatDateYear(d)}
                className={cn(
                  "rounded-xl border px-2 py-2.5 text-sm font-medium",
                  d === selected
                    ? "border-brand-600 bg-brand-600 text-white"
                    : skipped.includes(d)
                      ? "border-dashed border-stone-300 bg-stone-50 text-stone-400 line-through"
                      : "border-stone-200 hover:bg-stone-50",
                )}
              >
                {formatShortDate(d)}
              </button>
            ))}
          </div>
        </div>
      ))}
    </div>
  );
}

/** Refresh everything a skip/unskip changes: the meeting, the meeting list, points and dashboard. */
function useMeetingStatusMutation<TArg>(fn: (arg: TArg) => Promise<SaveAttendanceResult>, success: (r: SaveAttendanceResult) => string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: fn,
    onSuccess: (result) => {
      toast.success(success(result));
      for (const key of ["attendance", "meetings", "dashboard", "transactions", "students"]) {
        queryClient.invalidateQueries({ queryKey: [key] });
      }
    },
    onError: (error) => toast.error(errorMessage(error)),
  });
}

function SkipDialog({ date, open, onClose }: { date: string; open: boolean; onClose: () => void }) {
  const [reason, setReason] = useState("");
  const skip = useMeetingStatusMutation(
    () => api.put<SaveAttendanceResult>(`/attendance/${date}/skip`, { reason }),
    (r) => (r.netPoints < 0 ? `اتلغت الجمعة واتشال ${formatNumber(-r.netPoints)} نقطة` : "اتلغت الجمعة"),
  );
  return (
    <Dialog
      open={open}
      onClose={onClose}
      title="إلغاء الجمعة"
      footer={
        <>
          <Button variant="outline" className="flex-1" onClick={onClose}>
            رجوع
          </Button>
          <Button
            variant="danger"
            className="flex-1"
            loading={skip.isPending}
            onClick={() => skip.mutate(undefined, { onSuccess: () => { setReason(""); onClose(); } })}
          >
            إلغاء الجمعة
          </Button>
        </>
      }
    >
      <div className="space-y-4">
        <p className="text-sm text-stone-600">
          لو مفيش اجتماع <span className="font-semibold text-stone-900">{formatDate(date)}</span>:
        </p>
        <ul className="list-disc space-y-1 ps-5 text-sm text-stone-600">
          <li>أي نقط اتوزعت على حضورها هتتشال.</li>
          <li>مش هتتحسب في نسب الحضور ولا في عدد الاجتماعات.</li>
          <li>الحضور المسجّل هيفضل محفوظ، ولو رجّعتها النقط هترجع.</li>
        </ul>
        <Field label="السبب (اختياري)">
          <Input maxLength={200} placeholder="مثلًا: رحلة، أجازة، عيد" value={reason} onChange={(e) => setReason(e.target.value)} />
        </Field>
      </div>
    </Dialog>
  );
}

function SkippedBanner({ date, reason }: { date: string; reason: string | null }) {
  const unskip = useMeetingStatusMutation(
    () => api.delete<SaveAttendanceResult>(`/attendance/${date}/skip`),
    (r) => (r.netPoints > 0 ? `رجعت الجمعة ورجع ${formatNumber(r.netPoints)} نقطة` : "رجعت الجمعة"),
  );
  return (
    <Card className="flex flex-col items-center gap-3 border-dashed px-5 py-8 text-center">
      <span className="grid size-12 place-items-center rounded-2xl bg-stone-100 text-stone-500">
        <CalendarOff className="size-6" />
      </span>
      <div>
        <p className="font-semibold">الجمعة دي متلغية</p>
        {reason && <p className="mt-1 text-sm text-stone-600">السبب: {reason}</p>}
        <p className="mt-1 text-sm text-stone-500">مش بتتحسب في النسب ومفيش عليها نقط.</p>
      </div>
      <Button variant="secondary" loading={unskip.isPending} onClick={() => unskip.mutate(undefined)}>
        <RotateCcw className="size-4" />
        رجّعها اجتماع عادي
      </Button>
    </Card>
  );
}
