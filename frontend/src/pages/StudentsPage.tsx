import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Coins, Pencil, Phone, Plus, Search, Trash2, Users } from "lucide-react";
import { useDeferredValue, useState } from "react";
import { toast } from "sonner";
import { PointsDialog, type PointsTarget } from "@/components/PointsDialog";
import { Badge, Button, Card, Dialog, EmptyState, ErrorState, Field, Input, PageHeader, Pagination, Select, Spinner } from "@/components/ui";
import { api, ApiError } from "@/lib/api";
import { errorMessage, fieldErrors, formatNumber, gradeLabel, gradeLong, GRADES } from "@/lib/i18n";
import type { Grade, Group, Page, Student } from "@/lib/types";
import { copy } from "@/lib/copy";

export default function StudentsPage() {
  const [search, setSearch] = useState("");
  const [grade, setGrade] = useState<Grade | "">("");
  const [groupId, setGroupId] = useState("");
  const [page, setPage] = useState(0);
  const [editing, setEditing] = useState<Student | "new" | null>(null);
  const [removing, setRemoving] = useState<Student | null>(null);
  const [points, setPoints] = useState<PointsTarget | null>(null);
  const q = useDeferredValue(search.trim());

  const groups = useQuery({ queryKey: ["groups"], queryFn: () => api.get<Group[]>("/groups") });
  const students = useQuery({
    queryKey: ["students", q, grade, groupId, page],
    queryFn: () => api.get<Page<Student>>("/students", { q, grade, groupId, page, size: 25 }),
    placeholderData: keepPreviousData,
  });

  const resetPage = <T,>(setter: (v: T) => void) => (value: T) => {
    setter(value);
    setPage(0);
  };

  return (
    <>
      <PageHeader
        title={copy.students.title}
        subtitle={students.data ? copy.common.studentsCount(students.data.totalItems) : undefined}
        actions={
          <Button onClick={() => setEditing("new")}>
            <Plus className="size-4" />
            {copy.students.add}
          </Button>
        }
      />

      <div className="mb-4 grid gap-2 sm:grid-cols-[1fr_auto_auto]">
        <div className="relative">
          <Search className="pointer-events-none absolute start-3 top-1/2 size-4 -translate-y-1/2 text-stone-400" />
          <Input placeholder={copy.students.searchPlaceholder} className="ps-9" value={search} onChange={(e) => resetPage(setSearch)(e.target.value)} />
        </div>
        <Select value={grade} onChange={(e) => resetPage(setGrade)(e.target.value as Grade | "")}>
          <option value="">{copy.common.allGrades}</option>
          {GRADES.map((g) => (
            <option key={g} value={g}>
              {gradeLabel[g]}
            </option>
          ))}
        </Select>
        <Select value={groupId} onChange={(e) => resetPage(setGroupId)(e.target.value)}>
          <option value="">{copy.common.allGroups}</option>
          {groups.data?.map((g) => (
            <option key={g.id} value={g.id}>
              {g.name}
            </option>
          ))}
        </Select>
      </div>

      {students.isPending ? (
        <Spinner />
      ) : students.isError ? (
        <ErrorState message={errorMessage(students.error)} onRetry={() => students.refetch()} />
      ) : students.data.items.length === 0 ? (
        <Card>
          <EmptyState
            icon={<Users className="size-10" />}
            title={copy.common.noStudents}
            text={q || grade || groupId ? copy.common.tryOtherFilter : copy.students.startAdding}
            action={!q && !grade && !groupId && <Button onClick={() => setEditing("new")}>{copy.students.addFirst}</Button>}
          />
        </Card>
      ) : (
        <>
          <Card className={students.isPlaceholderData ? "opacity-60" : ""}>
            <ul className="divide-y divide-stone-100">
              {students.data.items.map((s) => (
                <li key={s.id} className="flex flex-wrap items-center gap-3 px-4 py-3">
                  <div className="min-w-0 flex-1">
                    <p className="truncate font-medium">{s.name}</p>
                    <div className="mt-1 flex flex-wrap items-center gap-1.5 text-xs text-stone-500">
                      <Badge>{gradeLabel[s.grade]}</Badge>
                      {s.groupName && <Badge tone="blue">{s.groupName}</Badge>}
                      {s.phone && (
                        <a href={`tel:${s.phone}`} className="inline-flex items-center gap-1 hover:text-brand-700" dir="ltr">
                          <Phone className="size-3" />
                          {s.phone}
                        </a>
                      )}
                    </div>
                  </div>
                  <button
                    onClick={() => setPoints({ type: "students", id: s.id, name: s.name, balance: s.pointsBalance })}
                    aria-label={copy.students.pointsLabel(s.name)}
                    className="flex items-center gap-1.5 rounded-xl bg-gold-100/70 px-3 py-1.5 text-sm font-semibold text-amber-900 hover:bg-gold-100"
                  >
                    <Coins className="size-4" />
                    <span className="tabular">{formatNumber(s.pointsBalance)}</span>
                  </button>
                  <div className="flex">
                    <Button variant="ghost" size="icon" onClick={() => setEditing(s)} aria-label={copy.common.edit}>
                      <Pencil className="size-4" />
                    </Button>
                    <Button variant="ghost" size="icon" onClick={() => setRemoving(s)} aria-label={copy.common.remove} className="hover:text-red-600">
                      <Trash2 className="size-4" />
                    </Button>
                  </div>
                </li>
              ))}
            </ul>
          </Card>
          <Pagination page={students.data.page} totalPages={students.data.totalPages} totalItems={students.data.totalItems} onChange={setPage} />
        </>
      )}

      <StudentForm student={editing} groups={groups.data ?? []} onClose={() => setEditing(null)} />
      <RemoveDialog student={removing} onClose={() => setRemoving(null)} />
      <PointsDialog target={points} onClose={() => setPoints(null)} />
    </>
  );
}

