import { useQuery } from "@tanstack/react-query";
import { ArrowRight, Church, HandHeart, Trophy } from "lucide-react";
import { Link, useParams } from "react-router";
import { AttendanceHistory } from "@/components/AttendanceHistory";
import { RankBadge, RateRow } from "@/components/stats";
import { TransactionList } from "@/components/TransactionList";
import { Avatar, Badge, Card, ErrorState, Spinner } from "@/components/ui";
import { api } from "@/lib/api";
import { errorMessage, formatNumber, gradeLong } from "@/lib/i18n";
import type { StudentDetail } from "@/lib/types";

export default function StudentDetailPage() {
  const { id } = useParams();
  const detail = useQuery({
    queryKey: ["dashboard", "student", id],
    queryFn: () => api.get<StudentDetail>(`/dashboard/students/${id}`),
  });

  return (
    <>
      <Link to="/" className="mb-4 inline-flex items-center gap-1 text-sm text-stone-500 hover:text-stone-800">
        <ArrowRight className="size-4" />
        الرئيسية
      </Link>
      {detail.isPending ? (
        <Spinner />
      ) : detail.isError ? (
        <ErrorState message={errorMessage(detail.error)} />
      ) : (
        <StudentProfile detail={detail.data} />
      )}
    </>
  );
}

export function StudentProfile({ detail }: { detail: StudentDetail }) {
  const { card } = detail;
  return (
    <div className="space-y-4">
      <Card className="p-5">
        <div className="flex flex-wrap items-center gap-4">
          <Avatar name={card.name} className="size-14 text-lg" />
          <div className="min-w-0 flex-1">
            <h1 className="text-xl font-bold">{card.name}</h1>
            <div className="mt-1 flex flex-wrap gap-1.5">
              <Badge>{gradeLong[card.grade]}</Badge>
              {card.groupName && <Badge tone="blue">{card.groupName}</Badge>}
            </div>
          </div>
          <div className="flex items-center gap-3 rounded-2xl bg-gold-100/60 px-4 py-3">
            <Trophy className="size-6 text-gold-600" />
            <div>
              <p className="text-2xl leading-none font-bold tabular">{formatNumber(card.points)}</p>
              <p className="mt-1 text-xs text-stone-600">نقطة</p>
            </div>
            <RankBadge rank={card.rank} />
          </div>
        </div>
        <div className="mt-5 grid gap-4 text-sm sm:grid-cols-2">
          <RateRow icon={<HandHeart className="size-4" />} label="حضور الخدمة" count={card.serviceCount} total={card.meetingsHeld} rate={card.serviceRate} tone="brand" />
          <RateRow icon={<Church className="size-4" />} label="حضور القداس" count={card.massCount} total={card.meetingsHeld} rate={card.massRate} tone="blue" />
        </div>
      </Card>

      <Card className="p-5">
        <h2 className="mb-3 font-semibold">الحضور</h2>
        <AttendanceHistory items={detail.attendance} />
      </Card>

      <Card className="p-5">
        <h2 className="mb-1 font-semibold">آخر حركات النقط</h2>
        {detail.recentTransactions.length === 0 ? (
          <p className="py-6 text-center text-sm text-stone-500">مفيش حركات لسه.</p>
        ) : (
          <TransactionList items={detail.recentTransactions} />
        )}
      </Card>
    </div>
  );
}
