import { MessageCircle, Phone } from "lucide-react";
import { cn } from "@/lib/utils";
import { copy } from "@/lib/copy";

/** Egyptian mobile numbers are stored locally (01xxxxxxxxx); WhatsApp needs the international form. */
function whatsappNumber(phone: string) {
  const digits = phone.replace(/\D/g, "");
  if (digits.startsWith("00")) return digits.slice(2);
  if (digits.startsWith("20")) return digits;
  if (digits.startsWith("0")) return `20${digits.slice(1)}`;
  return digits;
}

/** Call + WhatsApp buttons for a student's phone; renders nothing when there's no number. */
export function ContactButtons({ phone, name, size = "md", className }: { phone: string; name: string; size?: "sm" | "md"; className?: string }) {
  if (!phone?.trim()) return null;
  const base =
    "inline-flex flex-1 items-center justify-center gap-1.5 rounded-xl font-medium transition-colors active:scale-[0.98]";
  const sizing = size === "sm" ? "h-9 px-3 text-sm" : "h-11 px-4 text-sm";
  return (
    <div className={cn("flex gap-2", className)}>
      <a
        href={`tel:${phone.replace(/[^\d+]/g, "")}`}
        aria-label={copy.student.callLabel(name)}
        className={cn(base, sizing, "bg-brand-50 text-brand-800 hover:bg-brand-100")}
      >
        <Phone className="size-4" />
        {copy.student.call}
      </a>
      <a
        href={`https://wa.me/${whatsappNumber(phone)}`}
        target="_blank"
        rel="noopener noreferrer"
        aria-label={copy.student.whatsappLabel(name)}
        className={cn(base, sizing, "bg-[#e7f8ee] text-[#1a7f45] hover:bg-[#d3f2df]")}
      >
        <MessageCircle className="size-4" />
        {copy.student.whatsapp}
      </a>
    </div>
  );
}
