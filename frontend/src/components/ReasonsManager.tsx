import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Check, Pencil, Plus, Trash2, X } from "lucide-react";
import { useState, type FormEvent } from "react";
import { toast } from "sonner";
import { api } from "@/lib/api";
import { errorMessage } from "@/lib/i18n";
import type { PointReason } from "@/lib/types";
import { Button, Card, Input, Spinner } from "./ui";
import { copy } from "@/lib/copy";

/** Saved reasons for manual points, picked from a list in the points dialog. Most used first. */
export function ReasonsManager() {
  const queryClient = useQueryClient();
  const reasons = useQuery({ queryKey: ["point-reasons"], queryFn: () => api.get<PointReason[]>("/point-reasons") });
  const [editingId, setEditingId] = useState<number | null>(null);

  const refresh = () => queryClient.invalidateQueries({ queryKey: ["point-reasons"] });
  const remove = useMutation({
    mutationFn: (id: number) => api.delete(`/point-reasons/${id}`),
    onSuccess: () => {
      toast.success(copy.reasons.removed);
      refresh();
    },
    onError: (error) => toast.error(errorMessage(error)),
  });

  return (
    <Card className="max-w-2xl p-5">
      <h2 className="font-semibold">{copy.reasons.title}</h2>
      <p className="mt-1 mb-4 text-sm text-stone-500">
        {copy.reasons.subtitle}
      </p>

      <ReasonForm onSaved={refresh} />

      {reasons.isPending ? (
        <Spinner className="py-6" />
      ) : reasons.data && reasons.data.length > 0 ? (
        <ul className="mt-4 divide-y divide-stone-100 rounded-xl border border-stone-200">
          {reasons.data.map((r) =>
            editingId === r.id ? (
              <li key={r.id} className="p-2">
                <ReasonForm
                  reason={r}
                  onSaved={() => {
                    setEditingId(null);
                    refresh();
                  }}
                  onCancel={() => setEditingId(null)}
                />
              </li>
            ) : (
              <li key={r.id} className="flex items-center gap-3 px-3 py-2">
                <div className="min-w-0 flex-1">
                  <p className="truncate font-medium">{r.label}</p>
                  <p className="text-xs text-stone-500">
                    {r.defaultAmount ? copy.reasons.fixedPoints(r.defaultAmount) : copy.reasons.noFixedPoints} · {copy.reasons.used(r.usageCount)}
                  </p>
                </div>
                <Button variant="ghost" size="icon" onClick={() => setEditingId(r.id)} aria-label={copy.reasons.editLabel(r.label)}>
                  <Pencil className="size-4" />
                </Button>
                <Button
                  variant="ghost"
                  size="icon"
                  className="hover:text-red-600"
                  onClick={() => remove.mutate(r.id)}
                  disabled={remove.isPending}
                  aria-label={copy.reasons.deleteLabel(r.label)}
                >
                  <Trash2 className="size-4" />
                </Button>
              </li>
            ),
          )}
        </ul>
      ) : (
        <p className="mt-4 rounded-xl bg-stone-50 py-6 text-center text-sm text-stone-500">{copy.reasons.empty}</p>
      )}
    </Card>
  );
}

function ReasonForm({ reason, onSaved, onCancel }: { reason?: PointReason; onSaved: () => void; onCancel?: () => void }) {
  const [label, setLabel] = useState(reason?.label ?? "");
  const [amount, setAmount] = useState(reason?.defaultAmount ? String(reason.defaultAmount) : "");

  const save = useMutation({
    mutationFn: () => {
      const body = { label, defaultAmount: amount ? Number(amount) : null };
      return reason ? api.put(`/point-reasons/${reason.id}`, body) : api.post("/point-reasons", body);
    },
    onSuccess: () => {
      toast.success(reason ? copy.reasons.saved : copy.reasons.added);
      if (!reason) {
        setLabel("");
        setAmount("");
      }
      onSaved();
    },
    onError: (error) => toast.error(errorMessage(error)),
  });

  const submit = (e: FormEvent) => {
    e.preventDefault();
    save.mutate();
  };

  return (
    <form onSubmit={submit} className="flex flex-wrap gap-2">
      <Input
        required
        maxLength={200}
        placeholder={copy.reasons.placeholder}
        className="min-w-0 flex-[3_1_12rem]"
        value={label}
        onChange={(e) => setLabel(e.target.value)}
        aria-label={copy.points.reason}
      />
      <Input
        type="number"
        min={1}
        max={10000}
        placeholder={copy.reasons.pointsPlaceholder}
        className="min-w-0 flex-[1_1_7rem]"
        value={amount}
        onChange={(e) => setAmount(e.target.value)}
        aria-label={copy.points.amount}
      />
      {reason ? (
        <div className="flex gap-1">
          <Button type="submit" size="icon" loading={save.isPending} aria-label={copy.common.save}>
            {!save.isPending && <Check className="size-4" />}
          </Button>
          <Button type="button" variant="ghost" size="icon" onClick={onCancel} aria-label={copy.common.cancel}>
            <X className="size-4" />
          </Button>
        </div>
      ) : (
        <Button type="submit" loading={save.isPending}>
          <Plus className="size-4" />
          {copy.common.add}
        </Button>
      )}
    </form>
  );
}
