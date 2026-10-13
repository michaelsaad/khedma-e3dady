import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Settings2, Star, X } from "lucide-react";
import { useState } from "react";
import { Link } from "react-router";
import { toast } from "sonner";
import { api } from "@/lib/api";
import { copy } from "@/lib/copy";
import { errorMessage, formatSigned } from "@/lib/i18n";
import type { PointReason, Transaction } from "@/lib/types";
import { Button, Dialog, Field, Input, Spinner } from "./ui";

interface ExtraPointsTarget {
  studentId: number;
  studentName: string;
  extraPoints: number;
}

interface Grant {
  label: string;
  amount: number;
  saveReason?: boolean;
}

/**
 * Bonuses from the attendance page: one tap on a saved reason adds its points (tagged with the
 * meeting date), any number of times; a one-off or new reason can be typed and optionally saved.
 * The dialog lists the student's bonuses for the meeting, each removable.
 */
export function ExtraPointsDialog({ date, target, onClose }: { date: string; target: ExtraPointsTarget | null; onClose: () => void }) {
  const queryClient = useQueryClient();
  const reasons = useQuery({ queryKey: ["point-reasons"], queryFn: () => api.get<PointReason[]>("/point-reasons") });
  const bonusesKey = ["meeting-bonuses", date, target?.studentId];
  const bonuses = useQuery({
    queryKey: bonusesKey,
    queryFn: () => api.get<Transaction[]>(`/students/${target!.studentId}/meeting-bonuses`, { date }),
    enabled: Boolean(target),
  });
  const [pending, setPending] = useState<PointReason | null>(null);

  const close = () => {
    setPending(null);
    onClose();
  };

  const refresh = () => {
    for (const key of [["attendance", date], ["meeting-bonuses", date], ["students"], ["transactions"], ["dashboard"], ["point-reasons"]]) {
      queryClient.invalidateQueries({ queryKey: key });
    }
  };

  const undo = useMutation({
    mutationFn: ({ studentId, txId }: { studentId: number; txId: number }) => api.post<Transaction>(`/students/${studentId}/points/${txId}/undo`),
    onSuccess: () => {
      toast.success(copy.attendance.extraUndone);
      refresh();
    },
    onError: (error) => toast.error(errorMessage(error)),
  });

  const grant = useMutation({
    mutationFn: async ({ studentId, grant }: { studentId: number; studentName: string; grant: Grant }) => {
      if (grant.saveReason) {
        // Saved first so this use counts towards its ranking; best effort, the points still go in if it fails.
        await api.post("/point-reasons", { label: grant.label, defaultAmount: grant.amount }).catch(() => toast.warning(copy.points.reasonNotSaved));
      }
      return api.post<Transaction>(`/students/${studentId}/points`, { kind: "ADD", amount: grant.amount, notes: grant.label, meetingDate: date });
    },
    onSuccess: (tx, { studentId, studentName, grant: g }) => {
      toast.success(copy.attendance.extraAdded(studentName, g.amount, g.label), {
        duration: 6000,
        action: { label: copy.attendance.extraUndo, onClick: () => undo.mutate({ studentId, txId: tx.id }) },
      });
      refresh();
      // Stay open so several bonuses can be given in a row.
      setPending(null);
    },
    onError: (error) => toast.error(errorMessage(error)),
  });

  const give = (g: Grant) => target && grant.mutate({ studentId: target.studentId, studentName: target.studentName, grant: g });

  const pick = (reason: PointReason) => {
    if (reason.defaultAmount) give({ label: reason.label, amount: reason.defaultAmount });
    else setPending(reason);
  };

  const given = bonuses.data ?? [];
  const total = bonuses.data ? given.reduce((sum, t) => sum + t.amount, 0) : (target?.extraPoints ?? 0);

  return (
    <Dialog open={Boolean(target)} onClose={close} title={target ? copy.attendance.extraTitle(target.studentName) : ""}>
      {target && (
        <div className="space-y-4">
          {given.length > 0 && (
            <div className="rounded-2xl bg-brand-50 p-3">
              <p className="mb-2 flex items-center gap-1.5 text-sm font-semibold text-brand-800">
                <Star className="size-4 fill-current" />
                {copy.attendance.extraSoFar(total)}
              </p>
              <ul className="space-y-1">
                {given.map((t) => (
                  <li key={t.id} className="flex items-center gap-2 rounded-xl bg-white px-3 py-1.5 text-sm">
                    <span className="min-w-0 flex-1 truncate">{t.notes}</span>
                    <span className="font-semibold text-brand-700 tabular">{formatSigned(t.amount)}</span>
                    <button
                      type="button"
                      onClick={() => undo.mutate({ studentId: target.studentId, txId: t.id })}
                      disabled={undo.isPending}
                      aria-label={copy.attendance.extraRemove(t.notes)}
                      className="grid size-7 place-items-center rounded-lg text-stone-400 hover:bg-red-50 hover:text-red-600 disabled:opacity-50"
                    >
                      <X className="size-4" />
                    </button>
                  </li>
                ))}
              </ul>
            </div>
          )}

          {pending ? (
            <AmountForm reason={pending} loading={grant.isPending} onCancel={() => setPending(null)} onSubmit={(amount) => give({ label: pending.label, amount })} />
          ) : (
            <>
              {reasons.isPending ? (
                <Spinner />
              ) : reasons.data && reasons.data.length > 0 ? (
                <ul className="space-y-2">
                  {reasons.data.map((r) => (
                    <li key={r.id}>
                      <button
                        type="button"
                        disabled={grant.isPending}
                        onClick={() => pick(r)}
                        className="flex h-12 w-full items-center justify-between gap-3 rounded-xl border border-stone-200 bg-white px-4 text-start transition-colors hover:border-brand-300 hover:bg-brand-50 active:scale-[0.99] disabled:opacity-60"
                      >
                        <span className="truncate font-medium">{r.label}</span>
                        <span className="shrink-0 font-semibold text-brand-700 tabular">
                          {r.defaultAmount ? formatSigned(r.defaultAmount) : copy.attendance.extraAnyAmount}
                        </span>
                      </button>
                    </li>
                  ))}
                </ul>
              ) : (
                <p className="py-2 text-center text-sm text-stone-500">{copy.attendance.extraNoReasons}</p>
              )}

              <QuickBonusForm loading={grant.isPending} savedLabels={new Set((reasons.data ?? []).map((r) => r.label))} onSubmit={give} />

              <Link to="/settings" className="flex w-fit items-center gap-1 rounded-lg px-2 py-1.5 text-sm text-stone-500 hover:bg-stone-100 hover:text-stone-800">
                <Settings2 className="size-4" />
                {copy.attendance.extraEditReasons}
              </Link>
            </>
          )}
        </div>
      )}
    </Dialog>
  );
}

