import { useEffect, useRef, useState } from 'react';
import type { KeyboardEvent, PointerEvent } from 'react';
import { useQuery, keepPreviousData } from '@tanstack/react-query';
import { dlqTopicsApi } from '../../api/dlqTopics';
import type { TrendPoint, TrendRangeCode } from '../../types';

const RANGES: { code: TrendRangeCode; label: string }[] = [
  { code: '24h', label: '24 hours' },
  { code: '7d', label: '7 days' },
];

// One series colour, stepped for each card background (both pass 3:1 contrast against it)
const SERIES_STROKE = 'stroke-[#2a78d6] dark:stroke-[#3987e5]';
const SERIES_FILL = 'fill-[#2a78d6] dark:fill-[#3987e5]';
const SERIES_KEY = 'bg-[#2a78d6] dark:bg-[#3987e5]';
// Rings and gaps are drawn in the card colour, so marks stay apart without borders
const SURFACE_STROKE = 'stroke-white dark:stroke-gray-800';

const HEIGHT = 150;
const MARGIN = { top: 10, right: 8, bottom: 24, left: 40 };

type Measure = 'pending' | 'newMessages';
type Hover = { index: number; source: Measure } | null;

const hourFormat = new Intl.DateTimeFormat(undefined, { hour: '2-digit', minute: '2-digit', hourCycle: 'h23' });
const weekdayFormat = new Intl.DateTimeFormat(undefined, { weekday: 'short' });
const periodFormat = new Intl.DateTimeFormat(undefined, {
  weekday: 'short', day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit', hourCycle: 'h23',
});

function periodLabel(point: TrendPoint, bucketMinutes: number): string {
  const start = new Date(point.time);
  const end = new Date(start.getTime() + bucketMinutes * 60_000);
  return `${periodFormat.format(start)} – ${hourFormat.format(end)}`;
}

/**
 * Pending messages and new failures over the last day or week, for one DLQ.
 * Two small charts instead of one with two scales: the numbers mean different things.
 */
export function DlqTrendCard({ dlqTopicId }: { dlqTopicId: string }) {
  const [range, setRange] = useState<TrendRangeCode>('24h');
  const [showTable, setShowTable] = useState(false);
  const [hover, setHover] = useState<Hover>(null);

  const { data, isLoading, isFetching, isError } = useQuery({
    queryKey: ['dlqTrend', dlqTopicId, range],
    queryFn: () => dlqTopicsApi.getTrend(dlqTopicId, range),
    placeholderData: keepPreviousData,
    refetchInterval: 60_000, // a new sample is taken every minute
  });

  const points = data?.points ?? [];
  const bucketMinutes = data?.bucketMinutes ?? 60;
  const hasHistory = points.some((point) => point.pending !== null);
  const latestPending = [...points].reverse().find((point) => point.pending !== null)?.pending;
  const newInRange = points.reduce((sum, point) => sum + (point.newMessages ?? 0), 0);
  const rangeLabel = RANGES.find((r) => r.code === range)!.label;
  const perPeriod = bucketMinutes === 60 ? 'per hour' : `per ${bucketMinutes / 60} hours`;

  const toggleClass = (active: boolean) =>
    `px-3 py-1 text-xs font-medium rounded-md transition-colors ${
      active
        ? 'bg-white dark:bg-gray-600 text-gray-900 dark:text-white shadow-sm'
        : 'text-gray-500 dark:text-gray-400 hover:text-gray-900 dark:hover:text-white'
    }`;

  return (
    <div className="bg-white dark:bg-gray-800 rounded-lg shadow-sm border border-gray-200 dark:border-gray-700 p-6 mb-6">
      <div className="flex flex-wrap items-center justify-between gap-3 mb-4">
        <div>
          <h3 className="text-lg font-semibold text-gray-900 dark:text-white">Trend</h3>
          <p className="text-xs text-gray-500 dark:text-gray-400">Is this DLQ growing or shrinking?</p>
        </div>
        <div className="flex items-center gap-3">
          <div className="flex p-0.5 rounded-lg bg-gray-100 dark:bg-gray-700" role="group" aria-label="Time range">
            {RANGES.map((r) => (
              <button
                key={r.code}
                onClick={() => { setRange(r.code); setHover(null); }}
                aria-pressed={range === r.code}
                className={toggleClass(range === r.code)}
              >
                {r.label}
              </button>
            ))}
          </div>
          <button
            onClick={() => setShowTable((shown) => !shown)}
            className="text-xs text-orange-600 dark:text-orange-400 hover:underline"
          >
            {showTable ? 'Show chart' : 'Show as table'}
          </button>
        </div>
      </div>

      {isLoading ? (
        <p className="text-sm text-gray-500 dark:text-gray-400 py-8 text-center">Loading trend...</p>
      ) : isError ? (
        <p className="text-sm text-red-600 dark:text-red-400 py-8 text-center">Could not load the trend.</p>
      ) : !hasHistory ? (
        <p className="text-sm text-gray-500 dark:text-gray-400 py-8 text-center">
          No history yet. DLQ Manager records this DLQ once a minute, so the chart fills in over the next hour.
        </p>
      ) : showTable ? (
        <TrendTable points={points} bucketMinutes={bucketMinutes} />
      ) : (
        // While another range loads, the current chart stays in place, dimmed
        <div className={`grid grid-cols-1 lg:grid-cols-2 gap-6 transition-opacity ${isFetching ? 'opacity-60' : ''}`}>
          <div>
            <p className="text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
              Pending messages
              {latestPending != null && (
                <span className="font-normal text-gray-500 dark:text-gray-400"> · {latestPending.toLocaleString()} now</span>
              )}
            </p>
            <TrendPlot
              points={points} measure="pending" range={range} bucketMinutes={bucketMinutes}
              hover={hover} onHover={setHover}
            />
          </div>
          <div>
            <p className="text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
              New failures {perPeriod}
              <span className="font-normal text-gray-500 dark:text-gray-400">
                {' '}· {newInRange.toLocaleString()} in the last {rangeLabel}
              </span>
            </p>
            <TrendPlot
              points={points} measure="newMessages" range={range} bucketMinutes={bucketMinutes}
              hover={hover} onHover={setHover}
            />
          </div>
        </div>
      )}
    </div>
  );
}

