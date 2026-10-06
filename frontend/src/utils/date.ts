/**
 * Parses a time from the backend.
 *
 * The backend keeps its times in UTC and sends them as ISO instants ending in "Z".
 * A value without a time zone ("2026-10-06T15:06:46", or the legacy number array
 * [year, month, day, hour, min, sec]) is therefore UTC as well - reading it as local time
 * would show the wrong hour everywhere outside UTC.
 */
export function parseDate(value: string | number[] | unknown): Date {
  if (Array.isArray(value)) {
    const [year, month, day, hour = 0, minute = 0, second = 0] = value as number[];
    return new Date(Date.UTC(year, month - 1, day, hour, minute, second)); // month is 1-indexed from Java
  }
  const text = String(value);
  const hasZone = /(Z|[+-]\d{2}:?\d{2})$/.test(text);
  return new Date(/^\d{4}-\d{2}-\d{2}T/.test(text) && !hasZone ? `${text}Z` : text);
}

export function formatDate(value: string | number[] | unknown): string {
  return parseDate(value).toLocaleDateString();
}

export function formatDateTime(value: string | number[] | unknown): string {
  return parseDate(value).toLocaleString();
}
