/**
 * Parses a date value from the backend.
 * Spring Boot serializes LocalDateTime as a number array [year, month, day, hour, min, sec, nano]
 * when JavaTimeModule is registered without WRITE_DATES_AS_TIMESTAMPS=false.
 * This handles both that format and standard ISO strings.
 */
export function parseDate(value: string | number[] | unknown): Date {
  if (Array.isArray(value)) {
    const [year, month, day, hour = 0, minute = 0, second = 0] = value as number[];
    return new Date(year, month - 1, day, hour, minute, second); // month is 1-indexed from Java
  }
  return new Date(value as string);
}

export function formatDate(value: string | number[] | unknown): string {
  return parseDate(value).toLocaleDateString();
}

export function formatDateTime(value: string | number[] | unknown): string {
  return parseDate(value).toLocaleString();
}
