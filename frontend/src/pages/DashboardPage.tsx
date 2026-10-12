import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { CalendarDays, Church, HandHeart, Search, Users } from "lucide-react";
import { useDeferredValue, useState } from "react";
import { Link } from "react-router";
import { useAuth } from "@/auth/AuthProvider";
import { RankBadge, StatTile, StudentCardView } from "@/components/stats";
import { Card, EmptyState, ErrorState, Input, PageHeader, Pagination, Segmented, Select, Spinner } from "@/components/ui";
import { api } from "@/lib/api";
import { errorMessage, formatDate, formatNumber, gradeLabel, GRADES } from "@/lib/i18n";
import type { DashboardSummary, Grade, Page, StudentCard } from "@/lib/types";
import { copy } from "@/lib/copy";

type Sort = "POINTS" | "ATTENDANCE" | "NAME";

export default function DashboardPage() {
  const { user } = useAuth();
  const summary = useQuery({ queryKey: ["dashboard"], queryFn: () => api.get<DashboardSummary>("/dashboard") });

  return (
    <>
      <PageHeader title={copy.dashboard.greeting(user?.displayName.split(" ")[0] ?? "")} />

      {summary.isPending ? (
        <Spinner />
      ) : summary.isError ? (
        <ErrorState message={errorMessage(summary.error)} onRetry={() => summary.refetch()} />
      ) : (
        <SummarySection data={summary.data} />
      )}

      <StudentsGrid groups={summary.data?.groups ?? []} />
    </>
  );
}

function SummarySection({ data }: { data: DashboardSummary }) {
  const last = data.lastMeeting;
  const top = data.topStudents.filter((s) => s.points > 0);
  return (
    <div className="space-y-4">
      <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
        <StatTile label={copy.common.students} value={formatNumber(data.totalStudents)} hint={copy.common.groupsCount(data.totalGroups)} icon={<Users className="size-4" />} tone="stone" />
        <StatTile label={copy.dashboard.meetingsHeld} value={formatNumber(data.meetingsHeld)} hint={last ? copy.dashboard.lastMeeting(formatDate(last.date)) : copy.dashboard.none} icon={<CalendarDays className="size-4" />} tone="gold" />
        <StatTile label={copy.dashboard.avgService} value={`${formatNumber(data.averageServiceRate)}٪`} hint={last ? copy.dashboard.lastFriday(last.serviceCount) : undefined} icon={<HandHeart className="size-4" />} tone="brand" />
        <StatTile label={copy.dashboard.avgMass} value={`${formatNumber(data.averageMassRate)}٪`} hint={last ? copy.dashboard.lastFriday(last.massCount) : undefined} icon={<Church className="size-4" />} tone="blue" />
      </div>

      <div className="grid gap-4 lg:grid-cols-2">
        <Card className="p-5">
          <h2 className="mb-3 font-semibold">{copy.dashboard.top}</h2>
          {top.length === 0 ? (
            <p className="py-6 text-center text-sm text-stone-500">{copy.dashboard.noPoints}</p>
          ) : (
            <ol className="space-y-1">
              {top.map((s) => (
                <li key={s.id}>
                  <Link to={`/s/${s.id}`} className="flex items-center gap-3 rounded-xl px-2 py-2 hover:bg-stone-50">
                    <RankBadge rank={s.rank} />
                    <span className="min-w-0 flex-1 truncate font-medium">{s.name}</span>
                    <span className="text-xs text-stone-500">{gradeLabel[s.grade]}</span>
                    <span className="w-14 text-start font-semibold tabular">{formatNumber(s.points)}</span>
                  </Link>
                </li>
              ))}
            </ol>
          )}
        </Card>

        <Card className="p-5">
          <h2 className="mb-3 font-semibold">{copy.dashboard.groupRanking}</h2>
          {data.groups.length === 0 ? (
            <p className="py-6 text-center text-sm text-stone-500">{copy.dashboard.noGroups}</p>
          ) : (
            <ol className="space-y-1">
              {data.groups.map((g) => (
                <li key={g.id} className="flex items-center gap-3 rounded-xl px-2 py-2">
                  <RankBadge rank={g.rank} />
                  <span className="min-w-0 flex-1 truncate font-medium">{g.name}</span>
                  <span className="text-xs text-stone-500">{copy.common.studentsCount(g.memberCount)}</span>
                  <span className="w-14 text-start font-semibold tabular">{formatNumber(g.points)}</span>
                </li>
              ))}
            </ol>
          )}
        </Card>
      </div>
    </div>
  );
}

function StudentsGrid({ groups }: { groups: DashboardSummary["groups"] }) {
  const [search, setSearch] = useState("");
  const [grade, setGrade] = useState<Grade | "">("");
  const [groupId, setGroupId] = useState("");
  const [sort, setSort] = useState<Sort>("POINTS");
  const [page, setPage] = useState(0);
  const q = useDeferredValue(search);

  const cards = useQuery({
    queryKey: ["dashboard", "students", q, grade, groupId, sort, page],
    queryFn: () => api.get<Page<StudentCard>>("/dashboard/students", { q, grade, groupId, sort, page, size: 24 }),
    placeholderData: keepPreviousData,
  });

  const resetPage = <T,>(setter: (v: T) => void) => (value: T) => {
    setter(value);
    setPage(0);
  };

  return (
    <section className="mt-8">
      <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
        <h2 className="text-lg font-bold">{copy.common.students}</h2>
        <Segmented
          value={sort}
          onChange={resetPage(setSort)}
          options={[
            { value: "POINTS", label: copy.dashboard.sortPoints },
            { value: "ATTENDANCE", label: copy.dashboard.sortAttendance },
            { value: "NAME", label: copy.dashboard.sortName },
          ]}
        />
      </div>

      <div className="mb-4 grid gap-2 sm:grid-cols-[1fr_auto_auto]">
        <div className="relative">
          <Search className="pointer-events-none absolute start-3 top-1/2 size-4 -translate-y-1/2 text-stone-400" />
          <Input placeholder={copy.common.searchByName} className="ps-9" value={search} onChange={(e) => resetPage(setSearch)(e.target.value)} />
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
          {groups.map((g) => (
            <option key={g.id} value={g.id}>
              {g.name}
            </option>
          ))}
        </Select>
      </div>

      {cards.isPending ? (
        <Spinner />
      ) : cards.isError ? (
        <ErrorState message={errorMessage(cards.error)} onRetry={() => cards.refetch()} />
      ) : cards.data.items.length === 0 ? (
        <Card>
          <EmptyState icon={<Users className="size-10" />} title={copy.common.noStudents} text={copy.common.tryOtherFilter} />
        </Card>
      ) : (
        <>
          <div className={`grid gap-3 sm:grid-cols-2 xl:grid-cols-3 ${cards.isPlaceholderData ? "opacity-60" : ""}`}>
            {cards.data.items.map((card) => (
              <StudentCardView key={card.id} card={card} />
            ))}
          </div>
          <Pagination page={cards.data.page} totalPages={cards.data.totalPages} totalItems={cards.data.totalItems} onChange={setPage} />
        </>
      )}
    </section>
  );
}
