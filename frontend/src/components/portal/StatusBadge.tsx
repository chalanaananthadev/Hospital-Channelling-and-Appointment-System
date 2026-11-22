"use client";

interface StatusBadgeProps {
  value: string;
  label?: string;
}

const TONE_MAP: Record<string, { tone: string; pulse?: boolean }> = {
  ACTIVE: { tone: "emerald" },
  OPEN: { tone: "emerald" },
  COMPLETED: { tone: "emerald" },
  PAID: { tone: "emerald" },

  SCHEDULED: { tone: "sky" },
  BOOKED: { tone: "sky" },
  ONLINE: { tone: "sky" },

  IN_PROGRESS: { tone: "indigo", pulse: true },
  IN_CONSULTATION: { tone: "indigo", pulse: true },
  PHYSICAL: { tone: "indigo" },

  ARRIVED: { tone: "amber" },
  DELAYED: { tone: "amber" },
  PENDING: { tone: "amber" },

  CANCELLED: { tone: "rose" },
  NO_SHOW: { tone: "rose" },
  INACTIVE: { tone: "slate" }
};

export function StatusBadge({ value, label }: StatusBadgeProps) {
  const key = value.toUpperCase();
  const config = TONE_MAP[key] ?? { tone: "slate" };
  const display = label ?? key.replaceAll("_", " ");

  return (
    <span
      className={`mc-badge mc-badge-${config.tone}${
        config.pulse ? " mc-badge-pulse" : ""
      }`}
    >
      <span className="mc-badge-dot" aria-hidden="true" />
      {display}
    </span>
  );
}