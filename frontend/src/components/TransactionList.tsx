import { CalendarCheck, CalendarX, Minus, Plus, RotateCcw } from "lucide-react";
import { formatDateTime, formatNumber, formatSigned, transactionNote, typeLabel } from "@/lib/i18n";
import type { Transaction } from "@/lib/types";
import { cn } from "@/lib/utils";

const ICONS = {
  MANUAL_ADD: Plus,
  MANUAL_DEDUCT: Minus,
  RESET: RotateCcw,
  ATTENDANCE_AWARD: CalendarCheck,
  ATTENDANCE_REVERSAL: CalendarX,
} as const;

/** Ledger rows. `showTarget` adds the student/group name (for the global history page). */
export function TransactionList({ items, showTarget }: { items: Transaction[]; showTarget?: boolean }) {
  return (
    <ul className="divide-y divide-stone-100">
      {items.map((t) => {
        const Icon = ICONS[t.type];
        const positive = t.amount > 0;
        return (
          <li key={t.id} className="flex items-center gap-3 py-3">
            <span
              className={cn(
                "grid size-9 shrink-0 place-items-center rounded-xl",
                positive ? "bg-brand-50 text-brand-700" : "bg-red-50 text-red-600",
              )}
            >
              <Icon className="size-4" />
            </span>
            <div className="min-w-0 flex-1">
              <p className="truncate text-sm font-medium text-stone-900">
                {showTarget && (
                  <span className="text-stone-500">{t.studentName ?? `جروب ${t.groupName}`} · </span>
                )}
                {transactionNote(t)}
              </p>
              <p className="text-xs text-stone-500">
                {typeLabel[t.type]} · {formatDateTime(t.createdAt)}
                {t.createdBy && ` · ${t.createdBy}`}
              </p>
            </div>
            <div className="text-end">
              <p className={cn("font-semibold tabular", positive ? "text-brand-700" : "text-red-600")} dir="ltr">
                {formatSigned(t.amount)}
              </p>
              <p className="text-xs text-stone-400 tabular">الرصيد {formatNumber(t.balanceAfter)}</p>
            </div>
          </li>
        );
      })}
    </ul>
  );
}
