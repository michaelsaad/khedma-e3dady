import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Check, Link2, ShieldCheck, X } from "lucide-react";
import { useDeferredValue, useMemo, useState } from "react";
import { toast } from "sonner";
import { useAuth } from "@/auth/AuthProvider";
import { Avatar, Badge, Button, Card, Dialog, EmptyState, ErrorState, Input, PageHeader, Segmented, Select, Spinner } from "@/components/ui";
import { api } from "@/lib/api";
import { errorMessage, formatDateTime, gradeLabel, roleLabel, statusLabel } from "@/lib/i18n";
import type { Page, Role, Student, User, UserStatus } from "@/lib/types";
import { copy } from "@/lib/copy";

const ROLES: Role[] = ["USER", "ADMIN", "SUPER_ADMIN"];

export default function UsersPage() {
  const { user: me } = useAuth();
  const queryClient = useQueryClient();
  const [filter, setFilter] = useState<UserStatus>("PENDING");
  const [linking, setLinking] = useState<User | null>(null);
  const users = useQuery({ queryKey: ["users"], queryFn: () => api.get<User[]>("/users") });

  const update = useMutation({
    mutationFn: ({ id, ...body }: { id: number; role?: Role; status?: UserStatus }) => api.patch<User>(`/users/${id}`, body),
    onSuccess: () => {
      toast.success(copy.users.saved);
      queryClient.invalidateQueries({ queryKey: ["users"] });
    },
    onError: (error) => toast.error(errorMessage(error)),
  });

  const counts = useMemo(() => {
    const c: Record<UserStatus, number> = { PENDING: 0, APPROVED: 0, REJECTED: 0 };
    users.data?.forEach((u) => c[u.status]++);
    return c;
  }, [users.data]);
  const visible = users.data?.filter((u) => u.status === filter) ?? [];

  return (
    <>
      <PageHeader title={copy.users.title} subtitle={copy.users.subtitle} />
      <Segmented
        className="mb-4"
        value={filter}
        onChange={setFilter}
        options={(["PENDING", "APPROVED", "REJECTED"] as UserStatus[]).map((s) => ({
          value: s,
          label: `${statusLabel[s]} (${counts[s]})`,
        }))}
      />

      {users.isPending ? (
        <Spinner />
      ) : users.isError ? (
        <ErrorState message={errorMessage(users.error)} onRetry={() => users.refetch()} />
      ) : visible.length === 0 ? (
        <Card>
          <EmptyState icon={<ShieldCheck className="size-10" />} title={filter === "PENDING" ? copy.users.noRequests : copy.users.empty} />
        </Card>
      ) : (
        <Card>
          <ul className="divide-y divide-stone-100">
            {visible.map((u) => {
              const self = u.id === me?.id;
              return (
                <li key={u.id} className="flex flex-wrap items-center gap-3 px-4 py-3">
                  <Avatar name={u.displayName} url={u.avatarUrl} />
                  <div className="min-w-0 flex-1">
                    <p className="truncate font-medium">
                      {u.displayName} {self && <Badge tone="brand">{copy.users.you}</Badge>}
                    </p>
                    <p className="truncate text-xs text-stone-500" dir="ltr">
                      {u.email}
                    </p>
                    {u.lastLoginAt && <p className="text-xs text-stone-400">{copy.users.lastLogin(formatDateTime(u.lastLoginAt))}</p>}
                  </div>

                  {u.status === "PENDING" ? (
                    <div className="flex gap-2">
                      <Button size="sm" onClick={() => update.mutate({ id: u.id, status: "APPROVED" })}>
                        <Check className="size-4" />
                        {copy.users.approve}
                      </Button>
                      <Button size="sm" variant="outline" onClick={() => update.mutate({ id: u.id, status: "REJECTED" })}>
                        <X className="size-4" />
                        {copy.users.reject}
                      </Button>
                    </div>
                  ) : (
                    <div className="flex flex-wrap items-center gap-2">
                      <Select
                        className="h-9 w-32"
                        value={u.role}
                        disabled={self}
                        onChange={(e) => update.mutate({ id: u.id, role: e.target.value as Role })}
                        aria-label={copy.users.role}
                      >
                        {ROLES.map((r) => (
                          <option key={r} value={r}>
                            {roleLabel[r]}
                          </option>
                        ))}
                      </Select>
                      <Button size="sm" variant="outline" onClick={() => setLinking(u)} title={copy.users.linkTitle}>
                        <Link2 className="size-4" />
                        {u.studentId ? copy.users.linked : copy.users.link}
                      </Button>
                      {!self && (
                        <Button
                          size="sm"
                          variant="ghost"
                          onClick={() => update.mutate({ id: u.id, status: u.status === "APPROVED" ? "REJECTED" : "APPROVED" })}
                        >
                          {u.status === "APPROVED" ? copy.users.disable : copy.users.enable}
                        </Button>
                      )}
                    </div>
                  )}
                </li>
              );
            })}
          </ul>
        </Card>
      )}

      <Dialog open={Boolean(linking)} onClose={() => setLinking(null)} title={copy.users.linkDialogTitle}>
        {linking && <LinkStudent user={linking} onDone={() => setLinking(null)} />}
      </Dialog>
    </>
  );
}

function LinkStudent({ user, onDone }: { user: User; onDone: () => void }) {
  const queryClient = useQueryClient();
  const [search, setSearch] = useState("");
  const q = useDeferredValue(search.trim());
  const students = useQuery({
    queryKey: ["students", "link", q],
    queryFn: () => api.get<Page<Student>>("/students", { q, size: 10 }),
  });
  const link = useMutation({
    mutationFn: (studentId: number | null) => api.put<User>(`/users/${user.id}/student`, { studentId }),
    onSuccess: () => {
      toast.success(copy.users.linkSaved);
      queryClient.invalidateQueries({ queryKey: ["users"] });
      onDone();
    },
    onError: (error) => toast.error(errorMessage(error)),
  });

  return (
    <div className="space-y-3">
      <p className="text-sm text-stone-500">{copy.users.linkHint}</p>
      <Input placeholder={copy.common.searchByName} autoFocus value={search} onChange={(e) => setSearch(e.target.value)} />
      {students.isPending ? (
        <Spinner className="py-6" />
      ) : (
        <ul className="divide-y divide-stone-100 rounded-xl border border-stone-200">
          {students.data?.items.map((s) => (
            <li key={s.id}>
              <button
                className="flex w-full items-center justify-between px-3 py-2.5 text-start hover:bg-stone-50 disabled:opacity-50"
                disabled={link.isPending}
                onClick={() => link.mutate(s.id)}
              >
                <span className="font-medium">{s.name}</span>
                <span className="flex items-center gap-2 text-xs text-stone-500">
                  {gradeLabel[s.grade]}
                  {user.studentId === s.id && <Badge tone="brand">{copy.users.linked}</Badge>}
                </span>
              </button>
            </li>
          ))}
        </ul>
      )}
      {user.studentId && (
        <Button variant="outline" className="w-full" loading={link.isPending} onClick={() => link.mutate(null)}>
          {copy.users.unlink}
        </Button>
      )}
    </div>
  );
}
