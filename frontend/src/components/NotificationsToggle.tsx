import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Bell, BellOff, BellRing, Smartphone } from "lucide-react";
import { toast } from "sonner";
import { useAuth } from "@/auth/AuthProvider";
import { copy } from "@/lib/copy";
import { disablePush, enablePush, PushPermissionDenied, pushState } from "@/lib/push";
import { cn } from "@/lib/utils";

/** "Turn notifications on for this device": one row for the account menu. Hidden where push can't work. */
export function NotificationsToggle() {
  const { user } = useAuth();
  const queryClient = useQueryClient();
  const userId = user?.id ?? 0;
  const queryKey = ["push-state", userId];

  const state = useQuery({ queryKey, queryFn: () => pushState(userId), enabled: Boolean(user), staleTime: 30_000 });

  const toggle = useMutation({
    mutationFn: async (turnOn: boolean) => {
      if (turnOn) await enablePush(userId);
      else await disablePush(userId);
      return turnOn;
    },
    onSuccess: (turnedOn) => toast.success(turnedOn ? copy.push.enabledToast : copy.push.disabledToast),
    onError: (error) => toast.error(error instanceof PushPermissionDenied ? copy.push.denied : copy.push.failed),
    onSettled: () => queryClient.invalidateQueries({ queryKey }),
  });

  const current = state.data;
  if (!user || !current || current === "unsupported" || current === "unavailable") return null;

  if (current === "needs-install" || current === "blocked") {
    return (
      <p className="flex items-start gap-2 rounded-xl bg-stone-50 px-3 py-2.5 text-xs leading-relaxed text-stone-500">
        {current === "needs-install" ? <Smartphone className="mt-0.5 size-4 shrink-0" /> : <BellOff className="mt-0.5 size-4 shrink-0" />}
        {current === "needs-install" ? copy.push.needsInstall : copy.push.blocked}
      </p>
    );
  }

  const on = current === "on";
  return (
    <button
      type="button"
      onClick={() => toggle.mutate(!on)}
      disabled={toggle.isPending}
      aria-pressed={on}
      className={cn(
        "flex w-full items-center gap-3 rounded-xl px-3 py-2.5 text-sm font-medium transition-colors disabled:opacity-60",
        on ? "bg-brand-50 text-brand-800 hover:bg-brand-100" : "text-stone-600 hover:bg-stone-100 hover:text-stone-900",
      )}
    >
      {on ? <BellRing className="size-5" /> : <Bell className="size-5" />}
      {toggle.isPending ? copy.push.turningOn : on ? copy.push.on : copy.push.off}
    </button>
  );
}
