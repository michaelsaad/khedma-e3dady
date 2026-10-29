import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { CalendarRange, Plus, Trash2 } from "lucide-react";
import { useState, type FormEvent } from "react";
import { toast } from "sonner";
import { api } from "@/lib/api";
import { copy } from "@/lib/copy";
import { errorMessage, formatDate } from "@/lib/i18n";
import type { PointPeriod } from "@/lib/types";
import { Badge, Button, Card, Dialog, Field, Input, Spinner } from "./ui";

const today = () => new Date().toLocaleDateString("en-CA", { timeZone: "Africa/Cairo" });

/** Settings card: point periods (points start again from zero; older periods stay viewable). */
export function PeriodsManager() {
  const queryClient = useQueryClient();
  const periods = useQuery({ queryKey: ["point-periods"], queryFn: () => api.get<PointPeriod[]>("/point-periods") });
  const [creating, setCreating] = useState(false);
  const [name, setName] = useState("");
  const [startsOn, setStartsOn] = useState(today);

  const refresh = () => {
    for (const key of [["point-periods"], ["dashboard"], ["students"], ["groups"]]) queryClient.invalidateQueries({ queryKey: key });
  };

  const create = useMutation({
    mutationFn: () => api.post<PointPeriod>("/point-periods", { name: name.trim(), startsOn }),
    onSuccess: () => {
      toast.success(copy.periods.started);
      setCreating(false);
      setName("");
      refresh();
    },
    onError: (error) => toast.error(errorMessage(error)),
  });

  const remove = useMutation({
    mutationFn: (id: number) => api.delete(`/point-periods/${id}`),
    onSuccess: () => {
      toast.success(copy.periods.removed);
      refresh();
    },
    onError: (error) => toast.error(errorMessage(error)),
  });

  const list = periods.data ?? [];
  const latest = list[list.length - 1];

  return (
    <Card className="max-w-2xl p-5">
      <div className="flex items-start justify-between gap-3">
        <div>
          <h2 className="font-semibold">{copy.periods.title}</h2>
          <p className="mt-1 text-sm text-stone-500">{copy.periods.subtitle}</p>
        </div>
        <Button variant="secondary" size="sm" onClick={() => setCreating(true)}>
          <Plus className="size-4" />
          {copy.periods.start}
        </Button>
      </div>

      {periods.isPending ? (
        <Spinner className="py-6" />
      ) : (
        <ul className="mt-4 divide-y divide-stone-100 rounded-xl border border-stone-200">
          {[...list].reverse().map((p) => (
            <li key={p.id} className="flex items-center gap-3 px-3 py-2.5">
              <CalendarRange className="size-4 shrink-0 text-stone-400" />
              <div className="min-w-0 flex-1">
                <p className="truncate font-medium">{p.name}</p>
                <p className="text-xs text-stone-500">{copy.periods.range(formatDate(p.startsOn), p.endsOn ? formatDate(p.endsOn) : null)}</p>
              </div>
              {p.current && <Badge tone="brand">{copy.periods.current}</Badge>}
              {p.upcoming && <Badge>{copy.periods.upcoming}</Badge>}
              {latest && p.id === latest.id && list.length > 1 && (
                <Button
                  variant="ghost"
                  size="icon"
                  className="hover:text-red-600"
                  disabled={remove.isPending}
                  onClick={() => window.confirm(copy.periods.removeConfirm(p.name)) && remove.mutate(p.id)}
                  aria-label={copy.periods.removeLabel(p.name)}
                >
                  <Trash2 className="size-4" />
                </Button>
              )}
            </li>
          ))}
        </ul>
      )}

      <Dialog open={creating} onClose={() => setCreating(false)} title={copy.periods.newTitle}>
        <form
          className="space-y-4"
          onSubmit={(event: FormEvent) => {
            event.preventDefault();
            create.mutate();
          }}
        >
          <p className="rounded-xl bg-brand-50 px-3 py-2 text-sm text-brand-800">{copy.periods.explain}</p>
          <Field label={copy.periods.nameLabel}>
            <Input required maxLength={100} placeholder={copy.periods.namePlaceholder} value={name} onChange={(e) => setName(e.target.value)} />
          </Field>
          <Field label={copy.periods.startLabel} hint={copy.periods.startHint}>
            <Input type="date" required value={startsOn} onChange={(e) => setStartsOn(e.target.value)} />
          </Field>
          <Button type="submit" className="w-full" loading={create.isPending}>
            {copy.periods.confirm}
          </Button>
        </form>
      </Dialog>
    </Card>
  );
}

/** Picks which period's points to show; the current one by default. */
export function PeriodPicker({ value, onChange }: { value: number | undefined; onChange: (id: number | undefined) => void }) {
  const periods = useQuery({ queryKey: ["point-periods"], queryFn: () => api.get<PointPeriod[]>("/point-periods") });
  const shown = (periods.data ?? []).filter((p) => !p.upcoming);
  if (shown.length < 2) return null;
  const current = shown.find((p) => p.current);
  return (
    <select
      value={value ?? current?.id ?? ""}
      onChange={(e) => {
        const id = Number(e.target.value);
        onChange(id === current?.id ? undefined : id);
      }}
      aria-label={copy.periods.pickerLabel}
      className="h-10 rounded-xl border border-stone-300 bg-white px-3 text-sm"
    >
      {[...shown].reverse().map((p) => (
        <option key={p.id} value={p.id}>
          {p.current ? copy.periods.currentOption(p.name) : p.name}
        </option>
      ))}
    </select>
  );
}
