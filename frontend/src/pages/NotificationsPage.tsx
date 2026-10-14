import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { BellRing, Send } from "lucide-react";
import { useState, type FormEvent } from "react";
import { toast } from "sonner";
import { Badge, Button, Card, Dialog, EmptyState, ErrorState, Field, Input, PageHeader, Segmented, Spinner, Textarea } from "@/components/ui";
import { api } from "@/lib/api";
import { errorMessage, formatDateTime } from "@/lib/i18n";
import type { Audience, Broadcast, PushConfig, Recipients } from "@/lib/types";
import { copy } from "@/lib/copy";

/** The super admin writes a message and sends it to everyone's phones (or just the admins'). */
export default function NotificationsPage() {
  const queryClient = useQueryClient();
  const [title, setTitle] = useState("");
  const [body, setBody] = useState("");
  const [audience, setAudience] = useState<Audience>("ALL");
  const [confirming, setConfirming] = useState(false);

  const config = useQuery({ queryKey: ["push-config"], queryFn: () => api.get<PushConfig>("/push/config") });
  const recipients = useQuery({
    queryKey: ["notification-recipients", audience],
    queryFn: () => api.get<Recipients>("/notifications/recipients", { audience }),
    enabled: config.data?.enabled === true,
  });
  const history = useQuery({ queryKey: ["broadcasts"], queryFn: () => api.get<Broadcast[]>("/notifications/broadcasts") });

  const send = useMutation({
    mutationFn: () => api.post<Broadcast>("/notifications/broadcast", { title: title.trim(), body: body.trim(), audience }),
    onSuccess: (sent) => {
      toast.success(copy.notifications.sent(sent.devices));
      setTitle("");
      setBody("");
      queryClient.invalidateQueries({ queryKey: ["broadcasts"] });
    },
    onError: (error) => toast.error(errorMessage(error)),
    onSettled: () => setConfirming(false),
  });

  const devices = recipients.data?.devices ?? 0;
  const canSend = config.data?.enabled === true && title.trim() !== "" && body.trim() !== "" && devices > 0;

  const submit = (event: FormEvent) => {
    event.preventDefault();
    if (canSend) setConfirming(true);
  };

  return (
    <>
      <PageHeader title={copy.notifications.title} subtitle={copy.notifications.subtitle} />

      {config.isPending ? (
        <Spinner />
      ) : config.isError ? (
        <ErrorState message={errorMessage(config.error)} onRetry={() => config.refetch()} />
      ) : (
        <div className="grid gap-4 lg:grid-cols-2">
          <Card className="p-5">
            {!config.data.enabled && (
              <p className="mb-4 rounded-xl bg-amber-50 px-3 py-2 text-sm text-amber-800">{copy.notifications.notConfigured}</p>
            )}
            <form onSubmit={submit} className="space-y-4">
              <Field label={copy.notifications.titleLabel}>
                <Input required maxLength={100} placeholder={copy.notifications.titlePlaceholder} value={title} onChange={(e) => setTitle(e.target.value)} />
              </Field>
              <Field label={copy.notifications.bodyLabel}>
                <Textarea required maxLength={300} placeholder={copy.notifications.bodyPlaceholder} value={body} onChange={(e) => setBody(e.target.value)} />
              </Field>
              <div className="space-y-1.5">
                <span className="text-sm font-medium text-stone-700">{copy.notifications.audienceLabel}</span>
                <Segmented
                  className="w-full"
                  value={audience}
                  onChange={setAudience}
                  options={[
                    { value: "ALL", label: copy.notifications.audienceAll },
                    { value: "ADMINS", label: copy.notifications.audienceAdmins },
                  ]}
                />
                {config.data.enabled && recipients.data && (
                  <p className="text-xs text-stone-500">
                    {recipients.data.devices > 0
                      ? copy.notifications.recipients(recipients.data.devices, recipients.data.accounts)
                      : copy.notifications.noDevices}
                  </p>
                )}
              </div>
              <Button type="submit" className="w-full" disabled={!canSend}>
                <Send className="size-4" />
                {copy.notifications.send}
              </Button>
            </form>
          </Card>

          <Card className="p-5">
            <h2 className="mb-3 font-semibold">{copy.notifications.historyTitle}</h2>
            {history.isPending ? (
              <Spinner className="py-6" />
            ) : history.data && history.data.length > 0 ? (
              <ul className="divide-y divide-stone-100">
                {history.data.map((item) => (
                  <li key={item.id} className="py-3">
                    <div className="flex items-start justify-between gap-3">
                      <p className="font-medium">{item.title}</p>
                      <Badge tone={item.audience === "ALL" ? "brand" : "stone"}>
                        {item.audience === "ALL" ? copy.notifications.audienceAll : copy.notifications.audienceAdmins}
                      </Badge>
                    </div>
                    <p className="mt-0.5 text-sm text-stone-600">{item.body}</p>
                    <p className="mt-1 text-xs text-stone-500">
                      {[item.sentBy ? copy.notifications.sentBy(item.sentBy) : null, copy.notifications.devicesCount(item.devices), formatDateTime(item.createdAt)]
                        .filter(Boolean)
                        .join(" · ")}
                    </p>
                  </li>
                ))}
              </ul>
            ) : (
              <EmptyState icon={<BellRing className="size-10" />} title={copy.notifications.historyEmpty} />
            )}
          </Card>
        </div>
      )}

      <Dialog open={confirming} onClose={() => setConfirming(false)} title={copy.notifications.confirmTitle}>
        <div className="space-y-4">
          <div className="rounded-2xl bg-stone-50 p-4">
            <p className="font-semibold">{title}</p>
            <p className="mt-1 text-sm text-stone-600">{body}</p>
          </div>
          <p className="text-sm text-stone-600">{copy.notifications.confirmText(devices)}</p>
          <div className="flex gap-2">
            <Button className="flex-1" loading={send.isPending} onClick={() => send.mutate()}>
              {copy.notifications.confirmSend}
            </Button>
            <Button variant="ghost" onClick={() => setConfirming(false)}>
              {copy.common.cancel}
            </Button>
          </div>
        </div>
      </Dialog>
    </>
  );
}