// ---- One chart ----

function TrendPlot({ points, measure, range, bucketMinutes, hover, onHover }: {
  points: TrendPoint[];
  measure: Measure;
  range: TrendRangeCode;
  bucketMinutes: number;
  hover: Hover;
  onHover: (hover: Hover) => void;
}) {
  const [containerRef, width] = useWidth<HTMLDivElement>();

  const plotWidth = Math.max(0, width - MARGIN.left - MARGIN.right);
  const plotHeight = HEIGHT - MARGIN.top - MARGIN.bottom;
  const baseline = MARGIN.top + plotHeight;
  const values = points.map((point) => point[measure]);
  const ticks = niceTicks(Math.max(0, ...values.map((value) => value ?? 0)));
  const top = ticks[ticks.length - 1];
  const slot = points.length > 0 ? plotWidth / points.length : 0;
  const x = (index: number) => MARGIN.left + slot * (index + 0.5);
  const y = (value: number) => baseline - (value / top) * plotHeight;

  const hoverIndex = hover?.index ?? null;
  const indexAt = (clientX: number, element: Element) => {
    const offset = clientX - element.getBoundingClientRect().left - MARGIN.left;
    return Math.min(points.length - 1, Math.max(0, Math.floor(offset / slot)));
  };

  // Keyboard: focus the chart, then left/right walk through the points
  const onKeyDown = (event: KeyboardEvent<SVGSVGElement>) => {
    const current = hoverIndex ?? points.length - 1;
    if (event.key === 'ArrowLeft') onHover({ index: Math.max(0, current - 1), source: measure });
    else if (event.key === 'ArrowRight') onHover({ index: Math.min(points.length - 1, current + 1), source: measure });
    else if (event.key === 'Escape') onHover(null);
    else return;
    event.preventDefault();
  };

  return (
    <div ref={containerRef} className="relative">
      {width > 0 && (
        <svg
          width={width}
          height={HEIGHT}
          tabIndex={0}
          role="img"
          aria-label={`${measure === 'pending' ? 'Pending messages' : 'New failures'}, last ${range === '24h' ? '24 hours' : '7 days'}. Use the left and right arrow keys to read each point.`}
          className="block focus:outline-none focus-visible:ring-2 focus-visible:ring-orange-500 rounded"
          onPointerMove={(event: PointerEvent<SVGSVGElement>) =>
            onHover({ index: indexAt(event.clientX, event.currentTarget), source: measure })}
          onPointerLeave={() => onHover(null)}
          onFocus={() => onHover({ index: hoverIndex ?? points.length - 1, source: measure })}
          onBlur={() => onHover(null)}
          onKeyDown={onKeyDown}
        >
          {/* Grid and y-axis */}
          {ticks.map((tick) => (
            <g key={tick}>
              <line
                x1={MARGIN.left} x2={width - MARGIN.right} y1={y(tick)} y2={y(tick)}
                strokeWidth={1} shapeRendering="crispEdges"
                className="stroke-gray-200 dark:stroke-gray-700"
              />
              <text
                x={MARGIN.left - 6} y={y(tick)} dy="0.32em" textAnchor="end"
                className="fill-gray-500 dark:fill-gray-400 text-[11px] tabular-nums"
              >
                {tick.toLocaleString()}
              </text>
            </g>
          ))}

          {/* X-axis labels: every 6 hours (24h) or each midnight (7 days) */}
          {points.map((point, index) => {
            const label = axisLabel(point, range);
            return label ? (
              <text
                key={point.time} x={x(index)} y={HEIGHT - 6} textAnchor="middle"
                className="fill-gray-500 dark:fill-gray-400 text-[11px]"
              >
                {label}
              </text>
            ) : null;
          })}

          {measure === 'pending'
            ? <PendingLine values={values} x={x} y={y} baseline={baseline} hoverIndex={hoverIndex} />
            : <NewFailureColumns values={values} x={x} y={y} baseline={baseline} slot={slot} hoverIndex={hoverIndex} />}

          {/* Crosshair: shown on both charts, so the same moment lines up */}
          {hoverIndex !== null && (
            <line
              x1={x(hoverIndex)} x2={x(hoverIndex)} y1={MARGIN.top} y2={baseline}
              strokeWidth={1} shapeRendering="crispEdges"
              className="stroke-gray-400 dark:stroke-gray-500 pointer-events-none"
            />
          )}
        </svg>
      )}

      {hover && hover.source === measure && points[hover.index] && (
        <TrendTooltip
          point={points[hover.index]}
          bucketMinutes={bucketMinutes}
          left={Math.min(Math.max(0, x(hover.index) - 90), Math.max(0, width - 190))}
        />
      )}
    </div>
  );
}

