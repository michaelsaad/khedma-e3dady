import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Church, HandHeart } from "lucide-react";
import { useState } from "react";
import { toast } from "sonner";
import { Button, Card, ErrorState, Field, Input, PageHeader, Select, Spinner } from "@/components/ui";
import { api, ApiError } from "@/lib/api";
import { DAYS, dayLabel, errorMessage, fieldErrors, formatDateTime } from "@/lib/i18n";
import type { DayOfWeek, Settings } from "@/lib/types";

export default function SettingsPage() {
  const settings = useQuery({ queryKey: ["settings"], queryFn: () => api.get<Settings>("/settings") });
  return (
    <>
      <PageHeader title="إعدادات التطبيق" subtitle="النقط الأوتوماتيك للحضور، وإعدادات السنة." />
      {settings.isPending ? (
        <Spinner />
      ) : settings.isError ? (
        <ErrorState message={errorMessage(settings.error)} onRetry={() => settings.refetch()} />
      ) : (
        <SettingsForm key={settings.data.updatedAt} settings={settings.data} />
      )}
    </>
  );
}

function SettingsForm({ settings }: { settings: Settings }) {
  const queryClient = useQueryClient();
  const [form, setForm] = useState({
    massPoints: String(settings.massPoints),
    servicePoints: String(settings.servicePoints),
    seasonStart: settings.seasonStart,
    seasonEnd: settings.seasonEnd,
    meetingDay: settings.meetingDay,
  });

  const save = useMutation({
    mutationFn: () =>
      api.put<Settings>("/settings", { ...form, massPoints: Number(form.massPoints), servicePoints: Number(form.servicePoints) }),
    onSuccess: (data) => {
      toast.success("اتحفظت الإعدادات");
      queryClient.setQueryData(["settings"], data);
      queryClient.invalidateQueries({ queryKey: ["meetings"] });
      queryClient.invalidateQueries({ queryKey: ["dashboard"] });
    },
    onError: (error) => {
      if (!(error instanceof ApiError && error.code === "VALIDATION_FAILED")) toast.error(errorMessage(error));
    },
  });
  const errors = fieldErrors(save.error);

  return (
    <form
      className="max-w-2xl space-y-4"
      onSubmit={(e) => {
        e.preventDefault();
        save.mutate();
      }}
    >
      <Card className="p-5">
        <h2 className="font-semibold">نقط الحضور</h2>
        <p className="mt-1 mb-4 text-sm text-stone-500">
          بتتضاف لوحدها لما تسجّل الحضور. التغيير بيسري على الحضور الجديد بس، والحضور القديم بيفضل بالنقط اللي اتضافت ساعتها.
        </p>
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label="نقط حضور القداس" error={errors.massPoints}>
            <div className="relative">
              <Church className="pointer-events-none absolute start-3 top-1/2 size-4 -translate-y-1/2 text-sky-600" />
              <Input type="number" min={0} max={1000} required className="ps-9" value={form.massPoints} onChange={(e) => setForm({ ...form, massPoints: e.target.value })} />
            </div>
          </Field>
          <Field label="نقط حضور الخدمة" error={errors.servicePoints}>
            <div className="relative">
              <HandHeart className="pointer-events-none absolute start-3 top-1/2 size-4 -translate-y-1/2 text-brand-600" />
              <Input type="number" min={0} max={1000} required className="ps-9" value={form.servicePoints} onChange={(e) => setForm({ ...form, servicePoints: e.target.value })} />
            </div>
          </Field>
        </div>
      </Card>

      <Card className="p-5">
        <h2 className="mb-4 font-semibold">السنة والاجتماعات</h2>
        <div className="grid gap-4 sm:grid-cols-3">
          <Field label="بداية السنة" error={errors.seasonStart}>
            <Input type="date" required value={form.seasonStart} onChange={(e) => setForm({ ...form, seasonStart: e.target.value })} />
          </Field>
          <Field label="نهاية السنة" error={errors.seasonEnd}>
            <Input type="date" required value={form.seasonEnd} onChange={(e) => setForm({ ...form, seasonEnd: e.target.value })} />
          </Field>
          <Field label="يوم الاجتماع">
            <Select value={form.meetingDay} onChange={(e) => setForm({ ...form, meetingDay: e.target.value as DayOfWeek })}>
              {DAYS.map((d) => (
                <option key={d} value={d}>
                  {dayLabel[d]}
                </option>
              ))}
            </Select>
          </Field>
        </div>
      </Card>

      <div className="flex flex-wrap items-center justify-between gap-3">
        <p className="text-xs text-stone-500">
          {settings.updatedBy ? `آخر تعديل: ${settings.updatedBy} · ${formatDateTime(settings.updatedAt)}` : ""}
        </p>
        <Button type="submit" loading={save.isPending}>
          حفظ الإعدادات
        </Button>
      </div>
    </form>
  );
}
