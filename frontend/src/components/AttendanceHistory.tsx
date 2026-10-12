import { formatShortDate } from "@/lib/i18n";
import { cn } from "@/lib/utils";
import { copy } from "@/lib/copy";

/** Compact per-meeting grid: one tile per meeting with service/mass marks. Newest first. */
export function AttendanceHistory({ items }: { items: { date: string; service: boolean; mass: boolean }[] }) {
  if (items.length === 0) return <p className="py-6 text-center text-sm text-stone-500">{copy.student.noMeetings}</p>;
  return (
    <div>
      <div className="mb-3 flex gap-4 text-xs text-stone-500">
        <span className="flex items-center gap-1.5">
          <span className="size-2.5 rounded-full bg-brand-600" /> {copy.common.service}
        </span>
        <span className="flex items-center gap-1.5">
          <span className="size-2.5 rounded-full bg-mass-400" /> {copy.common.mass}
        </span>
      </div>
      <div className="grid grid-cols-4 gap-2 sm:grid-cols-6 lg:grid-cols-8">
        {items.map((m) => {
          const none = !m.service && !m.mass;
          return (
            <div
              key={m.date}
              className={cn(
                "rounded-xl border p-2 text-center",
                none ? "border-dashed border-stone-200 bg-stone-50" : "border-stone-200 bg-white",
              )}
              title={formatShortDate(m.date)}
            >
              <p className="text-[11px] text-stone-500">{formatShortDate(m.date)}</p>
              <div className="mt-1.5 flex justify-center gap-1">
                <span className={cn("size-2.5 rounded-full", m.service ? "bg-brand-600" : "bg-stone-200")} />
                <span className={cn("size-2.5 rounded-full", m.mass ? "bg-mass-400" : "bg-stone-200")} />
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}
