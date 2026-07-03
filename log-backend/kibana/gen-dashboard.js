// One-off generator for kibana/dashboards/log-backend-dashboard.ndjson.
// Run with `node gen-dashboard.js` after editing, then delete/keep as needed.
const fs = require("fs");
const path = require("path");

const KIBANA_VERSION = "8.17.3";

function tsvbSeries({ id, field, agg, label, color, splitByReplica, chartType = "line" }) {
  const series = {
    id,
    color,
    split_mode: splitByReplica ? "terms" : "everything",
    metrics: [{ id: `${id}-metric`, type: agg, field }],
    separate_axis: 0,
    axis_position: "right",
    formatter: "number",
    chart_type: chartType,
    line_width: 2,
    point_size: 2,
    fill: 0.2,
    stacked: "none",
    label,
  };
  if (splitByReplica) {
    series.terms_field = "instance";
    series.terms_size = "10";
    series.terms_order_by = "_key";
    series.terms_direction = "asc";
  }
  return series;
}

function tsvbTimeseriesViz({ id, title, filterQuery, series }) {
  const visState = {
    title,
    type: "metrics",
    params: {
      id: `${id}-panel`,
      type: "timeseries",
      series,
      time_field: "@timestamp",
      index_pattern: "log-backend-*",
      interval: "",
      axis_position: "left",
      axis_formatter: "number",
      axis_scale: "normal",
      show_legend: 1,
      show_grid: 1,
      tooltip_mode: "show_all",
      use_kibana_indexes: false,
      drop_last_bucket: 0,
      filter: { query: filterQuery, language: "kuery" },
      annotations: [],
      bar_color_rules: [],
      gauge_color_rules: [],
      background_color_rules: [],
    },
    aggs: [],
  };
  return {
    type: "visualization",
    id,
    attributes: {
      title,
      visState: JSON.stringify(visState),
      uiStateJSON: "{}",
      description: "",
      version: 1,
      kibanaSavedObjectMeta: {
        searchSourceJSON: JSON.stringify({ query: { query: "", language: "kuery" }, filter: [] }),
      },
    },
    references: [],
    migrationVersion: { visualization: "8.9.0" },
    coreMigrationVersion: KIBANA_VERSION,
    typeMigrationVersion: "8.9.0",
  };
}

function tsvbTableViz({ id, title, filterQuery, columns }) {
  const visState = {
    title,
    type: "metrics",
    params: {
      id: `${id}-panel`,
      type: "table",
      time_field: "@timestamp",
      index_pattern: "log-backend-*",
      interval: "",
      use_kibana_indexes: false,
      drop_last_bucket: 0,
      filter: { query: filterQuery, language: "kuery" },
      pivot_id: "instance",
      pivot_type: "keyword",
      pivot_label: "Replica",
      pivot_rows: "10",
      series: columns.map(({ id: cid, field, agg, label }) => ({
        id: cid,
        color: "#68BC00",
        split_mode: "everything",
        metrics: [{ id: `${cid}-metric`, type: agg, field }],
        label,
      })),
      bar_color_rules: [],
      gauge_color_rules: [],
      background_color_rules: [],
    },
    aggs: [],
  };
  return {
    type: "visualization",
    id,
    attributes: {
      title,
      visState: JSON.stringify(visState),
      uiStateJSON: "{}",
      description: "",
      version: 1,
      kibanaSavedObjectMeta: {
        searchSourceJSON: JSON.stringify({ query: { query: "", language: "kuery" }, filter: [] }),
      },
    },
    references: [],
    migrationVersion: { visualization: "8.9.0" },
    coreMigrationVersion: KIBANA_VERSION,
    typeMigrationVersion: "8.9.0",
  };
}

const indexPattern = {
  type: "index-pattern",
  id: "log-backend-index-pattern",
  attributes: {
    title: "log-backend-*",
    timeFieldName: "@timestamp",
  },
  references: [],
  migrationVersion: { "index-pattern": "8.0.0" },
  coreMigrationVersion: KIBANA_VERSION,
  typeMigrationVersion: "8.0.0",
};

const METRICS_FILTER = "metrics.type: metrics";

const totalsByReplica = tsvbTableViz({
  id: "log-backend-totals-by-replica",
  title: "Log Backend - Totals by Replica",
  filterQuery: METRICS_FILTER,
  columns: [
    { id: "col-received", field: "metrics.total_received", agg: "max", label: "Total Received" },
    { id: "col-inserted", field: "metrics.total_inserted", agg: "max", label: "Total Inserted" },
    { id: "col-dropped", field: "metrics.total_dropped", agg: "max", label: "Total Dropped" },
    { id: "col-failed", field: "metrics.total_failed", agg: "max", label: "Total Failed" },
  ],
});

