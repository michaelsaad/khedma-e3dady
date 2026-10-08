import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { Coins } from "lucide-react";
import { useState } from "react";
import { TransactionList } from "@/components/TransactionList";
import { Card, EmptyState, ErrorState, PageHeader, Pagination, Spinner } from "@/components/ui";
import { api } from "@/lib/api";
import { errorMessage } from "@/lib/i18n";
import type { Page, Transaction } from "@/lib/types";

/** Every points movement, newest first. Manual changes are made from the students/groups pages. */
export default function TransactionsPage() {
  const [page, setPage] = useState(0);
  const history = useQuery({
    queryKey: ["transactions", "all", page],
    queryFn: () => api.get<Page<Transaction>>("/transactions", { page, size: 30 }),
    placeholderData: keepPreviousData,
  });

  return (
    <>
      <PageHeader title="سجل النقط" subtitle="كل حركات النقط للمخدومين والجروبات. لإضافة نقط افتح المخدوم أو الجروب." />
      {history.isPending ? (
        <Spinner />
      ) : history.isError ? (
        <ErrorState message={errorMessage(history.error)} onRetry={() => history.refetch()} />
      ) : history.data.items.length === 0 ? (
        <Card>
          <EmptyState icon={<Coins className="size-10" />} title="مفيش حركات لسه" text="النقط بتتسجل هنا أول ما تسجّل حضور أو تضيف نقط." />
        </Card>
      ) : (
        <>
          <Card className={`px-4 ${history.isPlaceholderData ? "opacity-60" : ""}`}>
            <TransactionList items={history.data.items} showTarget />
          </Card>
          <Pagination page={history.data.page} totalPages={history.data.totalPages} totalItems={history.data.totalItems} onChange={setPage} />
        </>
      )}
    </>
  );
}
