import { ArrowRight, Info, Mail, ShieldCheck } from "lucide-react";
import type { ReactNode } from "react";
import { Link, NavLink } from "react-router";
import { Brand } from "@/components/Layout";
import { Card } from "@/components/ui";
import { copy } from "@/lib/copy";
import { cn } from "@/lib/utils";

const t = copy.info;

/** About, privacy and contact: public pages, reachable without signing in. */
function InfoShell({ icon, title, children }: { icon: ReactNode; title: string; children: ReactNode }) {
  return (
    <div className="min-h-dvh px-4 py-8 sm:py-12">
      <div className="mx-auto w-full max-w-2xl">
        <div className="mb-6 flex items-center justify-between gap-4">
          <Link to="/">
            <Brand compact />
          </Link>
          <Link to="/" className="inline-flex items-center gap-1 text-sm text-stone-500 hover:text-stone-800">
            <ArrowRight className="size-4" />
            {t.backToSite}
          </Link>
        </div>
        <Card className="p-6 sm:p-8">
          <span className="mb-4 grid size-12 place-items-center rounded-2xl bg-brand-50 text-brand-700">{icon}</span>
          <h1 className="mb-4 text-2xl font-bold">{title}</h1>
          <div className="space-y-6 text-[15px] leading-8 text-stone-700">{children}</div>
        </Card>
        <InfoLinks className="mt-6" />
        <p dir="ltr" className="mt-2 text-center text-[11px] text-stone-400">
          {t.about.builtBy}
        </p>
      </div>
    </div>
  );
}

/** The about / privacy / contact links, also shown under the sign-in form. */
export function InfoLinks({ className }: { className?: string }) {
  const links = [
    { to: "/about", label: t.nav.about },
    { to: "/privacy", label: t.nav.privacy },
    { to: "/contact", label: t.nav.contact },
  ];
  return (
    <nav className={cn("flex flex-wrap items-center justify-center gap-x-4 gap-y-1 text-xs text-stone-500", className)}>
      {links.map((link) => (
        <NavLink
          key={link.to}
          to={link.to}
          className={({ isActive }) => cn("hover:text-brand-700 hover:underline", isActive && "font-semibold text-brand-700")}
        >
          {link.label}
        </NavLink>
      ))}
    </nav>
  );
}

function Section({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section>
      <h2 className="mb-2 text-base font-bold text-stone-900">{title}</h2>
      {children}
    </section>
  );
}

function Bullets({ items }: { items: readonly string[] }) {
  return (
    <ul className="list-disc space-y-1 ps-5 marker:text-brand-600">
      {items.map((item) => (
        <li key={item}>{item}</li>
      ))}
    </ul>
  );
}

function EmailLink() {
  return (
    <a href={`mailto:${t.contactEmail}`} dir="ltr" className="font-medium text-brand-700 hover:underline">
      {t.contactEmail}
    </a>
  );
}

export function AboutPage() {
  return (
    <InfoShell icon={<Info className="size-6" />} title={t.about.title}>
      <p>{t.about.intro}</p>
      <Section title={t.about.featuresTitle}>
        <Bullets items={t.about.features} />
      </Section>
      <div dir="ltr" className="rounded-2xl border border-brand-100 bg-brand-50/60 px-5 py-4 text-center">
        <p className="text-[11px] font-semibold tracking-[0.2em] text-brand-700 uppercase">{t.about.builtTitle}</p>
        <p className="mt-1 text-base font-semibold text-stone-900">{t.about.builtBy}</p>
        <div className="mt-1 text-sm">
          <EmailLink />
        </div>
      </div>
    </InfoShell>
  );
}

export function PrivacyPage() {
  return (
    <InfoShell icon={<ShieldCheck className="size-6" />} title={t.privacy.title}>
      <p>{t.privacy.intro}</p>
      {t.privacy.sections.map((section) => (
        <Section key={section.title} title={section.title}>
          <Bullets items={section.items} />
        </Section>
      ))}
      <Section title={t.nav.contact}>
        <EmailLink />
      </Section>
      <p className="text-xs text-stone-400">{t.updated}</p>
    </InfoShell>
  );
}

export function ContactPage() {
  return (
    <InfoShell icon={<Mail className="size-6" />} title={t.contact.title}>
      <p>{t.contact.intro}</p>
      <div className="flex flex-col items-start gap-3 rounded-2xl bg-brand-50/60 p-5">
        <span className="text-sm text-stone-500">{t.contact.emailLabel}</span>
        <EmailLink />
        <a
          href={`mailto:${t.contactEmail}`}
          className="inline-flex items-center gap-2 rounded-xl bg-brand-700 px-5 py-2.5 text-sm font-semibold text-white hover:bg-brand-800"
        >
          <Mail className="size-4" />
          {t.contact.send}
        </a>
      </div>
    </InfoShell>
  );
}
