import { useMutation } from "@tanstack/react-query";
import { ArrowRight, MailCheck } from "lucide-react";
import { useEffect, useRef, useState, type FormEvent } from "react";
import { toast } from "sonner";
import { useAuth } from "@/auth/AuthProvider";
import { GoogleButton, googleEnabled } from "@/auth/GoogleButton";
import { Brand } from "@/components/Layout";
import { Button, Field, Input, Segmented } from "@/components/ui";
import { api, ApiError } from "@/lib/api";
import { errorMessage, fieldErrors } from "@/lib/i18n";
import type { AuthResponse, RegistrationResponse } from "@/lib/types";

type Mode = "signin" | "register";

export default function LoginPage() {
  const { signIn } = useAuth();
  const [mode, setMode] = useState<Mode>("signin");
  const [verifyEmail, setVerifyEmail] = useState<string | null>(null);

  const google = useMutation({
    mutationFn: (idToken: string) => api.post<AuthResponse>("/auth/google", { idToken }),
    onSuccess: signIn,
    onError: (error) => toast.error(errorMessage(error)),
  });

  return (
    <div className="grid min-h-dvh lg:grid-cols-2">
      <div className="flex flex-col justify-center px-5 py-10 sm:px-10">
        <div className="mx-auto w-full max-w-sm">
          <div className="mb-8 [&>div]:px-0 [&>div]:py-0">
            <Brand />
          </div>

          {verifyEmail ? (
            <VerifyStep email={verifyEmail} onBack={() => setVerifyEmail(null)} onVerified={signIn} />
          ) : (
            <>
              <h1 className="text-2xl font-bold">{mode === "signin" ? "أهلًا بيك تاني" : "حساب جديد"}</h1>
              <p className="mt-1 mb-6 text-sm text-stone-500">
                {mode === "signin" ? "سجّل الدخول عشان تتابع الحضور والنقط." : "سجّل بإيميلك وهنبعتلك كود تأكيد."}
              </p>

              {googleEnabled && (
                <>
                  <GoogleButton onCredential={(token) => google.mutate(token)} />
                  <div className="my-5 flex items-center gap-3 text-xs text-stone-400">
                    <span className="h-px flex-1 bg-stone-200" />
                    أو بالإيميل
                    <span className="h-px flex-1 bg-stone-200" />
                  </div>
                </>
              )}

              <Segmented
                className="mb-5 w-full"
                value={mode}
                onChange={setMode}
                options={[
                  { value: "signin", label: "تسجيل الدخول" },
                  { value: "register", label: "حساب جديد" },
                ]}
              />

              {mode === "signin" ? (
                <SignInForm onSignedIn={signIn} onNeedsVerification={setVerifyEmail} />
              ) : (
                <RegisterForm onRegistered={setVerifyEmail} />
              )}
            </>
          )}
        </div>
      </div>

      <div className="relative hidden overflow-hidden bg-brand-800 lg:block">
        <div className="absolute inset-0 bg-[radial-gradient(circle_at_30%_20%,rgba(255,255,255,0.14),transparent_55%)]" />
        <div className="relative flex h-full flex-col justify-end p-12 text-white">
          <p className="text-3xl leading-snug font-bold">كل جمعة بتفرق.</p>
          <p className="mt-3 max-w-md text-brand-100">
            سجّل حضور الخدمة والقداس، والنقط بتتوزع لوحدها. وكل مخدوم يقدر يتابع نقطه وترتيبه.
          </p>
        </div>
      </div>
    </div>
  );
}

function SignInForm({
  onSignedIn,
  onNeedsVerification,
}: {
  onSignedIn: (response: AuthResponse) => void;
  onNeedsVerification: (email: string) => void;
}) {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");

  const login = useMutation({
    mutationFn: () => api.post<AuthResponse>("/auth/login", { email, password }),
    onSuccess: onSignedIn,
    onError: async (error) => {
      if (error instanceof ApiError && error.code === "EMAIL_NOT_VERIFIED") {
        await api.post("/auth/resend-code", { email }).catch(() => undefined);
        toast.info("إيميلك لسه مش متأكد. ابعتنالك كود جديد.");
        onNeedsVerification(email.trim().toLowerCase());
        return;
      }
      toast.error(errorMessage(error));
    },
  });

  const submit = (event: FormEvent) => {
    event.preventDefault();
    login.mutate();
  };

  return (
    <form onSubmit={submit} className="space-y-4">
      <Field label="الإيميل">
        <Input type="email" dir="ltr" autoComplete="email" required value={email} onChange={(e) => setEmail(e.target.value)} />
      </Field>
      <Field label="كلمة السر">
        <Input
          type="password"
          dir="ltr"
          autoComplete="current-password"
          required
          value={password}
          onChange={(e) => setPassword(e.target.value)}
        />
      </Field>
      <Button type="submit" size="lg" className="w-full" loading={login.isPending}>
        دخول
      </Button>
    </form>
  );
}