function PendingLine({ values, x, y, baseline, hoverIndex }: {
  values: (number | null)[];
  x: (index: number) => number;
  y: (value: number) => number;
  baseline: number;
  hoverIndex: number | null;
}) {
  // Periods without history break the line instead of being drawn as zero
  const runs: number[][] = [];
  values.forEach((value, index) => {
    if (value === null) return;
    const run = runs[runs.length - 1];
    if (run && run[run.length - 1] === index - 1) run.push(index);
    else runs.push([index]);
  });
  const lastIndex = runs.length > 0 ? runs[runs.length - 1].slice(-1)[0] : null;
  const dot = (index: number) => (
    <circle
      key={`dot-${index}`} cx={x(index)} cy={y(values[index]!)} r={4} strokeWidth={2}
      className={`${SERIES_FILL} ${SURFACE_STROKE} pointer-events-none`}
    />
  );

  return (
    <g>
      {runs.map((run) => {
        const line = run.map((index, i) => `${i === 0 ? 'M' : 'L'}${x(index)},${y(values[index]!)}`).join(' ');
        const area = `M${x(run[0])},${baseline} ${run.map((index) => `L${x(index)},${y(values[index]!)}`).join(' ')} L${x(run[run.length - 1])},${baseline} Z`;
        return (
          <g key={run[0]} className="pointer-events-none">
            <path d={area} className={SERIES_FILL} fillOpacity={0.1} />
            <path d={line} fill="none" strokeWidth={2} strokeLinejoin="round" strokeLinecap="round" className={SERIES_STROKE} />
            {/* A single point on its own would be invisible as a line */}
            {run.length === 1 && dot(run[0])}
          </g>
        );
      })}
      {lastIndex !== null && dot(lastIndex)}
      {hoverIndex !== null && values[hoverIndex] !== null && hoverIndex !== lastIndex && dot(hoverIndex)}
    </g>
  );
}

