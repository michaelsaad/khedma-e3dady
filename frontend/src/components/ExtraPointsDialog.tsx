import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Plus, Settings2 } from "lucide-react";
import { useState } from "react";
import { Link } from "react-router";
import { toast } from "sonner";
import { api } from "@/lib/api";
import { copy } from "@/lib/copy";
import { errorMessage, formatSigned } from "@/lib/i18n";
import type { PointReason, Transaction } from "@/lib/types";
import { Button, Dialog, Field, Input, Spinner } from "./ui";

export interface ExtraPointsTarget {
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
 * Quick extra points from the attendance page: one tap on a saved reason adds its points
 * (tagged with the meeting date) and offers an undo; a new reason can be saved on the way.
 */
export function ExtraPointsDialog({ date, target, onClose }: { date: string; target: ExtraPointsTarget | null; onClose: () => void }) {
  const queryClient = useQueryClient();
  const reasons = useQuery({ queryKey: ["point-reasons"], queryFn: () => api.get<PointReason[]>("/point-reasons") });
  const [pending, setPending] = useState<PointReason | null>(null);
  const [creating, setCreating] = useState(false);

  const close = () => {
    setPending(null);
    setCreating(false);
    onClose();
  };

  const refresh = () => {
    queryClient.invalidateQueries({ queryKey: ["attendance", date] });
    queryClient.invalidateQueries({ queryKey: ["students"] });
    queryClient.invalidateQueries({ queryKey: ["transactions"] });
    queryClient.invalidateQueries({ queryKey: ["dashboard"] });
    queryClient.invalidateQueries({ queryKey: ["point-reasons"] });
  };

  const post = (studentId: number, kind: "ADD" | "DEDUCT", amount: number, notes: string) =>
    api.post<Transaction>(`/students/${studentId}/points`, { kind, amount, notes, meetingDate: date });

  const undo = useMutation({
    mutationFn: ({ studentId, grant }: { studentId: number; grant: Grant }) =>
      post(studentId, "DEDUCT", grant.amount, copy.attendance.extraUndoNote(grant.label)),
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
      return post(studentId, "ADD", grant.amount, grant.label);
    },
    onSuccess: (_tx, { studentId, studentName, grant: g }) => {
      toast.success(copy.attendance.extraAdded(studentName, g.amount, g.label), {
        duration: 6000,
        action: { label: copy.attendance.extraUndo, onClick: () => undo.mutate({ studentId, grant: g }) },
      });
      refresh();
      close();
    },
    onError: (error) => toast.error(errorMessage(error)),
  });

  const give = (g: Grant) => target && grant.mutate({ studentId: target.studentId, studentName: target.studentName, grant: g });

  const pick = (reason: PointReason) => {
    if (reason.defaultAmount) give({ label: reason.label, amount: reason.defaultAmount });
    else setPending(reason);
  };

  return (
    <Dialog open={Boolean(target)} onClose={close} title={target ? copy.attendance.extraTitle(target.studentName) : ""}>
      {target && (
        <div className="space-y-4">
          {target.extraPoints !== 0 && (
            <p className="rounded-xl bg-brand-50 px-3 py-2 text-sm text-brand-800">{copy.attendance.extraSoFar(target.extraPoints)}</p>
          )}

          {creating ? (
            <NewReasonForm loading={grant.isPending} onCancel={() => setCreating(false)} onSubmit={give} />
          ) : pending ? (
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

              <div className="flex items-center justify-between gap-2">
                <Button variant="secondary" onClick={() => setCreating(true)}>
                  <Plus className="size-4" />
                  {copy.attendance.extraNewReason}
                </Button>
                <Link to="/settings" className="flex items-center gap-1 rounded-lg px-2 py-1.5 text-sm text-stone-500 hover:bg-stone-100 hover:text-stone-800">
                  <Settings2 className="size-4" />
                  {copy.attendance.extraEditReasons}
                </Link>
              </div>
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

function NewReasonForm({ loading, onCancel, onSubmit }: { loading: boolean; onCancel: () => void; onSubmit: (grant: Grant) => void }) {
  const [label, setLabel] = useState("");
  const [amount, setAmount] = useState("");
  const [save, setSave] = useState(true);
  return (
    <form
      className="space-y-4"
      onSubmit={(e) => {
        e.preventDefault();
        onSubmit({ label: label.trim(), amount: Number(amount), saveReason: save });
      }}
    >
      <Field label={copy.points.reason}>
        <Input required maxLength={200} autoFocus placeholder={copy.attendance.extraReasonPlaceholder} value={label} onChange={(e) => setLabel(e.target.value)} />
      </Field>
      <Field label={copy.points.amount}>
        <Input type="number" inputMode="numeric" min={1} max={10000} required value={amount} onChange={(e) => setAmount(e.target.value)} />
      </Field>
      <label className="flex cursor-pointer items-center gap-2 text-sm text-stone-600">
        <input type="checkbox" className="size-4 accent-brand-700" checked={save} onChange={(e) => setSave(e.target.checked)} />
        {copy.attendance.extraSaveReason}
      </label>
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
