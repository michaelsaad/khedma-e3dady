import { useMutation } from "@tanstack/react-query";
import { ArrowRight, KeyRound, MailCheck, ShieldCheck } from "lucide-react";
import { useEffect, useRef, useState, type FormEvent } from "react";
import { toast } from "sonner";
import { useAuth } from "@/auth/AuthProvider";
import { GoogleButton, googleEnabled } from "@/auth/GoogleButton";
import { Brand } from "@/components/Layout";
import { InfoLinks } from "@/pages/InfoPages";
import { CodeInput } from "@/components/CodeInput";
import { Button, Field, Input, Segmented } from "@/components/ui";
import { api, ApiError } from "@/lib/api";
import { errorMessage, fieldErrors } from "@/lib/i18n";
import { challengeStore, deviceStore, takeCodeLink, type CodeLink } from "@/lib/device";
import type { AuthResponse, LoginChallenge, RegistrationResponse } from "@/lib/types";
import { copy } from "@/lib/copy";

type Mode = "signin" | "register";

// Read once per page load (StrictMode renders twice, and the hash is cleared on the first read).
let pendingLink: CodeLink | null | undefined;
function codeLinkOnce() {
  if (pendingLink === undefined) pendingLink = takeCodeLink();
  return pendingLink;
}

export default function LoginPage() {
  const { signIn } = useAuth();
  const [link] = useState(codeLinkOnce);
  const [linkChallenge] = useState(() => (link?.flow === "login" ? challengeStore.get(link.email) : null));
  const [mode, setMode] = useState<Mode>("signin");
  const [verifyEmail, setVerifyEmail] = useState<string | null>(link?.flow === "verify" ? link.email : null);
  const [forgotEmail, setForgotEmail] = useState<string | null>(link?.flow === "reset" ? link.email : null);
  const [challenge, setChallenge] = useState<LoginChallenge | null>(linkChallenge);

  useEffect(() => {
    // The email link opened in a tab that already shows this page: only the hash changed, so start over.
    const onHashChange = () => {
      if (window.location.hash.startsWith("#otp=")) window.location.reload();
    };
    window.addEventListener("hashchange", onHashChange);
    return () => window.removeEventListener("hashchange", onHashChange);
  }, []);

  useEffect(() => {
    pendingLink = null; // used up: coming back to this page later starts clean
    if (link?.flow === "login" && !linkChallenge) toast.info(copy.login.linkOtherBrowser);
  }, [link, linkChallenge]);

  const startChallenge = (next: LoginChallenge) => {
    challengeStore.set(next);
    setChallenge(next);
  };

  const google = useMutation({
    mutationFn: (idToken: string) => api.post<AuthResponse>("/auth/google", { idToken }),
    onSuccess: signIn,
    onError: (error) => toast.error(errorMessage(error)),
  });

  return (
    <div className="grid min-h-dvh lg:grid-cols-2">
      <div className="flex flex-col justify-center px-5 py-10 sm:px-10">
        <div className="mx-auto w-full max-w-sm">
          <div className="mb-8">
            <Brand large />
          </div>

          {challenge ? (
            <LoginCodeStep
              challenge={challenge}
              initialCode={linkChallenge === challenge ? link?.code : undefined}
              onBack={() => {
                challengeStore.set(null);
                setChallenge(null);
              }}
              onVerified={signIn}
            />
          ) : forgotEmail !== null ? (
            <ForgotPassword
              initialEmail={forgotEmail}
              initialCode={link?.flow === "reset" ? link.code : undefined}
              onBack={() => setForgotEmail(null)}
              onReset={signIn}
            />
          ) : verifyEmail ? (
            <VerifyStep
              email={verifyEmail}
              initialCode={link?.flow === "verify" && link.email === verifyEmail ? link.code : undefined}
              onBack={() => setVerifyEmail(null)}
              onVerified={signIn}
            />
          ) : (
            <>
              <h1 className="text-2xl font-bold">{mode === "signin" ? copy.login.welcomeBack : copy.login.newAccount}</h1>
              {mode === "register" ? <p className="mt-1 mb-6 text-sm text-stone-500">{copy.login.registerHint}</p> : <div className="mb-6" />}

              {googleEnabled && (
                <>
                  <GoogleButton onCredential={(token) => google.mutate(token)} />
                  <div className="my-5 flex items-center gap-3 text-xs text-stone-400">
                    <span className="h-px flex-1 bg-stone-200" />
                    {copy.login.orEmail}
                    <span className="h-px flex-1 bg-stone-200" />
                  </div>
                </>
              )}

              <Segmented
                className="mb-5 w-full"
                value={mode}
                onChange={setMode}
                options={[
                  { value: "signin", label: copy.login.signInTab },
                  { value: "register", label: copy.login.registerTab },
                ]}
              />

              {mode === "signin" ? (
                <SignInForm
                  initialEmail={link?.flow === "login" ? link.email : ""}
                  onSignedIn={signIn}
                  onChallenge={startChallenge}
                  onNeedsVerification={setVerifyEmail}
                  onForgot={setForgotEmail}
                />
              ) : (
                <RegisterForm onRegistered={setVerifyEmail} />
              )}
            </>
          )}
          <InfoLinks className="mt-10" />
        </div>
      </div>

      <div className="relative hidden overflow-hidden bg-brand-800 lg:block">
        <div className="absolute inset-0 bg-[radial-gradient(circle_at_30%_20%,rgba(255,255,255,0.14),transparent_55%)]" />
        <div className="relative flex h-full flex-col justify-end p-12 text-white">
          <img src="/logo.png" alt="" decoding="async" className="mt-8 mb-auto size-72 self-center drop-shadow-xl xl:size-80" />
          <p className="text-3xl leading-snug font-bold">{copy.login.heroTitle}</p>
          <p className="mt-3 max-w-md text-brand-100">
            {copy.login.heroText}
          </p>
        </div>
      </div>
    </div>
  );
}

