import { useQuery } from "@tanstack/react-query";
import { ErrorState, PageHeader, Spinner } from "@/components/ui";
import { useAuth } from "@/auth/AuthProvider";
import { api } from "@/lib/api";
import { errorMessage } from "@/lib/i18n";
import type { StudentDetail } from "@/lib/types";
import { StudentProfile } from "./StudentDetailPage";

/** The linked student's own profile; same view as the dashboard detail page. */
export default function MyPointsPage() {
  const { user } = useAuth();
  const detail = useQuery({
    queryKey: ["dashboard", "student", String(user?.studentId)],
    queryFn: () => api.get<StudentDetail>(`/dashboard/students/${user!.studentId}`),
    enabled: Boolean(user?.studentId),
  });

  if (!user?.studentId) {
    return <ErrorState message="حسابك مش مربوط بمخدوم لسه. كلّم مسؤول الخدمة." />;
  }
  return (
    <>
      <PageHeader title="نقطي" />
      {detail.isPending ? <Spinner /> : detail.isError ? <ErrorState message={errorMessage(detail.error)} /> : <StudentProfile detail={detail.data} />}
    </>
  );
}
