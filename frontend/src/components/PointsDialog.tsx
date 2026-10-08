import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { toast } from "sonner";
import { api, ApiError } from "@/lib/api";
import { errorMessage, fieldErrors, formatNumber, formatSigned } from "@/lib/i18n";
import type { ManualKind, Page, PointReason, Transaction } from "@/lib/types";
import { cn } from "@/lib/utils";
import { TransactionList } from "./TransactionList";
import { Button, Dialog, Field, Input, Pagination, Segmented, Spinner } from "./ui";

export interface PointsTarget {
  type: "students" | "groups";
  id: number;
  name: string;
  balance: number;
}

/** Manual add / deduct / reset for a student or group, with its paginated history. */
export function PointsDialog({ target, onClose }: { target: PointsTarget | null; onClose: () => void }) {
  const [tab, setTab] = useState<"new" | "history">("new");
  return (
    <Dialog open={Boolean(target)} onClose={onClose} title={target ? `نقط ${target.name}` : ""}>
      {target && (
        <>
          <div className="mb-4 flex items-center justify-between rounded-2xl bg-stone-50 px-4 py-3">
            <span className="text-sm text-stone-500">الرصيد الحالي</span>
            <span className="text-xl font-bold tabular">{formatNumber(target.balance)}</span>
          </div>
          <Segmented
            className="mb-4 w-full"
            value={tab}
            onChange={setTab}
            options={[
              { value: "new", label: "حركة جديدة" },
              { value: "history", label: "السجل" },
            ]}
          />
          {tab === "new" ? <NewTransaction target={target} onDone={onClose} /> : <History target={target} />}
        </>
      )}
    </Dialog>
  );
}

function NewTransaction({ target, onDone }: { target: PointsTarget; onDone: () => void }) {
  const queryClient = useQueryClient();
  const [kind, setKind] = useState<ManualKind>("ADD");
  const [amount, setAmount] = useState("");
  const [notes, setNotes] = useState("");
  const [saveReason, setSaveReason] = useState(false);
  const reasons = useQuery({ queryKey: ["point-reasons"], queryFn: () => api.get<PointReason[]>("/point-reasons") });

  const trimmed = notes.trim();
  const isSavedReason = reasons.data?.some((r) => r.label === trimmed) ?? false;

  const pickReason = (reason: PointReason) => {
    setNotes(reason.label);
    if (reason.defaultAmount && kind !== "RESET") setAmount(String(reason.defaultAmount));
  };

  const submit = useMutation({
    mutationFn: async () => {
      const tx = await api.post<Transaction>(`/${target.type}/${target.id}/points`, {
        kind,
        amount: kind === "RESET" ? null : Number(amount),
        notes,
      });
      if (saveReason && trimmed && !isSavedReason) {
        // Best effort: the transaction already succeeded, so a failed save shouldn't block it.
        await api
          .post("/point-reasons", { label: trimmed, defaultAmount: kind === "RESET" ? null : Number(amount) || null })
          .catch(() => toast.warning("اتسجلت النقط، بس السبب متحفظش في القايمة"));
      }
      return tx;
    },
    onSuccess: (tx) => {
      toast.success(`${target.name}: ${formatSigned(tx.amount)} نقطة · الرصيد ${formatNumber(tx.balanceAfter)}`);
      queryClient.invalidateQueries({ queryKey: [target.type] });
      queryClient.invalidateQueries({ queryKey: ["transactions"] });
      queryClient.invalidateQueries({ queryKey: ["dashboard"] });
      queryClient.invalidateQueries({ queryKey: ["point-reasons"] });
      onDone();
    },
    onError: (error) => {
      if (!(error instanceof ApiError && error.code === "VALIDATION_FAILED")) toast.error(errorMessage(error));
    },
  });
  const errors = fieldErrors(submit.error);

  return (
    <form
      className="space-y-4"
      onSubmit={(e) => {
        e.preventDefault();
        submit.mutate();
      }}
    >
      <Segmented
        className="w-full"
        value={kind}
        onChange={setKind}
        options={[
          { value: "ADD", label: "إضافة" },
          { value: "DEDUCT", label: "خصم" },
          { value: "RESET", label: "تصفير" },
        ]}
      />
      {kind !== "RESET" ? (
        <Field label="عدد النقط" error={errors.amount}>
          <Input type="number" inputMode="numeric" min={1} max={10000} required value={amount} onChange={(e) => setAmount(e.target.value)} />
        </Field>
      ) : (
        <p className="rounded-xl bg-red-50 px-3 py-2 text-sm text-red-700">
          هيتم خصم الرصيد كله ({formatNumber(target.balance)} نقطة).
        </p>
      )}
      <Field label="السبب" error={errors.notes}>
        <Input required maxLength={200} placeholder="اختار من تحت أو اكتب سبب جديد" value={notes} onChange={(e) => setNotes(e.target.value)} />
      </Field>

      {reasons.data && reasons.data.length > 0 && (
        <div className="flex flex-wrap gap-2">
          {reasons.data.map((r) => (
            <button
              key={r.id}
              type="button"
              onClick={() => pickReason(r)}
              className={cn(
                "rounded-full border px-3 py-1.5 text-sm transition-colors",
                r.label === trimmed
                  ? "border-brand-600 bg-brand-600 text-white"
                  : "border-stone-200 bg-white text-stone-700 hover:border-brand-300 hover:bg-brand-50",
              )}
            >
              {r.label}
              {r.defaultAmount && kind !== "RESET" && <span className="ms-1 opacity-70 tabular">({formatNumber(r.defaultAmount)})</span>}
            </button>
          ))}
        </div>
      )}

      {trimmed && !isSavedReason && (
        <label className="flex cursor-pointer items-center gap-2 text-sm text-stone-600">
          <input type="checkbox" className="size-4 accent-brand-700" checked={saveReason} onChange={(e) => setSaveReason(e.target.checked)} />
          احفظ السبب ده في القايمة عشان أختاره بعد كده
        </label>
      )}
      <Button type="submit" className="w-full" variant={kind === "ADD" ? "primary" : "danger"} loading={submit.isPending}>
        {kind === "ADD" ? "إضافة النقط" : kind === "DEDUCT" ? "خصم النقط" : "تصفير الرصيد"}
      </Button>
    </form>
  );
}

function History({ target }: { target: PointsTarget }) {
  const [page, setPage] = useState(0);
  const history = useQuery({
    queryKey: ["transactions", target.type, target.id, page],
    queryFn: () => api.get<Page<Transaction>>(`/${target.type}/${target.id}/transactions`, { page, size: 10 }),
    placeholderData: keepPreviousData,
  });
  if (history.isPending) return <Spinner />;
  if (history.isError) return <p className="text-sm text-red-600">{errorMessage(history.error)}</p>;
  if (history.data.items.length === 0) return <p className="py-8 text-center text-sm text-stone-500">مفيش حركات لسه.</p>;
  return (
    <>
      <TransactionList items={history.data.items} />
      <Pagination page={history.data.page} totalPages={history.data.totalPages} totalItems={history.data.totalItems} onChange={setPage} />
    </>
  );
}
