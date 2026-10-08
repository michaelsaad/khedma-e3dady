import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Check, Pencil, Plus, Trash2, X } from "lucide-react";
import { useState, type FormEvent } from "react";
import { toast } from "sonner";
import { api } from "@/lib/api";
import { errorMessage, formatNumber } from "@/lib/i18n";
import type { PointReason } from "@/lib/types";
import { Button, Card, Input, Spinner } from "./ui";

/** Saved reasons for manual points, picked from a list in the points dialog. Most used first. */
export function ReasonsManager() {
  const queryClient = useQueryClient();
  const reasons = useQuery({ queryKey: ["point-reasons"], queryFn: () => api.get<PointReason[]>("/point-reasons") });
  const [editingId, setEditingId] = useState<number | null>(null);

  const refresh = () => queryClient.invalidateQueries({ queryKey: ["point-reasons"] });
  const remove = useMutation({
    mutationFn: (id: number) => api.delete(`/point-reasons/${id}`),
    onSuccess: () => {
      toast.success("اتشال السبب");
      refresh();
    },
    onError: (error) => toast.error(errorMessage(error)),
  });

  return (
    <Card className="max-w-2xl p-5">
      <h2 className="font-semibold">الأسباب الجاهزة</h2>
      <p className="mt-1 mb-4 text-sm text-stone-500">
        الأسباب اللي بتتكرر كتير. بتظهر كاختيارات لما تضيف أو تخصم نقط، والأكتر استخدامًا بيظهر الأول. ولو حطيت عدد نقط، بيتملى لوحده.
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
                    {r.defaultAmount ? `${formatNumber(r.defaultAmount)} نقطة` : "من غير عدد ثابت"} · اتستخدم {formatNumber(r.usageCount)} مرة
                  </p>
                </div>
                <Button variant="ghost" size="icon" onClick={() => setEditingId(r.id)} aria-label={`تعديل ${r.label}`}>
                  <Pencil className="size-4" />
                </Button>
                <Button
                  variant="ghost"
                  size="icon"
                  className="hover:text-red-600"
                  onClick={() => remove.mutate(r.id)}
                  disabled={remove.isPending}
                  aria-label={`حذف ${r.label}`}
                >
                  <Trash2 className="size-4" />
                </Button>
              </li>
            ),
          )}
        </ul>
      ) : (
        <p className="mt-4 rounded-xl bg-stone-50 py-6 text-center text-sm text-stone-500">مفيش أسباب محفوظة لسه.</p>
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
      toast.success(reason ? "اتحفظ السبب" : "اتضاف السبب");
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
        placeholder="سبب جديد، مثلًا: حفظ آية"
        className="min-w-0 flex-[3_1_12rem]"
        value={label}
        onChange={(e) => setLabel(e.target.value)}
        aria-label="السبب"
      />
      <Input
        type="number"
        min={1}
        max={10000}
        placeholder="النقط (اختياري)"
        className="min-w-0 flex-[1_1_7rem]"
        value={amount}
        onChange={(e) => setAmount(e.target.value)}
        aria-label="عدد النقط"
      />
      {reason ? (
        <div className="flex gap-1">
          <Button type="submit" size="icon" loading={save.isPending} aria-label="حفظ">
            {!save.isPending && <Check className="size-4" />}
          </Button>
          <Button type="button" variant="ghost" size="icon" onClick={onCancel} aria-label="إلغاء">
            <X className="size-4" />
          </Button>
        </div>
      ) : (
        <Button type="submit" loading={save.isPending}>
          <Plus className="size-4" />
          إضافة
        </Button>
      )}
    </form>
  );
}
