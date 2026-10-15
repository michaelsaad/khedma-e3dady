import { useQuery, useQueryClient } from "@tanstack/react-query";
import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import { api, onUnauthorized, tokenStore } from "@/lib/api";
import { PUSH_UI } from "@/lib/features";
import { detachPush } from "@/lib/push";
import type { AuthResponse, Role, User } from "@/lib/types";

interface AuthState {
  user: User | null;
  loading: boolean;
  signIn: (response: AuthResponse) => void;
  signOut: () => void;
  hasRole: (role: Role) => boolean;
}

const AuthContext = createContext<AuthState | null>(null);

const RANK: Record<Role, number> = { USER: 1, ADMIN: 2, SUPER_ADMIN: 3 };

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient();
  const [token, setToken] = useState(() => tokenStore.get());

  const me = useQuery({
    queryKey: ["me", token],
    queryFn: () => api.get<User>("/me"),
    enabled: Boolean(token),
    staleTime: 60_000,
  });

  const signOut = useCallback(() => {
    // Detach this device from the account while the token still works, so the next person doesn't get its pushes.
    if (PUSH_UI) void detachPush();
    tokenStore.set(null);
    setToken(null);
    queryClient.clear();
  }, [queryClient]);

  const signIn = useCallback(
    (response: AuthResponse) => {
      tokenStore.set(response.accessToken);
      queryClient.setQueryData(["me", response.accessToken], response.user);
      setToken(response.accessToken);
    },
    [queryClient],
  );

  useEffect(() => onUnauthorized(signOut), [signOut]);

  const value = useMemo<AuthState>(() => {
    const user = token ? (me.data ?? null) : null;
    return {
      user,
      loading: Boolean(token) && me.isPending,
      signIn,
      signOut,
      hasRole: (role) => Boolean(user && user.status === "APPROVED" && RANK[user.role] >= RANK[role]),
    };
  }, [token, me.data, me.isPending, signIn, signOut]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) throw new Error("useAuth must be used inside AuthProvider");
  return context;
}
