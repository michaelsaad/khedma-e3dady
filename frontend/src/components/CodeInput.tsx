import { ClipboardPaste } from "lucide-react";
import { forwardRef, useState } from "react";
import { Input } from "@/components/ui";
import { copy } from "@/lib/copy";
import { extractCode } from "@/lib/device";
import { cn } from "@/lib/utils";

/**
 * The 6-digit code box. `one-time-code` lets iOS/Android offer the code from Mail or Messages
 * above the keyboard; the paste button reads the clipboard for everyone else. Fires
 * `onComplete` as soon as 6 digits are in.
 */
export const CodeInput = forwardRef<
  HTMLInputElement,
  {
    value: string;
    onChange: (value: string) => void;
    onComplete?: (value: string) => void;
    disabled?: boolean;
    autoFocus?: boolean;
    size?: "md" | "lg";
  }
>(function CodeInput({ value, onChange, onComplete, disabled, autoFocus, size = "lg" }, ref) {
  const [canPaste] = useState(() => typeof navigator !== "undefined" && Boolean(navigator.clipboard?.readText));

  const set = (raw: string) => {
    const digits = extractCode(raw);
    onChange(digits);
    if (digits.length === 6) onComplete?.(digits);
  };

  const pasteFromClipboard = async () => {
    try {
      set(await navigator.clipboard.readText());
    } catch {
      /* permission refused: the user can still long-press and paste */
    }
  };

  return (
    <div className="relative">
      <Input
        ref={ref}
        name="one-time-code"
        inputMode="numeric"
        autoComplete="one-time-code"
        pattern="\d{6}"
        maxLength={12}
        dir="ltr"
        placeholder="••••••"
        aria-label={copy.login.code}
        autoFocus={autoFocus}
        value={value}
        onChange={(e) => set(e.target.value)}
        onPaste={(e) => {
          e.preventDefault();
          set(e.clipboardData.getData("text"));
        }}
        disabled={disabled}
        className={cn(
          "text-center font-semibold tracking-[0.5em] tabular",
          size === "lg" ? "h-14 text-2xl" : "h-12 text-xl",
          canPaste && "px-14",
        )}
      />
      {canPaste && (
        <button
          type="button"
          onClick={pasteFromClipboard}
          disabled={disabled}
          className="absolute inset-y-0 left-2 my-auto flex h-9 items-center gap-1 rounded-lg px-2 text-xs font-medium text-brand-700 hover:bg-brand-50"
          aria-label={copy.login.pasteCode}
          title={copy.login.pasteCode}
        >
          <ClipboardPaste className="size-4" />
          <span className="hidden sm:inline">{copy.login.paste}</span>
        </button>
      )}
    </div>
  );
});