function StudentForm({ student, groups, onClose }: { student: Student | "new" | null; groups: Group[]; onClose: () => void }) {
  const isNew = student === "new";
  const existing = student && student !== "new" ? student : null;
  return (
    <Dialog open={Boolean(student)} onClose={onClose} title={isNew ? copy.students.newTitle : copy.students.editTitle}>
      {student && <StudentFormBody key={existing?.id ?? "new"} existing={existing} groups={groups} onClose={onClose} />}
    </Dialog>
  );
}

function StudentFormBody({ existing, groups, onClose }: { existing: Student | null; groups: Group[]; onClose: () => void }) {
  const queryClient = useQueryClient();
  const [form, setForm] = useState({
    name: existing?.name ?? "",
    grade: existing?.grade ?? ("FIRST" as Grade),
    phone: existing?.phone ?? "",
    groupId: existing?.groupId ? String(existing.groupId) : "",
  });

  const save = useMutation({
    mutationFn: () => {
      const body = { ...form, groupId: form.groupId ? Number(form.groupId) : null };
      return existing ? api.put<Student>(`/students/${existing.id}`, body) : api.post<Student>("/students", body);
    },
    onSuccess: (s) => {
      toast.success(existing ? copy.students.savedChanges : copy.students.added(s.name));
      queryClient.invalidateQueries({ queryKey: ["students"] });
      queryClient.invalidateQueries({ queryKey: ["dashboard"] });
      queryClient.invalidateQueries({ queryKey: ["attendance"] });
      queryClient.invalidateQueries({ queryKey: ["groups"] });
      onClose();
    },
    onError: (error) => {
      if (!(error instanceof ApiError && error.code === "VALIDATION_FAILED")) toast.error(errorMessage(error));
    },
  });
  const errors = fieldErrors(save.error);

  return (
    <form
      className="space-y-4"
      onSubmit={(e) => {
        e.preventDefault();
        save.mutate();
      }}
    >
      <Field label={copy.common.name} error={errors.name}>
        <Input required minLength={2} maxLength={100} autoFocus value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} />
      </Field>
      <Field label={copy.students.grade} error={errors.grade}>
        <Select value={form.grade} onChange={(e) => setForm({ ...form, grade: e.target.value as Grade })}>
          {GRADES.map((g) => (
            <option key={g} value={g}>
              {gradeLong[g]}
            </option>
          ))}
        </Select>
      </Field>
      <Field label={copy.students.phone} error={errors.phone} hint={copy.common.optional}>
        <Input type="tel" dir="ltr" maxLength={30} value={form.phone} onChange={(e) => setForm({ ...form, phone: e.target.value })} />
      </Field>
      <Field label={copy.students.group}>
        <Select value={form.groupId} onChange={(e) => setForm({ ...form, groupId: e.target.value })}>
          <option value="">{copy.students.noGroup}</option>
          {groups.map((g) => (
            <option key={g.id} value={g.id}>
              {g.name}
            </option>
          ))}
        </Select>
      </Field>
      <Button type="submit" className="w-full" loading={save.isPending}>
        {existing ? copy.common.save : copy.common.add}
      </Button>
    </form>
  );
}

function RemoveDialog({ student, onClose }: { student: Student | null; onClose: () => void }) {
  const queryClient = useQueryClient();
  const remove = useMutation({
    mutationFn: (id: number) => api.delete(`/students/${id}`),
    onSuccess: () => {
      toast.success(copy.students.removed);
      queryClient.invalidateQueries({ queryKey: ["students"] });
      queryClient.invalidateQueries({ queryKey: ["dashboard"] });
      queryClient.invalidateQueries({ queryKey: ["attendance"] });
      onClose();
    },
    onError: (error) => toast.error(errorMessage(error)),
  });
  return (
    <Dialog
      open={Boolean(student)}
      onClose={onClose}
      title={copy.students.removeTitle}
      footer={
        <>
          <Button variant="outline" className="flex-1" onClick={onClose}>
            {copy.common.cancel}
          </Button>
          <Button variant="danger" className="flex-1" loading={remove.isPending} onClick={() => student && remove.mutate(student.id)}>
            {copy.common.remove}
          </Button>
        </>
      }
    >
      <p className="text-sm text-stone-600">
        {copy.students.removeBefore} <span className="font-semibold text-stone-900">{student?.name}</span> {copy.students.removeAfter}
      </p>
    </Dialog>
  );
}