function AmountForm({ reason, loading, onCancel, onSubmit }: { reason: PointReason; loading: boolean; onCancel: () => void; onSubmit: (amount: number) => void }) {
  const [amount, setAmount] = useState("");
  return (
    <form
      className="space-y-4"
      onSubmit={(e) => {
        e.preventDefault();
        onSubmit(Number(amount));
      }}
    >
      <Field label={copy.attendance.extraAmountFor(reason.label)}>
        <Input type="number" inputMode="numeric" min={1} max={10000} required autoFocus value={amount} onChange={(e) => setAmount(e.target.value)} />
      </Field>
      <div className="flex gap-2">
        <Button type="submit" className="flex-1" loading={loading}>
          {copy.attendance.extraSubmit}
        </Button>
        <Button type="button" variant="ghost" onClick={onCancel}>
          {copy.common.cancel}
        </Button>
      </div>
    </form>
  );
}

/** Always-open one-off bonus: optional reason + points. The reason is only saved to the list if asked. */
function QuickBonusForm({ loading, savedLabels, onSubmit }: { loading: boolean; savedLabels: Set<string>; onSubmit: (grant: Grant) => void }) {
  const [label, setLabel] = useState("");
  const [amount, setAmount] = useState("");
  const [save, setSave] = useState(false);
  const trimmed = label.trim();
  const canSave = trimmed !== "" && !savedLabels.has(trimmed);
  return (
    <form
      className="space-y-3 rounded-2xl border border-stone-200 p-3"
      onSubmit={(e) => {
        e.preventDefault();
        onSubmit({ label: trimmed || copy.attendance.extraDefaultReason, amount: Number(amount), saveReason: canSave && save });
        setLabel("");
        setAmount("");
        setSave(false);
      }}
    >
      <p className="text-sm font-semibold text-stone-700">{copy.attendance.extraOneOffTitle}</p>
      <div className="flex gap-2">
        <Input
          maxLength={200}
          className="min-w-0 flex-[3_1_0]"
          placeholder={copy.attendance.extraReasonOptional}
          aria-label={copy.attendance.extraReasonOptional}
          value={label}
          onChange={(e) => setLabel(e.target.value)}
        />
        <Input
          type="number"
          inputMode="numeric"
          min={1}
          max={10000}
          required
          className="min-w-0 flex-[2_1_0]"
          placeholder={copy.points.amount}
          aria-label={copy.points.amount}
          value={amount}
          onChange={(e) => setAmount(e.target.value)}
        />
      </div>
      {canSave && (
        <label className="flex cursor-pointer items-center gap-2 text-sm text-stone-600">
          <input type="checkbox" className="size-4 accent-brand-700" checked={save} onChange={(e) => setSave(e.target.checked)} />
          {copy.attendance.extraSaveReason}
        </label>
      )}
      <Button type="submit" className="w-full" loading={loading}>
        {copy.attendance.extraSubmit}
      </Button>
    </form>
  );
}