function NewFailureColumns({ values, x, y, baseline, slot, hoverIndex }: {
  values: (number | null)[];
  x: (index: number) => number;
  y: (value: number) => number;
  baseline: number;
  slot: number;
  hoverIndex: number | null;
}) {
  // Thin columns that leave air between them, rounded only at the data end
  const columnWidth = Math.max(2, Math.min(24, slot * 0.6));
  return (
    <g className="pointer-events-none">
      {values.map((value, index) => {
        if (!value) return null;
        const left = x(index) - columnWidth / 2;
        const right = left + columnWidth;
        const columnTop = y(value);
        const radius = Math.min(4, columnWidth / 2, baseline - columnTop);
        const d = `M${left},${baseline} V${columnTop + radius} A${radius},${radius} 0 0 1 ${left + radius},${columnTop}`
          + ` H${right - radius} A${radius},${radius} 0 0 1 ${right},${columnTop + radius} V${baseline} Z`;
        const dimmed = hoverIndex !== null && hoverIndex !== index;
        return <path key={index} d={d} className={SERIES_FILL} fillOpacity={dimmed ? 0.45 : 1} />;
      })}
    </g>
  );
}

function TrendTooltip({ point, bucketMinutes, left }: { point: TrendPoint; bucketMinutes: number; left: number }) {
  const row = (value: number | null, label: string) => (
    <div className="flex items-center gap-2">
      <span className={`inline-block w-3 h-0.5 rounded ${SERIES_KEY}`} />
      <span className="font-semibold text-gray-900 dark:text-white tabular-nums">
        {value === null ? '–' : value.toLocaleString()}
      </span>
      <span className="text-gray-500 dark:text-gray-400">{label}</span>
    </div>
  );
  return (
    <div
      className="absolute top-0 z-10 w-[180px] pointer-events-none rounded-lg border border-gray-200 dark:border-gray-600 bg-white dark:bg-gray-700 shadow-md px-3 py-2 text-xs"
      style={{ left }}
    >
      <p className="text-gray-500 dark:text-gray-400 mb-1">{periodLabel(point, bucketMinutes)}</p>
      {row(point.pending, 'pending')}
      {row(point.newMessages, 'new failures')}
    </div>
  );
}

// ---- Table view: every value without hovering ----

function TrendTable({ points, bucketMinutes }: { points: TrendPoint[]; bucketMinutes: number }) {
  return (
    <div className="max-h-72 overflow-y-auto">
      <table className="w-full text-sm">
        <thead className="sticky top-0 bg-gray-50 dark:bg-gray-700">
          <tr className="text-left text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wider">
            <th className="px-3 py-2">Period</th>
            <th className="px-3 py-2 text-right">Pending at end</th>
            <th className="px-3 py-2 text-right">New failures</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-gray-200 dark:divide-gray-700">
          {[...points].reverse().map((point) => (
            <tr key={point.time} className="text-gray-700 dark:text-gray-300">
              <td className="px-3 py-1.5">{periodLabel(point, bucketMinutes)}</td>
              <td className="px-3 py-1.5 text-right tabular-nums">{point.pending?.toLocaleString() ?? '–'}</td>
              <td className="px-3 py-1.5 text-right tabular-nums">{point.newMessages?.toLocaleString() ?? '–'}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

// ---- Helpers ----

function axisLabel(point: TrendPoint, range: TrendRangeCode): string | null {
  const start = new Date(point.time);
  if (range === '24h') {
    return start.getHours() % 6 === 0 ? hourFormat.format(start) : null;
  }
  // "Tue 29" (some locales would print "29 Tue" for weekday + day)
  return start.getHours() === 0 ? `${weekdayFormat.format(start)} ${start.getDate()}` : null;
}

/**
 * Clean y-axis steps (1, 2, 5 x 10^n), about three of them, from 0 to at least max
 */
function niceTicks(max: number): number[] {
  if (max <= 0) return [0, 1];
  const rough = max / 3;
  const magnitude = 10 ** Math.floor(Math.log10(rough));
  const step = Math.max(1, [1, 2, 5, 10].map((m) => m * magnitude).find((s) => s >= rough)!);
  const ticks = [0];
  while (ticks[ticks.length - 1] < max) ticks.push(ticks[ticks.length - 1] + step);
  return ticks;
}

/**
 * Width of an element, kept up to date as the window resizes (the chart is drawn in pixels)
 */
function useWidth<T extends HTMLElement>() {
  const ref = useRef<T>(null);
  const [width, setWidth] = useState(0);
  useEffect(() => {
    const element = ref.current;
    if (!element) return;
    const observer = new ResizeObserver(([entry]) => setWidth(Math.floor(entry.contentRect.width)));
    observer.observe(element);
    return () => observer.disconnect();
  }, []);
  return [ref, width] as const;
}
