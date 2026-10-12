import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Coins, Pencil, Plus, UsersRound } from "lucide-react";
import { useState } from "react";
import { toast } from "sonner";
import { PointsDialog, type PointsTarget } from "@/components/PointsDialog";
import { Button, Card, Dialog, EmptyState, ErrorState, Field, Input, PageHeader, Spinner } from "@/components/ui";
import { api, ApiError } from "@/lib/api";
import { errorMessage, fieldErrors, formatNumber } from "@/lib/i18n";
import type { Group } from "@/lib/types";
import { copy } from "@/lib/copy";

export default function GroupsPage() {
  const groups = useQuery({ queryKey: ["groups"], queryFn: () => api.get<Group[]>("/groups") });
  const [editing, setEditing] = useState<Group | "new" | null>(null);
  const [points, setPoints] = useState<PointsTarget | null>(null);

  return (
    <>
      <PageHeader
        title={copy.groups.title}
        subtitle={copy.groups.subtitle}
        actions={
          <Button onClick={() => setEditing("new")}>
            <Plus className="size-4" />
            {copy.groups.add}
          </Button>
        }
      />
      {groups.isPending ? (
        <Spinner />
      ) : groups.isError ? (
        <ErrorState message={errorMessage(groups.error)} onRetry={() => groups.refetch()} />
      ) : groups.data.length === 0 ? (
        <Card>
          <EmptyState icon={<UsersRound className="size-10" />} title={copy.groups.empty} action={<Button onClick={() => setEditing("new")}>{copy.groups.addFirst}</Button>} />
        </Card>
      ) : (
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
          {groups.data.map((g) => (
            <Card key={g.id} className="p-4">
              <div className="flex items-start justify-between gap-2">
                <div>
                  <p className="text-lg font-semibold">{g.name}</p>
                  <p className="text-sm text-stone-500">{copy.common.studentsCount(g.memberCount)}</p>
                </div>
                <Button variant="ghost" size="icon" onClick={() => setEditing(g)} aria-label={copy.groups.renameLabel}>
                  <Pencil className="size-4" />
                </Button>
              </div>
              <div className="mt-4 flex items-center justify-between">
                <p className="text-3xl font-bold tabular">
                  {formatNumber(g.pointsBalance)} <span className="text-sm font-normal text-stone-500">{copy.common.point}</span>
                </p>
                <Button variant="secondary" size="sm" onClick={() => setPoints({ type: "groups", id: g.id, name: g.name, balance: g.pointsBalance })}>
                  <Coins className="size-4" />
                  {copy.common.points}
                </Button>
              </div>
            </Card>
          ))}
        </div>
      )}

      <Dialog open={Boolean(editing)} onClose={() => setEditing(null)} title={editing === "new" ? copy.groups.newTitle : copy.groups.renameTitle}>
        {editing && <GroupForm key={editing === "new" ? "new" : editing.id} group={editing === "new" ? null : editing} onClose={() => setEditing(null)} />}
      </Dialog>
      <PointsDialog target={points} onClose={() => setPoints(null)} />
    </>
  );
}

function GroupForm({ group, onClose }: { group: Group | null; onClose: () => void }) {
  const queryClient = useQueryClient();
  const [name, setName] = useState(group?.name ?? "");
  const save = useMutation({
    mutationFn: () => (group ? api.put(`/groups/${group.id}`, { name }) : api.post("/groups", { name })),
    onSuccess: () => {
      toast.success(group ? copy.groups.renamed : copy.groups.added);
      queryClient.invalidateQueries({ queryKey: ["groups"] });
      queryClient.invalidateQueries({ queryKey: ["dashboard"] });
      onClose();
    },
    onError: (error) => {
      if (!(error instanceof ApiError && error.code === "VALIDATION_FAILED")) toast.error(errorMessage(error));
    },
  });
  return (
    <form
      className="space-y-4"
      onSubmit={(e) => {
        e.preventDefault();
        save.mutate();
      }}
    >
      <Field label={copy.groups.name} error={fieldErrors(save.error).name}>
        <Input required minLength={2} maxLength={60} autoFocus value={name} onChange={(e) => setName(e.target.value)} />
      </Field>
      <Button type="submit" className="w-full" loading={save.isPending}>
        {copy.common.save}
      </Button>
    </form>
  );
}