const insertedTpsByReplica = tsvbTimeseriesViz({
  id: "log-backend-inserted-tps-by-replica",
  title: "Log Backend - Inserted TPS by Replica",
  filterQuery: METRICS_FILTER,
  series: [
    tsvbSeries({
      id: "inserted-tps",
      field: "metrics.inserted_tps",
      agg: "avg",
      label: "Inserted TPS",
      color: "#00B3A4",
      splitByReplica: true,
    }),
  ],
});

const droppedTpsByReplica = tsvbTimeseriesViz({
  id: "log-backend-dropped-tps-by-replica",
  title: "Log Backend - Dropped TPS by Replica (overload signal)",
  filterQuery: METRICS_FILTER,
  series: [
    tsvbSeries({
      id: "dropped-tps",
      field: "metrics.dropped_tps",
      agg: "avg",
      label: "Dropped TPS",
      color: "#BF1B00",
      splitByReplica: true,
    }),
  ],
});

const queueFillByReplica = tsvbTimeseriesViz({
  id: "log-backend-queue-fill-by-replica",
  title: "Log Backend - Queue Fill % by Replica",
  filterQuery: METRICS_FILTER,
  series: [
    tsvbSeries({
      id: "queue-fill",
      field: "metrics.queue_fill_pct",
      agg: "avg",
      label: "Queue Fill %",
      color: "#F98510",
      splitByReplica: true,
    }),
  ],
});

const jvmMemByReplica = tsvbTimeseriesViz({
  id: "log-backend-jvm-mem-by-replica",
  title: "Log Backend - JVM Memory Used (MB) by Replica",
  filterQuery: METRICS_FILTER,
  series: [
    tsvbSeries({
      id: "jvm-used",
      field: "metrics.jvm_used_mb",
      agg: "avg",
      label: "JVM Used MB",
      color: "#4796BC",
      splitByReplica: true,
    }),
  ],
});

const panels = [
  { ref: "panel_1", id: totalsByReplica.id, x: 0, y: 0, w: 48, h: 12 },
  { ref: "panel_2", id: insertedTpsByReplica.id, x: 0, y: 12, w: 24, h: 15 },
  { ref: "panel_3", id: droppedTpsByReplica.id, x: 24, y: 12, w: 24, h: 15 },
  { ref: "panel_4", id: queueFillByReplica.id, x: 0, y: 27, w: 24, h: 15 },
  { ref: "panel_5", id: jvmMemByReplica.id, x: 24, y: 27, w: 24, h: 15 },
];

const dashboard = {
  type: "dashboard",
  id: "log-backend-dashboard",
  attributes: {
    title: "Log Backend - Stats",
    hits: 0,
    description: "Realtime ingest/consumer stats for the log-backend service, broken down by replica",
    panelsJSON: JSON.stringify(
      panels.map((p, i) => ({
        version: KIBANA_VERSION,
        type: "visualization",
        gridData: { x: p.x, y: p.y, w: p.w, h: p.h, i: String(i + 1) },
        panelIndex: String(i + 1),
        embeddableConfig: {},
        panelRefName: p.ref,
      }))
    ),
    optionsJSON: JSON.stringify({
      useMargins: true,
      syncColors: false,
      syncCursor: true,
      syncTooltips: false,
      hidePanelTitles: false,
    }),
    version: 1,
    timeRestore: true,
    timeFrom: "now-15m",
    timeTo: "now",
    refreshInterval: { pause: false, value: 5000 },
    kibanaSavedObjectMeta: {
      searchSourceJSON: JSON.stringify({ query: { query: "", language: "kuery" }, filter: [] }),
    },
  },
  references: panels.map((p) => ({ name: p.ref, type: "visualization", id: p.id })),
  migrationVersion: { dashboard: "8.9.0" },
  coreMigrationVersion: KIBANA_VERSION,
  typeMigrationVersion: "8.9.0",
};

const objects = [
  indexPattern,
  totalsByReplica,
  insertedTpsByReplica,
  droppedTpsByReplica,
  queueFillByReplica,
  jvmMemByReplica,
  dashboard,
];

const outDir = path.join(__dirname, "dashboards");
fs.mkdirSync(outDir, { recursive: true });
const outFile = path.join(outDir, "log-backend-dashboard.ndjson");
fs.writeFileSync(outFile, objects.map((o) => JSON.stringify(o)).join("\n") + "\n");
console.log("Wrote", outFile);
