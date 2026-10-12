import { useQueryClient } from "@tanstack/react-query";
import { Clock, LogOut, RefreshCw, XCircle } from "lucide-react";
import { Navigate } from "react-router";
import { useAuth } from "@/auth/AuthProvider";
import { Brand } from "@/components/Layout";
import { Button, Card } from "@/components/ui";
import { copy } from "@/lib/copy";

export default function PendingPage() {
  const { user, signOut } = useAuth();
  const queryClient = useQueryClient();
  if (user?.status === "APPROVED") return <Navigate to="/" replace />;
  const rejected = user?.status === "REJECTED";

  return (
    <div className="flex min-h-dvh flex-col items-center justify-center px-5">
      <div className="mb-8">
        <Brand compact />
      </div>
      <Card className="w-full max-w-md p-8 text-center">
        <span
          className={`mx-auto mb-4 grid size-14 place-items-center rounded-2xl ${rejected ? "bg-red-50 text-red-600" : "bg-gold-100 text-gold-600"}`}
        >
          {rejected ? <XCircle className="size-7" /> : <Clock className="size-7" />}
        </span>
        <h1 className="text-xl font-bold">{rejected ? copy.pending.rejectedTitle : copy.pending.waitingTitle}</h1>
        <p className="mt-2 text-sm text-stone-500">
          {rejected
            ? copy.pending.rejectedText
            : copy.pending.waitingText(user?.displayName ?? "")}
        </p>
        <div className="mt-6 flex justify-center gap-2">
          {!rejected && (
            <Button variant="secondary" onClick={() => queryClient.invalidateQueries({ queryKey: ["me"] })}>
              <RefreshCw className="size-4" />
              {copy.pending.refresh}
            </Button>
          )}
          <Button variant="ghost" onClick={signOut}>
            <LogOut className="size-4" />
            {copy.pending.signOut}
          </Button>
        </div>
      </Card>
    </div>
  );
}