function SignInForm({
  initialEmail,
  onSignedIn,
  onChallenge,
  onNeedsVerification,
  onForgot,
}: {
  initialEmail: string;
  onSignedIn: (response: AuthResponse) => void;
  onChallenge: (challenge: LoginChallenge) => void;
  onNeedsVerification: (email: string) => void;
  onForgot: (email: string) => void;
}) {
  const [email, setEmail] = useState(initialEmail);
  const [password, setPassword] = useState("");

  const login = useMutation({
    mutationFn: () =>
      api.post<AuthResponse | LoginChallenge>("/auth/login", { email, password, deviceToken: deviceStore.get(email) }),
    onSuccess: (result) => ("challengeToken" in result ? onChallenge(result) : onSignedIn(result)),
    onError: async (error) => {
      if (error instanceof ApiError && error.code === "EMAIL_NOT_VERIFIED") {
        await api.post("/auth/resend-code", { email }).catch(() => undefined);
        toast.info(copy.login.notVerified);
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
      <Field label={copy.common.email}>
        <Input type="email" dir="ltr" autoComplete="email" required value={email} onChange={(e) => setEmail(e.target.value)} />
      </Field>
      <Field label={copy.common.password}>
        <Input
          type="password"
          dir="ltr"
          autoComplete="current-password"
          required
          value={password}
          onChange={(e) => setPassword(e.target.value)}
        />
      </Field>
      <div className="-mt-2 text-end">
        <button type="button" onClick={() => onForgot(email.trim().toLowerCase())} className="text-sm font-medium text-brand-700 hover:underline">
          {copy.login.forgotLink}
        </button>
      </div>
      <Button type="submit" size="lg" className="w-full" loading={login.isPending}>
        {copy.login.signIn}
      </Button>
    </form>
  );
}

function RegisterForm({ onRegistered }: { onRegistered: (email: string) => void }) {
  const [form, setForm] = useState({ displayName: "", email: "", password: "" });
  const register = useMutation({
    mutationFn: () => api.post<RegistrationResponse>("/auth/register", form),
    onSuccess: (response) => {
      toast.success(copy.login.codeSent);
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
      <Field label={copy.common.name} error={errors.displayName}>
        <Input autoComplete="name" required minLength={2} value={form.displayName} onChange={set("displayName")} />
      </Field>
      <Field label={copy.common.email} error={errors.email}>
        <Input type="email" dir="ltr" autoComplete="email" required value={form.email} onChange={set("email")} />
      </Field>
      <Field label={copy.common.password} error={errors.password} hint={copy.login.passwordHint}>
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
        {copy.login.createAccount}
      </Button>
    </form>
  );
}

function VerifyStep({
  email,
  initialCode,
  onBack,
  onVerified,
}: {
  email: string;
  initialCode?: string;
  onBack: () => void;
  onVerified: (response: AuthResponse) => void;
}) {
  const [code, setCode] = useState(initialCode ?? "");
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
      toast.success(copy.login.newCodeSent);
      setCooldown(60);
    },
    onError: (error) => toast.error(errorMessage(error)),
  });

  useAutoSubmit(initialCode, verify.mutate);

  return (
    <div>
      <button onClick={onBack} className="mb-6 inline-flex items-center gap-1 text-sm text-stone-500 hover:text-stone-800">
        <ArrowRight className="size-4" />
        {copy.common.back}
      </button>
      <span className="mb-4 grid size-12 place-items-center rounded-2xl bg-brand-50 text-brand-700">
        <MailCheck className="size-6" />
      </span>
      <h1 className="text-2xl font-bold">{copy.login.verifyTitle}</h1>
      <p className="mt-1 mb-6 text-sm text-stone-500">
        {copy.login.verifySentTo}{" "}
        <span dir="ltr" className="font-medium text-stone-800">
          {email}
        </span>
      </p>
      <CodeInput ref={input} value={code} onChange={setCode} onComplete={verify.mutate} disabled={verify.isPending} />
      <Button size="lg" className="mt-4 w-full" loading={verify.isPending} disabled={code.length !== 6} onClick={() => verify.mutate(code)}>
        {copy.login.verify}
      </Button>
      <p className="mt-5 text-center text-sm text-stone-500">
        {copy.login.codeNotArrived}{" "}
        {cooldown > 0 ? (
          <span className="tabular">{copy.login.resendIn(cooldown)}</span>
        ) : (
          <button className="font-medium text-brand-700 hover:underline" onClick={() => resend.mutate()} disabled={resend.isPending}>
            {copy.login.resend}
          </button>
        )}
      </p>
    </div>
  );
}

/** Two steps: ask for the email, then the emailed code + a new password (signs the user in). */
function ForgotPassword({
  initialEmail,
  initialCode,
  onBack,
  onReset,
}: {
  initialEmail: string;
  initialCode?: string;
  onBack: () => void;
  onReset: (response: AuthResponse) => void;
}) {
  const [email, setEmail] = useState(initialEmail);
  const [sentTo, setSentTo] = useState<string | null>(initialCode ? initialEmail : null);
  const [code, setCode] = useState(initialCode ?? "");
  const [password, setPassword] = useState("");
  const [cooldown, setCooldown] = useState(0);

  useEffect(() => {
    if (cooldown <= 0) return;
    const timer = setTimeout(() => setCooldown((c) => c - 1), 1000);
    return () => clearTimeout(timer);
  }, [cooldown]);

  const request = useMutation({
    mutationFn: (target: string) => api.post("/auth/forgot-password", { email: target }),
    onSuccess: (_data, target) => {
      setSentTo(target);
      setCooldown(60);
      toast.success(copy.login.resetCodeSent);
    },
    onError: (error) => toast.error(errorMessage(error)),
  });

  const reset = useMutation({
    mutationFn: () => api.post<AuthResponse>("/auth/reset-password", { email: sentTo, code, newPassword: password }),
    onSuccess: (response) => {
      toast.success(copy.login.passwordChanged);
      onReset(response);
    },
    onError: (error) => {
      if (!(error instanceof ApiError && error.code === "VALIDATION_FAILED")) toast.error(errorMessage(error));
      setCode("");
    },
  });
  const errors = fieldErrors(reset.error);

  return (
    <div>
      <button onClick={onBack} className="mb-6 inline-flex items-center gap-1 text-sm text-stone-500 hover:text-stone-800">
        <ArrowRight className="size-4" />
        {copy.login.backToSignIn}
      </button>
      <span className="mb-4 grid size-12 place-items-center rounded-2xl bg-brand-50 text-brand-700">
        <KeyRound className="size-6" />
      </span>
      <h1 className="text-2xl font-bold">{copy.login.forgotTitle}</h1>

      {!sentTo ? (
        <form
          className="mt-6 space-y-4"
          onSubmit={(e) => {
            e.preventDefault();
            request.mutate(email.trim().toLowerCase());
          }}
        >
          <p className="text-sm text-stone-500">{copy.login.forgotHint}</p>
          <Field label={copy.common.email}>
            <Input type="email" dir="ltr" autoComplete="email" required autoFocus value={email} onChange={(e) => setEmail(e.target.value)} />
          </Field>
          <Button type="submit" size="lg" className="w-full" loading={request.isPending}>
            {copy.login.sendCode}
          </Button>
        </form>
      ) : (
        <form
          className="mt-6 space-y-4"
          onSubmit={(e) => {
            e.preventDefault();
            reset.mutate();
          }}
        >
          <p className="text-sm text-stone-500">
            {copy.login.resetSentTo}{" "}
            <span dir="ltr" className="font-medium text-stone-800">
              {sentTo}
            </span>{" "}
            {copy.login.resetSentHint}
          </p>
          <Field label={copy.login.code} error={errors.code}>
            <CodeInput size="md" autoFocus={!initialCode} value={code} onChange={setCode} />
          </Field>
          <Field label={copy.login.newPassword} error={errors.newPassword} hint={copy.login.passwordHint}>
            <Input
              type="password"
              dir="ltr"
              autoComplete="new-password"
              required
              minLength={8}
              autoFocus={Boolean(initialCode)}
              value={password}
              onChange={(e) => setPassword(e.target.value)}
            />
          </Field>
          <Button type="submit" size="lg" className="w-full" loading={reset.isPending} disabled={code.length !== 6 || password.length < 8}>
            {copy.login.resetSubmit}
          </Button>
          <p className="text-center text-sm text-stone-500">
            {copy.login.codeNotArrived}{" "}
            {cooldown > 0 ? (
              <span className="tabular">{copy.login.resendIn(cooldown)}</span>
            ) : (
              <button type="button" className="font-medium text-brand-700 hover:underline" onClick={() => request.mutate(sentTo)} disabled={request.isPending}>
                {copy.login.resend}
              </button>
            )}
          </p>
        </form>
      )}
    </div>
  );
}

/** Second step of an email/password sign-in on a browser that isn't trusted yet. */
function LoginCodeStep({
  challenge,
  initialCode,
  onBack,
  onVerified,
}: {
  challenge: LoginChallenge;
  initialCode?: string;
  onBack: () => void;
  onVerified: (response: AuthResponse) => void;
}) {
  const [code, setCode] = useState(initialCode ?? "");
  const [cooldown, setCooldown] = useState(60);
  const input = useRef<HTMLInputElement>(null);

  useEffect(() => input.current?.focus(), []);
  useEffect(() => {
    if (cooldown <= 0) return;
    const timer = setTimeout(() => setCooldown((c) => c - 1), 1000);
    return () => clearTimeout(timer);
  }, [cooldown]);

  const verify = useMutation({
    mutationFn: (value: string) =>
      api.post<AuthResponse>("/auth/login/verify", { challengeToken: challenge.challengeToken, code: value }),
    onSuccess: onVerified,
    onError: (error) => {
      toast.error(errorMessage(error));
      setCode("");
      if (error instanceof ApiError && error.code === "LOGIN_CHALLENGE_INVALID") onBack();
    },
  });
  const resend = useMutation({
    mutationFn: () => api.post("/auth/login/resend", { challengeToken: challenge.challengeToken }),
    onSuccess: () => {
      toast.success(copy.login.newCodeSent);
      setCooldown(60);
    },
    onError: (error) => toast.error(errorMessage(error)),
  });

  useAutoSubmit(initialCode, verify.mutate);

  return (
    <div>
      <button onClick={onBack} className="mb-6 inline-flex items-center gap-1 text-sm text-stone-500 hover:text-stone-800">
        <ArrowRight className="size-4" />
        {copy.login.backToSignIn}
      </button>
      <span className="mb-4 grid size-12 place-items-center rounded-2xl bg-brand-50 text-brand-700">
        <ShieldCheck className="size-6" />
      </span>
      <h1 className="text-2xl font-bold">{copy.login.loginCodeTitle}</h1>
      <p className="mt-1 mb-6 text-sm text-stone-500">
        {copy.login.loginCodeSentTo}{" "}
        <span dir="ltr" className="font-medium text-stone-800">
          {challenge.email}
        </span>
      </p>
      <CodeInput ref={input} value={code} onChange={setCode} onComplete={verify.mutate} disabled={verify.isPending} />
      <Button size="lg" className="mt-4 w-full" loading={verify.isPending} disabled={code.length !== 6} onClick={() => verify.mutate(code)}>
        {copy.login.verify}
      </Button>
      <p className="mt-3 text-center text-xs text-stone-500">{copy.login.loginCodeHint}</p>
      <p className="mt-5 text-center text-sm text-stone-500">
        {copy.login.codeNotArrived}{" "}
        {cooldown > 0 ? (
          <span className="tabular">{copy.login.resendIn(cooldown)}</span>
        ) : (
          <button className="font-medium text-brand-700 hover:underline" onClick={() => resend.mutate()} disabled={resend.isPending}>
            {copy.login.resend}
          </button>
        )}
      </p>
    </div>
  );
}

/** A code that arrived through the email link is submitted once, as if it had been typed. */
function useAutoSubmit(initialCode: string | undefined, submit: (code: string) => void) {
  const done = useRef(false);
  useEffect(() => {
    if (done.current || !initialCode || initialCode.length !== 6) return;
    done.current = true;
    submit(initialCode);
  }, [initialCode, submit]);
}
