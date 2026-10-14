import {
  CalendarCheck,
  Bell,
  History,
  LayoutDashboard,
  LogOut,
  MoreHorizontal,
  Settings,
  ShieldCheck,
  Star,
  Users,
  UsersRound,
  type LucideIcon,
} from "lucide-react";
import { useEffect, useState } from "react";
import { NavLink, Outlet } from "react-router";
import { useAuth } from "@/auth/AuthProvider";
import { NotificationsToggle } from "@/components/NotificationsToggle";
import { resyncPush } from "@/lib/push";
import { roleLabel } from "@/lib/i18n";
import type { Role } from "@/lib/types";
import { cn } from "@/lib/utils";
import { Avatar, Button, Dialog } from "./ui";
import { copy } from "@/lib/copy";

interface NavItem {
  to: string;
  label: string;
  icon: LucideIcon;
  role: Role;
  requiresLink?: boolean;
}

const NAV: NavItem[] = [
  { to: "/", label: copy.nav.home, icon: LayoutDashboard, role: "USER" },
  { to: "/me", label: copy.nav.myPoints, icon: Star, role: "USER", requiresLink: true },
  { to: "/attendance", label: copy.nav.attendance, icon: CalendarCheck, role: "ADMIN" },
  { to: "/students", label: copy.nav.students, icon: Users, role: "ADMIN" },
  { to: "/groups", label: copy.nav.groups, icon: UsersRound, role: "ADMIN" },
  { to: "/transactions", label: copy.nav.history, icon: History, role: "ADMIN" },
  { to: "/users", label: copy.nav.users, icon: ShieldCheck, role: "SUPER_ADMIN" },
  { to: "/notifications", label: copy.nav.notifications, icon: Bell, role: "SUPER_ADMIN" },
  { to: "/settings", label: copy.nav.settings, icon: Settings, role: "ADMIN" },
];

const MOBILE_SLOTS = 4;

export function Layout() {
  const { user, hasRole, signOut } = useAuth();
  const [moreOpen, setMoreOpen] = useState(false);
  useEffect(() => {
    // Signed in again on a device that had notifications on for this account: reconnect it silently.
    if (user) void resyncPush(user.id);
  }, [user]);
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
        <div className="px-3 pb-2">
          <NotificationsToggle />
        </div>
        {user && (
          <div className="flex items-center gap-3 border-t border-stone-100 p-4">
            <Avatar name={user.displayName} url={user.avatarUrl} />
            <div className="min-w-0 flex-1">
              <p className="truncate text-sm font-semibold">{user.displayName}</p>
              <p className="text-xs text-stone-500">{roleLabel[user.role]}</p>
            </div>
            <Button variant="ghost" size="icon" onClick={signOut} aria-label={copy.nav.signOut} title={copy.nav.signOut}>
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
            <MoreHorizontal className="size-5" />
            {copy.nav.more}
          </button>
        </div>
      </nav>

      <Dialog open={moreOpen} onClose={() => setMoreOpen(false)} title={copy.nav.more}>
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
          {/* The dialog closes on any tap inside it; the toggle keeps it open so its status stays visible. */}
          <div onClick={(event) => event.stopPropagation()}>
            <NotificationsToggle />
          </div>
          <button
            onClick={signOut}
            className="flex w-full items-center gap-3 rounded-xl px-3 py-2.5 text-sm font-medium text-red-600 hover:bg-red-50"
          >
            <LogOut className="size-5" />
            {copy.nav.signOut}
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

/** The logo, pre-scaled per size so the detailed artwork stays sharp on high-DPI phones. */
function Logo({ className, sizes }: { className?: string; sizes: string }) {
  return (
    <img
      src="/logo-128.png"
      srcSet="/logo-96.png 96w, /logo-128.png 128w, /icon-192.png 192w, /logo.png 512w"
      sizes={sizes}
      alt=""
      decoding="async"
      className={cn("shrink-0 drop-shadow-sm", className)}
    />
  );
}

export function Brand({ compact, large }: { compact?: boolean; large?: boolean }) {
  if (large) {
    return (
      <div className="flex flex-col items-center gap-3 text-center">
        <Logo sizes="96px" className="size-24" />
        <div className="leading-tight">
          <p className="text-lg font-bold text-stone-900">{copy.brand.name}</p>
          <p className="text-xs text-stone-500">{copy.brand.tagline}</p>
        </div>
      </div>
    );
  }
  return (
    <div className={cn("flex items-center gap-2.5", !compact && "px-5 py-6")}>
      <Logo sizes={compact ? "40px" : "48px"} className={compact ? "size-10" : "size-12"} />
      <div className="min-w-0 leading-tight">
        <p className="font-bold text-stone-900">{copy.brand.name}</p>
        <p className="truncate text-[11px] text-stone-500">{copy.brand.tagline}</p>
      </div>
    </div>
  );
}