function RegisterForm({ onRegistered }: { onRegistered: (email: string) => void }) {
  const [form, setForm] = useState({ displayName: "", email: "", password: "" });
  const register = useMutation({
    mutationFn: () => api.post<RegistrationResponse>("/auth/register", form),
    onSuccess: (response) => {
      toast.success("بعتنالك كود التأكيد على إيميلك");
      onRegistered(response.email);
    },
    onError: (error) => {
      if (!(error instanceof ApiError && error.code === "VALIDATION_FAILED")) toast.error(errorMessage(error));
    },
  });
  const errors = fieldErrors(register.error);
  const set = (key: keyof typeof form) => (e: React.ChangeEvent<HTMLInputElement>) =>
    setForm((f) => ({ ...f, [key]: e.target.value }));

  return (
    <form
      onSubmit={(e) => {
        e.preventDefault();
        register.mutate();
      }}
      className="space-y-4"
    >
      <Field label="الاسم" error={errors.displayName}>
        <Input autoComplete="name" required minLength={2} value={form.displayName} onChange={set("displayName")} />
      </Field>
      <Field label="الإيميل" error={errors.email}>
        <Input type="email" dir="ltr" autoComplete="email" required value={form.email} onChange={set("email")} />
      </Field>
      <Field label="كلمة السر" error={errors.password} hint="8 حروف على الأقل">
        <Input
          type="password"
          dir="ltr"
          autoComplete="new-password"
          required
          minLength={8}
          value={form.password}
          onChange={set("password")}
        />
      </Field>
      <Button type="submit" size="lg" className="w-full" loading={register.isPending}>
        إنشاء الحساب
      </Button>
    </form>
  );
}

function VerifyStep({
  email,
  onBack,
  onVerified,
}: {
  email: string;
  onBack: () => void;
  onVerified: (response: AuthResponse) => void;
}) {
  const [code, setCode] = useState("");
  const [cooldown, setCooldown] = useState(60);
  const input = useRef<HTMLInputElement>(null);

  useEffect(() => input.current?.focus(), []);
  useEffect(() => {
    if (cooldown <= 0) return;
    const timer = setTimeout(() => setCooldown((c) => c - 1), 1000);
    return () => clearTimeout(timer);
  }, [cooldown]);

  const verify = useMutation({
    mutationFn: (value: string) => api.post<AuthResponse>("/auth/verify-email", { email, code: value }),
    onSuccess: onVerified,
    onError: (error) => {
      toast.error(errorMessage(error));
      setCode("");
    },
  });
  const resend = useMutation({
    mutationFn: () => api.post("/auth/resend-code", { email }),
    onSuccess: () => {
      toast.success("بعتنا كود جديد");
      setCooldown(60);
    },
    onError: (error) => toast.error(errorMessage(error)),
  });

  const onChange = (value: string) => {
    const digits = value.replace(/\D/g, "").slice(0, 6);
    setCode(digits);
    if (digits.length === 6) verify.mutate(digits);
  };

  return (
    <div>
      <button onClick={onBack} className="mb-6 inline-flex items-center gap-1 text-sm text-stone-500 hover:text-stone-800">
        <ArrowRight className="size-4" />
        رجوع
      </button>
      <span className="mb-4 grid size-12 place-items-center rounded-2xl bg-brand-50 text-brand-700">
        <MailCheck className="size-6" />
      </span>
      <h1 className="text-2xl font-bold">أكّد إيميلك</h1>
      <p className="mt-1 mb-6 text-sm text-stone-500">
        بعتنا كود من 6 أرقام على{" "}
        <span dir="ltr" className="font-medium text-stone-800">
          {email}
        </span>
      </p>
      <Input
        ref={input}
        inputMode="numeric"
        autoComplete="one-time-code"
        dir="ltr"
        placeholder="••••••"
        value={code}
        onChange={(e) => onChange(e.target.value)}
        disabled={verify.isPending}
        className="h-14 text-center text-2xl font-semibold tracking-[0.6em] tabular"
      />
      <Button size="lg" className="mt-4 w-full" loading={verify.isPending} disabled={code.length !== 6} onClick={() => verify.mutate(code)}>
        تأكيد
      </Button>
      <p className="mt-5 text-center text-sm text-stone-500">
        الكود موصلش؟{" "}
        {cooldown > 0 ? (
          <span className="tabular">ممكن تطلب كود جديد بعد {cooldown} ثانية</span>
        ) : (
          <button className="font-medium text-brand-700 hover:underline" onClick={() => resend.mutate()} disabled={resend.isPending}>
            ابعت كود جديد
          </button>
        )}
      </p>
    </div>
  );
}
