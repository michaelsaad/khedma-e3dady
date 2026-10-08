import {
  CalendarCheck,
  Coins,
  LayoutDashboard,
  LogOut,
  Menu,
  Settings,
  ShieldCheck,
  Star,
  Users,
  UsersRound,
  type LucideIcon,
} from "lucide-react";
import { useState } from "react";
import { NavLink, Outlet } from "react-router";
import { useAuth } from "@/auth/AuthProvider";
import { roleLabel } from "@/lib/i18n";
import type { Role } from "@/lib/types";
import { cn } from "@/lib/utils";
import { Avatar, Button, Dialog } from "./ui";

interface NavItem {
  to: string;
  label: string;
  icon: LucideIcon;
  role: Role;
  requiresLink?: boolean;
}

const NAV: NavItem[] = [
  { to: "/", label: "الرئيسية", icon: LayoutDashboard, role: "USER" },
  { to: "/me", label: "نقطي", icon: Star, role: "USER", requiresLink: true },
  { to: "/attendance", label: "الحضور", icon: CalendarCheck, role: "ADMIN" },
  { to: "/students", label: "المخدومين", icon: Users, role: "ADMIN" },
  { to: "/groups", label: "الجروبات", icon: UsersRound, role: "ADMIN" },
  { to: "/transactions", label: "النقط", icon: Coins, role: "ADMIN" },
  { to: "/users", label: "المستخدمين", icon: ShieldCheck, role: "SUPER_ADMIN" },
  { to: "/settings", label: "الإعدادات", icon: Settings, role: "ADMIN" },
];

const MOBILE_SLOTS = 4;

export function Layout() {
  const { user, hasRole, signOut } = useAuth();
  const [moreOpen, setMoreOpen] = useState(false);
  const items = NAV.filter((item) => hasRole(item.role) && (!item.requiresLink || user?.studentId));
  const primary = items.slice(0, MOBILE_SLOTS);
  const overflow = items.slice(MOBILE_SLOTS);

  return (
    <div className="min-h-dvh lg:flex">
      {/* Desktop sidebar (right side in RTL) */}
      <aside className="sticky top-0 hidden h-dvh w-64 shrink-0 flex-col border-e border-stone-200 bg-white lg:flex">
        <Brand />
        <nav className="flex-1 space-y-1 px-3">
          {items.map((item) => (
            <SideLink key={item.to} item={item} />
          ))}
        </nav>
        {user && (
          <div className="flex items-center gap-3 border-t border-stone-100 p-4">
            <Avatar name={user.displayName} url={user.avatarUrl} />
            <div className="min-w-0 flex-1">
              <p className="truncate text-sm font-semibold">{user.displayName}</p>
              <p className="text-xs text-stone-500">{roleLabel[user.role]}</p>
            </div>
            <Button variant="ghost" size="icon" onClick={signOut} aria-label="تسجيل الخروج" title="تسجيل الخروج">
              <LogOut className="size-4" />
            </Button>
          </div>
        )}
      </aside>

      <div className="min-w-0 flex-1">
        {/* Mobile header */}
        <header className="sticky top-0 z-30 flex items-center justify-between border-b border-stone-200 bg-white/90 px-4 py-3 backdrop-blur lg:hidden">
          <Brand compact />
          {user && <Avatar name={user.displayName} url={user.avatarUrl} className="size-8" />}
        </header>

        <main className="mx-auto w-full max-w-6xl px-4 pt-5 pb-28 lg:px-8 lg:pt-8 lg:pb-12">
          <Outlet />
        </main>
      </div>

      {/* Mobile bottom navigation */}
      <nav className="safe-bottom fixed inset-x-0 bottom-0 z-30 border-t border-stone-200 bg-white/95 backdrop-blur lg:hidden">
        <div className="mx-auto flex max-w-md">
          {primary.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.to === "/"}
              className={({ isActive }) =>
                cn(
                  "flex flex-1 flex-col items-center gap-0.5 py-2 text-[11px] font-medium",
                  isActive ? "text-brand-700" : "text-stone-500",
                )
              }
            >
              <item.icon className="size-5" />
              {item.label}
            </NavLink>
          ))}
          <button
            onClick={() => setMoreOpen(true)}
            className="flex flex-1 flex-col items-center gap-0.5 py-2 text-[11px] font-medium text-stone-500"
          >
            <Menu className="size-5" />
            المزيد
          </button>
        </div>
      </nav>

      <Dialog open={moreOpen} onClose={() => setMoreOpen(false)} title="المزيد">
        <div className="space-y-1" onClick={() => setMoreOpen(false)}>
          {overflow.map((item) => (
            <SideLink key={item.to} item={item} />
          ))}
          {user && (
            <div className="mt-3 flex items-center gap-3 rounded-xl bg-stone-50 p-3">
              <Avatar name={user.displayName} url={user.avatarUrl} />
              <div className="min-w-0 flex-1">
                <p className="truncate text-sm font-semibold">{user.displayName}</p>
                <p className="truncate text-xs text-stone-500">{user.email}</p>
              </div>
            </div>
          )}
          <button
            onClick={signOut}
            className="flex w-full items-center gap-3 rounded-xl px-3 py-2.5 text-sm font-medium text-red-600 hover:bg-red-50"
          >
            <LogOut className="size-5" />
            تسجيل الخروج
          </button>
        </div>
      </Dialog>
    </div>
  );
}

function SideLink({ item }: { item: NavItem }) {
  return (
    <NavLink
      to={item.to}
      end={item.to === "/"}
      className={({ isActive }) =>
        cn(
          "flex items-center gap-3 rounded-xl px-3 py-2.5 text-sm font-medium transition-colors",
          isActive ? "bg-brand-50 text-brand-800" : "text-stone-600 hover:bg-stone-100 hover:text-stone-900",
        )
      }
    >
      <item.icon className="size-5" />
      {item.label}
    </NavLink>
  );
}

export function Brand({ compact }: { compact?: boolean }) {
  return (
    <div className={cn("flex items-center gap-2.5", !compact && "px-5 py-6")}>
      <span className="grid size-9 place-items-center rounded-xl bg-brand-700 text-white shadow-sm">
        <svg viewBox="0 0 32 32" className="size-5" aria-hidden>
          <path d="M16 6v20M9 13h14" stroke="currentColor" strokeWidth="3.2" strokeLinecap="round" fill="none" />
        </svg>
      </span>
      <div className="leading-tight">
        <p className="font-bold text-stone-900">خدمة إعدادي</p>
        {!compact && <p className="text-xs text-stone-500">الحضور والنقط</p>}
      </div>
    </div>
  );
}
