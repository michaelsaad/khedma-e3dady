import { Church, HandHeart, Trophy } from "lucide-react";
import type { ReactNode } from "react";
import { Link } from "react-router";
import { formatNumber, gradeLabel } from "@/lib/i18n";
import type { StudentCard } from "@/lib/types";
import { cn } from "@/lib/utils";
import { ContactButtons } from "./ContactButtons";
import { Avatar, Badge, Card } from "./ui";
import { copy } from "@/lib/copy";

export function StatTile({
  label,
  value,
  hint,
  icon,
  tone = "stone",
}: {
  label: string;
  value: ReactNode;
  hint?: ReactNode;
  icon?: ReactNode;
  tone?: "stone" | "brand" | "gold" | "blue";
}) {
  const tones = {
    stone: "bg-stone-100 text-stone-600",
    brand: "bg-brand-50 text-brand-700",
    gold: "bg-gold-100 text-gold-600",
    blue: "bg-mass-50 text-mass-700",
  };
  return (
    <Card className="p-4">
      <div className="flex items-start justify-between gap-2">
        <p className="text-sm text-stone-500">{label}</p>
        {icon && <span className={cn("grid size-8 place-items-center rounded-lg", tones[tone])}>{icon}</span>}
      </div>
      <p className="mt-2 text-2xl font-bold tabular">{value}</p>
      {hint && <p className="mt-0.5 text-xs text-stone-500">{hint}</p>}
    </Card>
  );
}

function RateBar({ value, tone }: { value: number; tone: "brand" | "blue" }) {
  return (
    <div className="h-1.5 w-full overflow-hidden rounded-full bg-stone-100">
      <div
        className={cn("h-full rounded-full", tone === "brand" ? "bg-brand-600" : "bg-mass-400")}
        style={{ width: `${Math.min(100, Math.max(0, value))}%` }}
      />
    </div>
  );
}

export function RankBadge({ rank }: { rank: number }) {
  const medal = rank === 1 ? "bg-gold-500 text-white" : rank === 2 ? "bg-stone-400 text-white" : rank === 3 ? "bg-amber-700 text-white" : "bg-stone-100 text-stone-600";
  return (
    <span className={cn("grid size-7 shrink-0 place-items-center rounded-full text-xs font-bold tabular", medal)}>
      {formatNumber(rank)}
    </span>
  );
}

/** Student card used on the dashboard grid. */
export function StudentCardView({ card }: { card: StudentCard }) {
  return (
    <Card className="flex h-full flex-col p-4 transition-shadow hover:shadow-md">
      <Link to={`/s/${card.id}`} className="block flex-1">
        <div className="flex items-start gap-3">
          <Avatar name={card.name} />
          <div className="min-w-0 flex-1">
            <p className="truncate font-semibold text-stone-900">{card.name}</p>
            <div className="mt-1 flex flex-wrap gap-1">
              <Badge>{gradeLabel[card.grade]}</Badge>
              {card.groupName && <Badge tone="blue">{card.groupName}</Badge>}
            </div>
          </div>
          <RankBadge rank={card.rank} />
        </div>

        <div className="mt-4 flex items-baseline gap-1">
          <Trophy className="size-4 self-center text-gold-500" />
          <span className="text-2xl font-bold tabular">{formatNumber(card.points)}</span>
          <span className="text-sm text-stone-500">{copy.common.point}</span>
        </div>

        <div className="mt-3 space-y-2 text-xs text-stone-600">
          <RateRow icon={<HandHeart className="size-3.5" />} label={copy.common.serviceTitle} count={card.serviceCount} total={card.meetingsHeld} rate={card.serviceRate} tone="brand" />
          <RateRow icon={<Church className="size-3.5" />} label={copy.common.massTitle} count={card.massCount} total={card.meetingsHeld} rate={card.massRate} tone="blue" />
        </div>
      </Link>
      <ContactButtons phone={card.phone} name={card.name} size="sm" className="mt-4 border-t border-stone-100 pt-3" />
    </Card>
  );
}

export function RateRow({
  icon,
  label,
  count,
  total,
  rate,
  tone,
}: {
  icon: ReactNode;
  label: string;
  count: number;
  total: number;
  rate: number;
  tone: "brand" | "blue";
}) {
  return (
    <div>
      <div className="mb-1 flex items-center justify-between">
        <span className="flex items-center gap-1">
          {icon}
          {label}
        </span>
        <span className="tabular">
          {formatNumber(count)}/{formatNumber(total)} · {formatNumber(rate)}٪
        </span>
      </div>
      <RateBar value={rate} tone={tone} />
    </div>
  );
}
