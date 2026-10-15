import type { ReactNode } from "react";
import { Navigate, Route, Routes, useLocation } from "react-router";
import { useAuth } from "@/auth/AuthProvider";
import { Layout } from "@/components/Layout";
import { Spinner } from "@/components/ui";
import { PUSH_UI } from "@/lib/features";
import type { Role } from "@/lib/types";
import AttendancePage from "@/pages/AttendancePage";
import DashboardPage from "@/pages/DashboardPage";
import GroupsPage from "@/pages/GroupsPage";
import LoginPage from "@/pages/LoginPage";
import MyPointsPage from "@/pages/MyPointsPage";
import NotificationsPage from "@/pages/NotificationsPage";
import PendingPage from "@/pages/PendingPage";
import SettingsPage from "@/pages/SettingsPage";
import StudentDetailPage from "@/pages/StudentDetailPage";
import StudentsPage from "@/pages/StudentsPage";
import TransactionsPage from "@/pages/TransactionsPage";
import UsersPage from "@/pages/UsersPage";

export default function App() {
  const { user, loading } = useAuth();
  if (loading) return <Spinner className="min-h-dvh items-center" />;

  return (
    <Routes>
      <Route path="/login" element={user ? <Navigate to="/" replace /> : <LoginPage />} />
      <Route path="/pending" element={<RequireSignedIn><PendingPage /></RequireSignedIn>} />
      <Route
        element={
          <RequireSignedIn>
            <RequireApproved>
              <Layout />
            </RequireApproved>
          </RequireSignedIn>
        }
      >
        <Route index element={<DashboardPage />} />
        <Route path="s/:id" element={<StudentDetailPage />} />
        <Route path="me" element={<MyPointsPage />} />
        <Route path="attendance" element={<RequireRole role="ADMIN"><AttendancePage /></RequireRole>} />
        <Route path="students" element={<RequireRole role="ADMIN"><StudentsPage /></RequireRole>} />
        <Route path="groups" element={<RequireRole role="ADMIN"><GroupsPage /></RequireRole>} />
        <Route path="transactions" element={<RequireRole role="ADMIN"><TransactionsPage /></RequireRole>} />
        <Route path="settings" element={<RequireRole role="ADMIN"><SettingsPage /></RequireRole>} />
        <Route path="users" element={<RequireRole role="SUPER_ADMIN"><UsersPage /></RequireRole>} />
        {PUSH_UI && <Route path="notifications" element={<RequireRole role="SUPER_ADMIN"><NotificationsPage /></RequireRole>} />}
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}

function RequireSignedIn({ children }: { children: ReactNode }) {
  const { user } = useAuth();
  const location = useLocation();
  if (!user) return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  return children;
}

function RequireApproved({ children }: { children: ReactNode }) {
  const { user } = useAuth();
  if (user?.status !== "APPROVED") return <Navigate to="/pending" replace />;
  return children;
}

function RequireRole({ role, children }: { role: Role; children: ReactNode }) {
  const { hasRole } = useAuth();
  if (!hasRole(role)) return <Navigate to="/" replace />;
  return children;
}
