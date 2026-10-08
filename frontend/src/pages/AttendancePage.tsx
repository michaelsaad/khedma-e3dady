import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { CalendarDays, Check, ChevronLeft, ChevronRight, Church, HandHeart, Search, Users } from "lucide-react";
import { useDeferredValue, useMemo, useState } from "react";
import { useSearchParams } from "react-router";
import { toast } from "sonner";
import { Badge, Button, Card, Dialog, EmptyState, ErrorState, Input, PageHeader, Segmented, Spinner } from "@/components/ui";
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
        <MeetingView date={date} meetings={list} onNavigate={goTo} />
      )}
    </>
  );
}

function MeetingView({ date, meetings, onNavigate }: { date: string; meetings: string[]; onNavigate: (d: string) => void }) {
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
            {isLatest ? "آخر اجتماع" : "اضغط لاختيار اجتماع تاني"}
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
        </>
      )}

      <Dialog open={pickerOpen} onClose={() => setPickerOpen(false)} title="اختار الاجتماع">
        <MeetingPicker
          meetings={meetings}
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
function MeetingPicker({ meetings, selected, onSelect }: { meetings: string[]; selected: string; onSelect: (d: string) => void }) {
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
                title={formatDateYear(d)}
                className={cn(
                  "rounded-xl border px-2 py-2.5 text-sm font-medium",
                  d === selected ? "border-brand-600 bg-brand-600 text-white" : "border-stone-200 hover:bg-stone-50",
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
