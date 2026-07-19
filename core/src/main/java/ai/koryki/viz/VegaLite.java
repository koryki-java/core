/*
 * Copyright 2025-2026 Johannes Zemlin
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package ai.koryki.viz;

import ai.koryki.antlr.KorykiaiException;
import ai.koryki.catalog.types.TypeDescriptor;
import ai.koryki.iql.query.Out;
import ai.koryki.iql.query.viz.FacetSpec;
import ai.koryki.iql.query.viz.Label;
import ai.koryki.iql.query.viz.Layer;
import ai.koryki.iql.query.viz.Mapping;
import ai.koryki.iql.query.viz.Projection;
import ai.koryki.iql.query.viz.Rename;
import ai.koryki.iql.query.viz.ScaleSpec;
import ai.koryki.iql.query.viz.Visualise;
import ai.koryki.kql.HeaderInfo;
import ai.koryki.kql.KQLTranspiler;
import ai.koryki.viz.stat.StatResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.temporal.Temporal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Compiles a query's {@link Visualise} spec + its output columns + result rows
 * into a Vega-Lite v6 JSON string.
 *
 * <p>Supported: one layer, cartesian, the core channels, per-channel titles,
 * {@code SCALE} (domain/range/scheme/transform/type/reverse) and {@code FACET}
 * (wrap + grid, {@code free} scale resolution). Data is embedded inline as
 * {@code data.values}. Multiple layers and coordinate systems come later.
 */
public class VegaLite {

    private static final String SCHEMA = "https://vega.github.io/schema/vega-lite/v6.json";

    /** KQL mark (geom) → Vega-Lite {@code mark.type}. */
    private static final Map<String, String> MARK = markTable();

    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    /**
     * @param viz     the parsed VISUALISE clause
     * @param columns output columns (name = FETCH alias, plus resolved type), in result order
     * @param rows    result rows as positional value lists, aligned with {@code columns}
     */
    public String render(Visualise viz, List<VizColumn> columns, List<List<Object>> rows) {
        return render(viz, columns, rows, java.util.Collections.emptyMap());
    }

    /**
     * @param baseColumns/baseRows the raw query result shared by ordinary layers
     * @param stats                per-layer aggregated data for statistical geoms
     */
    public String render(Visualise viz, List<VizColumn> baseColumns, List<List<Object>> baseRows,
                         Map<Layer, StatResult> stats) {
        if (viz == null) {
            throw new KorykiaiException("query has no VISUALISE clause");
        }
        Map<String, VizColumn> cols = new LinkedHashMap<>();
        for (VizColumn c : baseColumns) {
            cols.put(c.getName(), c);
        }
        Map<String, ScaleSpec> scales = new LinkedHashMap<>();
        for (ScaleSpec s : viz.getScales()) {
            scales.put(s.getChannel(), s);
        }

        // every field a VISUALISE mapping references must be a real output column; REMAPPING is
        // exempt — it deliberately targets statistical-transform outputs (density, count, …)
        checkColumns(viz.getGlobal(), cols);
        for (Layer l : viz.getLayers()) {
            checkColumns(l.getMapping(), cols);
        }

        ObjectNode spec = mapper.createObjectNode();
        spec.put("$schema", SCHEMA);
        String title = label(viz, "title");
        if (title != null) {
            spec.put("title", title);
        }

        // the "unit" spec (mark + encoding, or a layer[] for multiple DRAW/PLACE
        // layers); faceting nests it under "spec".
        Projection proj = viz.getProject();
        if (proj != null && !"cartesian".equals(proj.getCoord()) && !"polar".equals(proj.getCoord())
                && !isMap(proj)) {
            throw new KorykiaiException("unsupported coordinate system: " + proj.getCoord()
                    + " (supported: cartesian, polar, and map projections)");
        }
        List<Layer> layers = viz.getLayers();
        boolean anyPlace = false;
        boolean anySpatial = false;
        boolean sharesFlatData = layers.isEmpty();
        for (Layer l : layers) {
            anyPlace |= l.isPlace();
            boolean spatial = isSpatial(l);
            anySpatial |= spatial;
            // an ordinary DRAW layer (not place/stat/spatial) draws the shared flat rows
            sharesFlatData |= !l.isPlace() && !stats.containsKey(l) && !spatial;
        }
        // map backgrounds (sphere, graticule, world basemap) drawn under the data
        List<ObjectNode> backgrounds = mapBackgrounds(proj);
        boolean multi = layers.size() > 1 || anyPlace || anySpatial || !stats.isEmpty() || !backgrounds.isEmpty();
        ObjectNode unit = mapper.createObjectNode();
        if (!multi) {
            Layer layer = layers.isEmpty() ? null : layers.get(0);
            buildMark(unit, layer, proj);
            unit.set("encoding", layerEncoding(viz, layer, cols, scales, proj));
        } else {
            // ordinary DRAW layers share the top-level data; PLACE, statistical,
            // spatial (choropleth) and map-background layers carry their own data.
            ArrayNode arr = unit.putArray("layer");
            for (ObjectNode bg : backgrounds) {
                arr.add(bg);
            }
            for (Layer layer : layers) {
                ObjectNode l = arr.addObject();
                if (stats.containsKey(layer)) {
                    statLayer(l, stats.get(layer), proj);
                } else if (layer.isPlace()) {
                    placeLayer(l, layer, proj);
                } else if (isSpatial(layer)) {
                    spatialLayer(l, layer, viz, baseColumns, baseRows, cols, scales, proj);
                } else {
                    buildMark(l, layer, proj);
                    l.set("encoding", layerEncoding(viz, layer, cols, scales, proj));
                }
            }
        }
        addScaleResolve(unit, layers, viz, cols, stats); // feature 4: independent y for mixed dimensions

        FacetSpec facet = viz.getFacet();
        if (facet != null && !facet.getVars().isEmpty()) {
            spec.set("facet", facetNode(facet));
            Integer ncol = intOf(facet.getSettings().get("ncol"));
            if (ncol != null) {
                spec.put("columns", ncol);
            }
            ObjectNode resolve = freeResolve(facet);
            if (resolve != null) {
                spec.set("resolve", resolve);
            }
            spec.set("spec", unit);
        } else {
            spec.setAll(unit);
        }
        if (isMap(proj)) {
            spec.set("projection", projectionNode(proj));
        } else if (anySpatial) {
            spec.putObject("projection").put("type", "equalEarth"); // geoshape needs a projection
        }
        // top-level data is the shared raw result; only emitted when a layer draws it flat
        // (statistical, choropleth and PLACE layers carry their own data instead)
        if (!baseRows.isEmpty() && sharesFlatData) {
            spec.set("data", data(baseColumns, baseRows));
        }

        try {
            return mapper.writeValueAsString(spec);
        } catch (JsonProcessingException e) {
            throw new KorykiaiException("failed to serialize Vega-Lite spec: " + e.getMessage());
        }
    }

    /** Convenience: pull the columns (name + resolved type) straight from a transpiler. */
    public static List<VizColumn> columns(KQLTranspiler transpiler) {
        List<Out> outs = transpiler.getOut();
        List<HeaderInfo> infos = transpiler.infos(HeaderInfo::new);
        List<VizColumn> cols = new ArrayList<>();
        for (int i = 0; i < outs.size(); i++) {
            Out o = outs.get(i);
            String name = o.getHeader() != null ? o.getHeader() : Integer.toString(o.getIdx());
            cols.add(new VizColumn(name, infos.get(i).getTypeDescriptor()));
        }
        return cols;
    }

    private ObjectNode layerEncoding(Visualise viz, Layer layer, Map<String, VizColumn> cols,
                                     Map<String, ScaleSpec> scales, Projection proj) {
        // global mappings first, then the layer's own mappings/remappings override
        Map<String, Mapping> byChannel = new LinkedHashMap<>();
        for (Mapping m : viz.getGlobal()) {
            put(byChannel, m);
        }
        if (layer != null) {
            for (Mapping m : layer.getMapping()) {
                put(byChannel, m);
            }
            for (Mapping m : layer.getRemapping()) {
                put(byChannel, m);
            }
        }
        // wildcard '*': map every known-channel output column not already mapped
        if (hasWildcard(viz.getGlobal()) || (layer != null && hasWildcard(layer.getMapping()))) {
            for (String col : cols.keySet()) {
                if (KNOWN_CHANNELS.contains(col) && !byChannel.containsKey(col)) {
                    Mapping w = new Mapping();
                    w.setChannel(col);
                    w.setColumn(col);
                    byChannel.put(col, w);
                }
            }
        }

        ObjectNode enc = mapper.createObjectNode();
        Map<String, String> posType = new HashMap<>();   // x/y → resolved field type
        String groupField = null;                        // color/fill field, for dodge offset
        for (Map.Entry<String, Mapping> e : byChannel.entrySet()) {
            String channelName = e.getKey();
            if ("geometry".equals(channelName)) {
                continue; // geoshape reads geometry from each Feature, not from encoding
            }
            Mapping m = e.getValue();
            String vlName = vlChannel(channelName, proj);
            ObjectNode channel = enc.putObject(vlName);
            if (m.getColumn() != null) {
                channel.put("field", m.getColumn());
                if (!fieldOnly(vlName)) {
                    VizColumn col = cols.get(m.getColumn());
                    ScaleSpec scale = scales.get(channelName);
                    String ft = fieldType(col != null ? col.getType() : null, scale);
                    channel.put("type", ft);
                    String t = label(viz, channelName);      // explicit LABEL wins
                    if (t == null) {
                        t = semanticTitle(col);              // feature 2/3: metric name (+ real unit)
                    }
                    if (t != null) {
                        channel.put("title", t);
                    }
                    applyScale(channel, scale);
                    applyGuides(channel, isPositional(vlName), scale);
                    applyGrainFormat(channel, isPositional(vlName), ft, col); // feature 3: temporal format
                    if ("x".equals(channelName) || "y".equals(channelName)) {
                        posType.put(channelName, ft);
                    }
                    if (groupField == null && ("color".equals(channelName) || "fill".equals(channelName))) {
                        groupField = m.getColumn();
                    }
                }
            } else {
                putValue(channel, "value", m.getLiteral());
            }
        }
        if (layer != null && !isPolar(proj) && !isMap(proj)) {
            applyQuerySort(enc, byChannel, cols, proj); // feature 5: order a discrete axis by ORDER BY
            applyPosition(enc, str(layer.getSettings().get("position")), posType, groupField);
        }
        return enc;
    }

    /**
     * Feature 2/3: a semantic axis/legend title. Uses the concluded metric name
     * ("Net revenue" rather than the raw expression) and suffixes a real physical
     * unit. Returns null when there is nothing semantic to add (Vega then falls
     * back to the field name).
     */
    private static String semanticTitle(VizColumn col) {
        if (col == null) {
            return null;
        }
        String unit = meaningfulUnit(col.getUnit());
        String metric = col.getMetric();
        if (metric != null) {
            return unit != null ? metric + " (" + unit + ")" : metric;
        }
        if (unit != null) {
            return col.getName() + " (" + unit + ")";
        }
        return null;
    }

    /** Dimensionless (null) and count ("1") units add nothing to a title. */
    private static String meaningfulUnit(String unit) {
        return unit == null || unit.isBlank() || "1".equals(unit) ? null : unit;
    }

    /** Feature 3: a date/time axis (or legend) format for a temporal channel from its grain. */
    private void applyGrainFormat(ObjectNode channel, boolean positional, String fieldType, VizColumn col) {
        if (col == null || col.getGrain() == null || !"temporal".equals(fieldType)) {
            return;
        }
        String fmt = GRAIN_FORMAT.get(col.getGrain().toLowerCase(java.util.Locale.ROOT));
        if (fmt != null) {
            guide(channel, positional).put("format", fmt);
        }
    }

    private static final Map<String, String> GRAIN_FORMAT = grainFormatTable();

    private static Map<String, String> grainFormatTable() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("year", "%Y");
        m.put("quarter", "%Y-%m");
        m.put("month", "%Y-%m");
        m.put("week", "%Y-%m-%d");
        m.put("day", "%Y-%m-%d");
        m.put("date", "%Y-%m-%d");
        m.put("hour", "%Y-%m-%d %H:00");
        m.put("minute", "%H:%M");
        return m;
    }

    /**
     * Feature 4: when ordinary DRAW layers map different physical dimensions to y
     * (e.g. a money total and a count), give y an independent scale per layer so
     * neither is crushed onto the other's axis. Same dimension → shared (the default).
     */
    private void addScaleResolve(ObjectNode unit, List<Layer> layers, Visualise viz,
                                 Map<String, VizColumn> cols, Map<Layer, StatResult> stats) {
        Set<String> yDims = new LinkedHashSet<>();
        for (Layer layer : layers) {
            if (layer.isPlace() || isSpatial(layer) || stats.containsKey(layer)) {
                continue; // only plain measure layers carry a comparable field dimension
            }
            String field = channelColumn(viz, layer, "y");
            VizColumn c = field == null ? null : cols.get(field);
            if (c != null && c.getQuantityDim() != null) {
                yDims.add(c.getQuantityDim());
            }
        }
        if (yDims.size() >= 2) {
            unit.putObject("resolve").putObject("scale").put("y", "independent");
        }
    }

    /** The output column mapped to a channel for a layer (layer mapping/remapping overrides global). */
    private static String channelColumn(Visualise viz, Layer layer, String channel) {
        String col = null;
        for (Mapping m : viz.getGlobal()) {
            if (channel.equals(m.getChannel()) && m.getColumn() != null) {
                col = m.getColumn();
            }
        }
        for (Mapping m : layer.getMapping()) {
            if (channel.equals(m.getChannel()) && m.getColumn() != null) {
                col = m.getColumn();
            }
        }
        for (Mapping m : layer.getRemapping()) {
            if (channel.equals(m.getChannel()) && m.getColumn() != null) {
                col = m.getColumn();
            }
        }
        return col;
    }

    /**
     * Feature 5: reflect the query's {@code ORDER BY} on the chart. Sorts the
     * discrete (nominal/ordinal) positional axis — either by its own values when
     * it is the ordered column, or by the ordered measure otherwise. Explicit
     * sorts and non-discrete axes are left untouched.
     */
    private void applyQuerySort(ObjectNode enc, Map<String, Mapping> byChannel,
                                Map<String, VizColumn> cols, Projection proj) {
        String orderField = null;
        String orderDir = null;
        int best = Integer.MAX_VALUE;
        for (Mapping m : byChannel.values()) {
            if (m.getColumn() == null) {
                continue;
            }
            VizColumn c = cols.get(m.getColumn());
            if (c == null || c.getOrderPriority() == null || c.getOrderPriority() >= best) {
                continue;
            }
            best = c.getOrderPriority();
            orderField = m.getColumn();
            orderDir = c.getOrderDirection();
        }
        if (orderField == null) {
            return;
        }
        String vlOrder = "DESC".equalsIgnoreCase(orderDir) ? "descending" : "ascending";
        for (String channelName : byChannel.keySet()) {
            String vlName = vlChannel(channelName, proj);
            if (!"x".equals(vlName) && !"y".equals(vlName)) {
                continue;
            }
            JsonNode node = enc.get(vlName);
            if (!(node instanceof ObjectNode)) {
                continue;
            }
            ObjectNode ch = (ObjectNode) node;
            if (!ch.has("field") || ch.has("sort")) {
                continue;
            }
            String ft = ch.path("type").asText("");
            if (!"nominal".equals(ft) && !"ordinal".equals(ft)) {
                continue; // value axes don't need an explicit sort
            }
            if (orderField.equals(ch.get("field").asText())) {
                ch.put("sort", vlOrder); // the axis is itself the ordered column
            } else {
                ObjectNode s = ch.putObject("sort");
                s.put("field", orderField); // discrete axis ordered by the sorted measure
                s.put("order", vlOrder);
            }
            return;
        }
    }

    /**
     * A PLACE annotation layer: every aesthetic is a literal from SETTING (no
     * data mapping). Positional aesthetics use {@code datum} (data space),
     * others {@code value} (visual space). The layer carries its own single-row
     * data so the annotation renders once rather than per result row.
     */
    private void placeLayer(ObjectNode l, Layer layer, Projection proj) {
        buildMark(l, layer, proj);
        ObjectNode enc = mapper.createObjectNode();
        for (Map.Entry<String, Object> s : layer.getSettings().entrySet()) {
            Object v = s.getValue();
            if (v instanceof List) {
                throw new KorykiaiException("PLACE setting '" + s.getKey()
                        + "' with an array value (multi-annotation) is not supported yet");
            }
            String ch = vlChannel(s.getKey(), proj);
            ObjectNode channel = enc.putObject(ch);
            if (isPositional(ch)) {
                putValue(channel, "datum", v);
            } else {
                putValue(channel, "value", v);
            }
        }
        l.set("encoding", enc);
        l.putObject("data").putArray("values").addObject();
    }

    /**
     * A statistical layer rendered from its pre-aggregated {@link StatResult}:
     * its own inline data plus an encoding built from the stat's channel bindings
     * (e.g. a histogram's pre-binned {@code x}/{@code x2}/{@code y}).
     */
    private void statLayer(ObjectNode l, StatResult stat, Projection proj) {
        Map<String, TypeDescriptor> types = new LinkedHashMap<>();
        for (VizColumn c : stat.getColumns()) {
            types.put(c.getName(), c.getType());
        }
        // the layer owns the aggregated rows; single-part inlines mark+encoding,
        // a composite (e.g. boxplot) nests its marks as a sub-layer[] over that data
        ArrayNode values = l.putObject("data").putArray("values");
        for (List<Object> row : stat.getRows()) {
            ObjectNode obj = values.addObject();
            for (int i = 0; i < stat.getColumns().size(); i++) {
                putValue(obj, stat.getColumns().get(i).getName(), i < row.size() ? row.get(i) : null);
            }
        }
        if (stat.getParts().size() == 1) {
            StatResult.Part part = stat.getParts().get(0);
            l.putObject("mark").put("type", partMark(part));
            l.set("encoding", bindingEncoding(part, types, proj));
        } else {
            ArrayNode inner = l.putArray("layer");
            for (StatResult.Part part : stat.getParts()) {
                ObjectNode pl = inner.addObject();
                pl.putObject("mark").put("type", partMark(part));
                pl.set("encoding", bindingEncoding(part, types, proj));
            }
        }
    }

    private static String partMark(StatResult.Part part) {
        return MARK.getOrDefault(part.getMark(), part.getMark());
    }

    private ObjectNode bindingEncoding(StatResult.Part part, Map<String, TypeDescriptor> types, Projection proj) {
        ObjectNode enc = mapper.createObjectNode();
        for (StatResult.ChannelBinding b : part.getBindings()) {
            String ch = vlChannel(b.getChannel(), proj);
            ObjectNode channel = enc.putObject(ch);
            channel.put("field", b.getField());
            if (!fieldOnly(ch)) {
                channel.put("type", VegaLiteTypes.channelType(types.get(b.getField())));
            }
            if (b.isBinned()) {
                channel.put("bin", "binned");
            }
            if (b.getTitle() != null) {
                channel.put("title", b.getTitle());
            }
        }
        return enc;
    }

    /** Secondary position channels take only a field, no type/scale. */
    private static final Set<String> SECONDARY = Set.of("x2", "y2", "theta2", "radius2");

    /** Channels rendered as field-only (no type/scale/axis): secondaries + geographic position. */
    private static boolean fieldOnly(String channel) {
        return SECONDARY.contains(channel) || "longitude".equals(channel) || "latitude".equals(channel);
    }

    private void buildMark(ObjectNode parent, Layer layer, Projection proj) {
        ObjectNode mark = parent.putObject("mark");
        if (isPolar(proj)) {
            mark.put("type", "arc");
            Double inner = dbl(proj.getSettings().get("inner"));
            if (inner != null && inner > 0) {
                mark.put("innerRadius", (int) Math.round(inner * 90)); // fraction → px of a ~90px radius
            }
        } else {
            mark.put("type", markType(layer));
        }
    }

    /**
     * Position adjustment for the layer. {@code stack} stacks the quantitative
     * positional channel; {@code dodge} un-stacks it and offsets bars by the
     * grouping (color/fill) field along the categorical axis. {@code jitter} is
     * not yet supported.
     */
    private void applyPosition(ObjectNode enc, String position, Map<String, String> posType, String groupField) {
        if (position == null) {
            return;
        }
        // validate the value up front, so an unsupported adjustment errors even when there is
        // no quantitative positional field for the application step below to act on
        switch (position) {
            case "identity":
            case "stack":
            case "dodge":
                break;
            case "jitter":
                throw new KorykiaiException("position => 'jitter' is not supported yet "
                        + "(supported: identity, stack, dodge)");
            default:
                throw new KorykiaiException("unknown position adjustment '" + position
                        + "' (supported: identity, stack, dodge)");
        }
        String quant = "quantitative".equals(posType.get("y")) ? "y"
                : "quantitative".equals(posType.get("x")) ? "x" : null;
        if (quant == null) {
            return;
        }
        ObjectNode q = (ObjectNode) enc.get(quant);
        if (q == null || !q.has("field")) {
            return;
        }
        String categorical = "y".equals(quant) ? "x" : "y";
        switch (position) {
            case "stack":
                q.put("stack", "zero");
                break;
            case "dodge":
                q.putNull("stack");
                if (groupField != null) {
                    ObjectNode offset = enc.putObject(categorical + "Offset");
                    offset.put("field", groupField);
                    offset.put("type", "nominal");
                }
                break;
            default:
                break; // identity: no-op (the default layout)
        }
    }

    private static void put(Map<String, Mapping> byChannel, Mapping m) {
        if (!m.isWildcard() && m.getChannel() != null) {
            byChannel.put(m.getChannel(), m);
        }
    }

    /** Rejects a mapping to a field that is not one of the query's output columns. */
    private static void checkColumns(List<Mapping> mappings, Map<String, VizColumn> cols) {
        for (Mapping m : mappings) {
            if (!m.isWildcard() && m.getColumn() != null && !cols.containsKey(m.getColumn())) {
                throw new KorykiaiException("VISUALISE maps channel '" + m.getChannel()
                        + "' to unknown column '" + m.getColumn() + "' — not in the query's output");
            }
        }
    }

    // ── scales ──────────────────────────────────────────────────────────────

    private static String fieldType(TypeDescriptor type, ScaleSpec scale) {
        if (scale != null) {
            if (isTemporalTransform(scale.getVia())) {
                return "temporal";
            }
            if (scale.getType() != null) {
                switch (scale.getType()) {
                    case "continuous":
                    case "binned":
                        return "quantitative";
                    case "discrete":
                        return "nominal";
                    case "ordinal":
                        return "ordinal";
                    default:
                        break; // identity → inferred (scale disabled in applyScale)
                }
            }
        }
        return VegaLiteTypes.channelType(type);
    }

    private void applyScale(ObjectNode channel, ScaleSpec scale) {
        if (scale == null) {
            return;
        }
        if ("identity".equals(scale.getType())) {
            channel.putNull("scale"); // Vega-Lite: disable scaling, use raw values
            return;
        }
        ObjectNode sc = mapper.createObjectNode();
        if (scale.getFrom() != null) {
            ArrayNode domain = sc.putArray("domain");
            for (Object o : scale.getFrom()) {
                addValue(domain, o);
            }
        }
        if (scale.getTo() != null) {
            if (scale.isToPalette()) {
                sc.put("scheme", String.valueOf(scale.getTo()));
            } else if (scale.getTo() instanceof List) {
                ArrayNode range = sc.putArray("range");
                for (Object o : (List<?>) scale.getTo()) {
                    addValue(range, o);
                }
            }
        }
        applyTransform(sc, scale.getVia());
        if (isTrue(scale.getSettings().get("reverse"))) {
            sc.put("reverse", true);
        }
        if (sc.size() > 0) {
            channel.set("scale", sc);
        }
    }

    /** {@code SETTING breaks} → axis/legend {@code values}/{@code tickCount}; {@code RENAMING} → {@code labelExpr}. */
    private void applyGuides(ObjectNode channel, boolean positional, ScaleSpec scale) {
        if (scale == null) {
            return;
        }
        Object breaks = scale.getSettings().get("breaks");
        if (breaks instanceof List) {
            ArrayNode vals = guide(channel, positional).putArray("values");
            for (Object o : (List<?>) breaks) {
                addValue(vals, o);
            }
        } else if (breaks != null) {
            Integer n = intOf(breaks);
            if (n != null) {
                guide(channel, positional).put("tickCount", n);
            }
        }
        if (!scale.getRenaming().isEmpty()) {
            String expr = labelExpr(scale.getRenaming());
            if (expr != null) {
                guide(channel, positional).put("labelExpr", expr);
            }
        }
    }

    private ObjectNode guide(ObjectNode channel, boolean positional) {
        String key = positional ? "axis" : "legend";
        if (channel.get(key) instanceof ObjectNode) {
            return (ObjectNode) channel.get(key);
        }
        ObjectNode g = mapper.createObjectNode();
        channel.set(key, g);
        return g;
    }

    /** Build a Vega {@code labelExpr} nested-ternary from direct RENAMING entries (template {@code *} deferred). */
    private static String labelExpr(List<Rename> renaming) {
        StringBuilder expr = new StringBuilder();
        int matched = 0;
        for (Rename r : renaming) {
            if ("*".equals(r.getFrom())) {
                throw new KorykiaiException("RENAMING with a wildcard template "
                        + "('*' => …) is not supported yet");
            }
            String from = r.getFrom() instanceof String
                    ? "'" + ((String) r.getFrom()).replace("'", "\\'") + "'"
                    : String.valueOf(r.getFrom());
            String to = r.getTo() == null ? "''" : "'" + r.getTo().replace("'", "\\'") + "'";
            expr.append("datum.value == ").append(from).append(" ? ").append(to).append(" : ");
            matched++;
        }
        if (matched == 0) {
            return null;
        }
        expr.append("datum.label");
        return expr.toString();
    }

    private static boolean hasWildcard(List<Mapping> mappings) {
        for (Mapping m : mappings) {
            if (m.isWildcard()) {
                return true;
            }
        }
        return false;
    }

    private static void applyTransform(ObjectNode sc, String via) {
        if (via == null) {
            return;
        }
        switch (via) {
            case "log":
            case "ln":
                sc.put("type", "log");
                break;
            case "log10":
                sc.put("type", "log");
                sc.put("base", 10);
                break;
            case "log2":
                sc.put("type", "log");
                sc.put("base", 2);
                break;
            case "sqrt":
                sc.put("type", "sqrt");
                break;
            case "square":
                sc.put("type", "pow");
                sc.put("exponent", 2);
                break;
            case "symlog":
            case "pseudo_log":
            case "asinh":
                sc.put("type", "symlog");
                break;
            default:
                break; // date/datetime/time → field type; unknown ignored
        }
    }

    private static boolean isTemporalTransform(String via) {
        return "date".equals(via) || "datetime".equals(via) || "time".equals(via);
    }

    // ── facets ──────────────────────────────────────────────────────────────

    private ObjectNode facetNode(FacetSpec f) {
        ObjectNode node = mapper.createObjectNode();
        if (f.getBy().isEmpty()) {
            node.put("field", f.getVars().get(0));
            node.put("type", "nominal");
        } else {
            node.set("row", facetField(f.getVars().get(0)));
            node.set("column", facetField(f.getBy().get(0)));
        }
        return node;
    }

    private ObjectNode facetField(String column) {
        ObjectNode n = mapper.createObjectNode();
        n.put("field", column);
        n.put("type", "nominal");
        return n;
    }

    private ObjectNode freeResolve(FacetSpec f) {
        Object free = f.getSettings().get("free");
        if (free == null) {
            return null;
        }
        Set<String> axes = new LinkedHashSet<>();
        if (free instanceof List) {
            for (Object o : (List<?>) free) {
                axes.add(String.valueOf(o));
            }
        } else {
            axes.add(String.valueOf(free));
        }
        ObjectNode scale = mapper.createObjectNode();
        for (String a : axes) {
            if ("x".equals(a) || "y".equals(a)) {
                scale.put(a, "independent");
            }
        }
        if (scale.size() == 0) {
            return null;
        }
        ObjectNode resolve = mapper.createObjectNode();
        resolve.set("scale", scale);
        return resolve;
    }

    // ── data ────────────────────────────────────────────────────────────────

    private ObjectNode data(List<VizColumn> columns, List<List<Object>> rows) {
        ObjectNode data = mapper.createObjectNode();
        ArrayNode values = data.putArray("values");
        for (List<Object> row : rows) {
            ObjectNode obj = values.addObject();
            for (int i = 0; i < columns.size(); i++) {
                Object cell = i < row.size() ? row.get(i) : null;
                putValue(obj, columns.get(i).getName(), cell);
            }
        }
        return data;
    }

    private void putValue(ObjectNode node, String key, Object v) {
        if (v == null) {
            node.putNull(key);
        } else if (v instanceof BigDecimal) {
            node.put(key, (BigDecimal) v);
        } else if (v instanceof Double || v instanceof Float) {
            node.put(key, ((Number) v).doubleValue());
        } else if (v instanceof BigInteger) {
            node.put(key, new BigDecimal((BigInteger) v));
        } else if (v instanceof Number) {
            node.put(key, ((Number) v).longValue());
        } else if (v instanceof Boolean) {
            node.put(key, (Boolean) v);
        } else if (v instanceof Temporal) {
            node.put(key, v.toString()); // java.time ISO-8601
        } else {
            node.put(key, v.toString());
        }
    }

    private void addValue(ArrayNode arr, Object v) {
        if (v == null) {
            arr.addNull();
        } else if (v instanceof BigDecimal) {
            arr.add((BigDecimal) v);
        } else if (v instanceof Double || v instanceof Float) {
            arr.add(((Number) v).doubleValue());
        } else if (v instanceof BigInteger) {
            arr.add(new BigDecimal((BigInteger) v));
        } else if (v instanceof Number) {
            arr.add(((Number) v).longValue());
        } else if (v instanceof Boolean) {
            arr.add((Boolean) v);
        } else if (v instanceof Temporal) {
            arr.add(v.toString());
        } else {
            arr.add(v.toString());
        }
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private static String markType(Layer layer) {
        if (layer == null || layer.getMark() == null) {
            return "point";
        }
        return MARK.getOrDefault(layer.getMark(), layer.getMark());
    }

    private static String label(Visualise viz, String target) {
        for (Label l : viz.getLabels()) {
            if (target.equals(l.getTarget())) {
                return l.getValue();
            }
        }
        return null;
    }

    private static Integer intOf(Object v) {
        if (v instanceof Number) {
            return ((Number) v).intValue();
        }
        if (v instanceof String) {
            try {
                return Integer.parseInt((String) v);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private static boolean isTrue(Object v) {
        return v instanceof Boolean ? (Boolean) v : "true".equals(String.valueOf(v));
    }

    private static String str(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    private static Double dbl(Object v) {
        if (v instanceof Number) {
            return ((Number) v).doubleValue();
        }
        if (v instanceof String) {
            try {
                return Double.parseDouble((String) v);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private static boolean isPolar(Projection proj) {
        return proj != null && "polar".equals(proj.getCoord());
    }

    // ── maps (Vega-Lite-native projections; d3 projects client-side) ─────────

    /** ggsql/proj coordinate name → Vega/d3 projection {@code type}. */
    private static final Map<String, String> MAP_PROJECTION = mapProjectionTable();

    private static boolean isMap(Projection proj) {
        return proj != null && MAP_PROJECTION.containsKey(proj.getCoord());
    }

    /** Top-level Vega-Lite {@code projection} from the PROJECT coord + settings. */
    private ObjectNode projectionNode(Projection proj) {
        Object target = proj.getSettings().get("target");
        Object source = proj.getSettings().get("source");
        if (target != null || source != null) {
            throw new KorykiaiException("arbitrary CRS (target/source) is not supported by the "
                    + "Vega-Lite projection backend — use a named projection");
        }
        ObjectNode node = mapper.createObjectNode();
        node.put("type", MAP_PROJECTION.get(proj.getCoord()));
        // origin (central meridian, or lon/lat centre) → d3 rotate = negative of the centre
        Object origin = proj.getSettings().get("origin");
        double[] o = pair(origin);
        if (o != null) {
            ArrayNode rotate = node.putArray("rotate");
            rotate.add(-o[0]);
            rotate.add(-o[1]);
        }
        // standard parallels for conic projections (harmless on others)
        double[] p = pair(proj.getSettings().get("parallel"));
        if (p != null) {
            ArrayNode parallels = node.putArray("parallels");
            parallels.add(p[0]);
            parallels.add(p[1]);
        }
        // bounds is ignored: d3 fits the projection to the data via width/height
        return node;
    }

    /** A scalar → {value, 0}; a 2-array → {a, b}; else null. */
    private static double[] pair(Object v) {
        if (v instanceof Number) {
            return new double[]{((Number) v).doubleValue(), 0};
        }
        if (v instanceof List) {
            List<?> l = (List<?>) v;
            if (l.size() >= 2 && l.get(0) instanceof Number && l.get(1) instanceof Number) {
                return new double[]{((Number) l.get(0)).doubleValue(), ((Number) l.get(1)).doubleValue()};
            }
            if (l.size() == 1 && l.get(0) instanceof Number) {
                return new double[]{((Number) l.get(0)).doubleValue(), 0};
            }
        }
        return null;
    }

    /**
     * Relative URL of the vendored world basemap: Natural Earth 110m countries as
     * TopoJSON (the {@code world-atlas} build), served by the web app itself so a
     * rendered spec never reaches out to an external host.
     */
    private static final String BASEMAP_URL = "/geo/countries-110m.json";

    /**
     * Background geoshape layers drawn under the data of every map projection:
     * the sphere (ocean), a graticule and the world basemap (country polygons).
     * All three are on by default so a map never renders as marks on a blank
     * canvas; {@code PROJECT SETTING sphere/graticule/basemap => false} turns one
     * off, and {@code basemap} also accepts a replacement URL (TopoJSON with a
     * {@code countries} feature).
     */
    private List<ObjectNode> mapBackgrounds(Projection proj) {
        List<ObjectNode> bg = new ArrayList<>();
        if (!isMap(proj)) {
            return bg;
        }
        if (!isFalse(proj.getSettings().get("sphere"))) {
            ObjectNode sphere = mapper.createObjectNode();
            sphere.putObject("data").put("sphere", true);
            ObjectNode mark = sphere.putObject("mark");
            mark.put("type", "geoshape");
            mark.put("fill", "#eaf3fb");
            mark.putNull("stroke");
            bg.add(sphere);
        }
        if (!isFalse(proj.getSettings().get("graticule"))) {
            ObjectNode grat = mapper.createObjectNode();
            grat.putObject("data").put("graticule", true);
            ObjectNode mark = grat.putObject("mark");
            mark.put("type", "geoshape");
            mark.put("filled", false);
            mark.put("stroke", "#dddddd");
            mark.put("strokeWidth", 0.5);
            bg.add(grat);
        }
        Object basemap = proj.getSettings().get("basemap");
        if (!isFalse(basemap)) {
            ObjectNode land = mapper.createObjectNode();
            ObjectNode data = land.putObject("data");
            data.put("url", basemap instanceof String && !"true".equals(basemap)
                    ? (String) basemap : BASEMAP_URL);
            ObjectNode format = data.putObject("format");
            format.put("type", "topojson");
            format.put("feature", "countries");
            ObjectNode mark = land.putObject("mark");
            mark.put("type", "geoshape");
            mark.put("fill", "#e2e8f0");
            mark.put("stroke", "#ffffff");
            mark.put("strokeWidth", 0.5);
            bg.add(land);
        }
        return bg;
    }

    /** Explicitly disabled: boolean {@code false} or the string {@code 'false'}. */
    private static boolean isFalse(Object v) {
        return Boolean.FALSE.equals(v) || "false".equals(v);
    }

    // ── choropleths (DRAW spatial → geoshape with inline GeoJSON Features) ───

    private static boolean isSpatial(Layer layer) {
        return "spatial".equals(layer.getMark());
    }

    /** A choropleth layer: geoshape mark + its own GeoJSON-Feature data from the base rows. */
    private void spatialLayer(ObjectNode l, Layer layer, Visualise viz, List<VizColumn> columns,
                              List<List<Object>> rows, Map<String, VizColumn> cols,
                              Map<String, ScaleSpec> scales, Projection proj) {
        String geometry = geometryColumn(viz, layer);
        if (geometry == null) {
            throw new KorykiaiException("spatial layer needs a column mapped to geometry");
        }
        buildMark(l, layer, proj); // "spatial" → geoshape via the mark table
        l.set("encoding", layerEncoding(viz, layer, cols, scales, proj)); // geometry channel skipped
        l.set("data", spatialData(geometry, columns, rows));
    }

    /**
     * Result rows as a GeoJSON values array: the geometry column becomes each
     * Feature's {@code geometry} (parsed from GeoJSON text), other columns become
     * top-level Feature keys that {@code encoding.<ch>.field} references.
     */
    private ObjectNode spatialData(String geometryColumn, List<VizColumn> columns, List<List<Object>> rows) {
        int geomIdx = -1;
        for (int i = 0; i < columns.size(); i++) {
            if (columns.get(i).getName().equals(geometryColumn)) {
                geomIdx = i;
                break;
            }
        }
        ObjectNode data = mapper.createObjectNode();
        ArrayNode values = data.putArray("values");
        for (List<Object> row : rows) {
            ObjectNode feature = values.addObject();
            feature.put("type", "Feature");
            feature.set("geometry", parseGeoJson(geomIdx >= 0 && geomIdx < row.size() ? row.get(geomIdx) : null));
            for (int i = 0; i < columns.size(); i++) {
                if (i != geomIdx) {
                    putValue(feature, columns.get(i).getName(), i < row.size() ? row.get(i) : null);
                }
            }
        }
        return data;
    }

    private JsonNode parseGeoJson(Object cell) {
        if (cell == null) {
            return NullNode.getInstance();
        }
        try {
            return mapper.readTree(cell.toString());
        } catch (JsonProcessingException e) {
            throw new KorykiaiException("geometry column is not valid GeoJSON: " + e.getMessage());
        }
    }

    private static String geometryColumn(Visualise viz, Layer layer) {
        for (Mapping m : layer.getMapping()) {
            if ("geometry".equals(m.getChannel()) && m.getColumn() != null) {
                return m.getColumn();
            }
        }
        for (Mapping m : viz.getGlobal()) {
            if ("geometry".equals(m.getChannel()) && m.getColumn() != null) {
                return m.getColumn();
            }
        }
        return null;
    }

    private static Map<String, String> mapProjectionTable() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("crs", "equalEarth");         // generic → sensible world default
        m.put("map", "equalEarth");
        m.put("geographic", "equirectangular");
        m.put("equirectangular", "equirectangular");
        m.put("mercator", "mercator");
        m.put("transverse_mercator", "transverseMercator");
        m.put("utm", "transverseMercator"); // approximation (no zone in d3)
        m.put("orthographic", "orthographic");
        m.put("stereographic", "stereographic");
        m.put("gnomonic", "gnomonic");
        m.put("albers", "albers");
        m.put("equal_area", "conicEqualArea");
        m.put("lambert_conformal", "conicConformal");
        m.put("lambert", "azimuthalEqualArea");
        m.put("azimuthal_equidistant", "azimuthalEquidistant");
        m.put("natural", "naturalEarth1");
        m.put("equal_earth", "equalEarth");
        // extended (render only if the viewer's Vega build registered d3-geo-projection):
        m.put("miller", "miller");
        m.put("mollweide", "mollweide");
        m.put("sinusoidal", "sinusoidal");
        m.put("eckert4", "eckert4");
        m.put("winkel_tripel", "winkel3");
        m.put("robinson", "robinson");
        m.put("igh", "interruptedHomolosine");
        // also accept the Vega/d3 names themselves (identity pass-through)
        for (String vega : new java.util.ArrayList<>(m.values())) {
            m.putIfAbsent(vega, vega);
        }
        return m;
    }

    /**
     * KQL channel → Vega-Lite channel under the active coordinate system.
     * Polar: {@code y→theta}, {@code x→radius}. Cartesian with an explicit
     * aesthetic order (e.g. {@code PROJECT y, x TO cartesian}) swaps the axes:
     * the first listed aesthetic drives x, the second y.
     */
    private static String vlChannel(String channel, Projection proj) {
        // cartesian axis reorder compares against the original KQL channel name
        if (proj != null && "cartesian".equals(proj.getCoord()) && proj.getAesthetics().size() >= 2) {
            List<String> aes = proj.getAesthetics();
            if (channel.equals(aes.get(0))) {
                return "x";
            }
            if (channel.equals(aes.get(1))) {
                return "y";
            }
        }
        String ch = CHANNEL_ALIAS.getOrDefault(channel, channel);
        if (proj != null && "polar".equals(proj.getCoord())) {
            if ("y".equals(ch)) {
                return "theta";
            }
            if ("x".equals(ch)) {
                return "radius";
            }
        }
        if (isMap(proj)) {
            if ("x".equals(ch)) {
                return "longitude";
            }
            if ("y".equals(ch)) {
                return "latitude";
            }
        }
        return ch;
    }

    /** KQL/ggsql aesthetic names → their Vega-Lite channel names. */
    private static final Map<String, String> CHANNEL_ALIAS = Map.of(
            "colour", "color",
            "label", "text",
            "linewidth", "strokeWidth",
            "linetype", "strokeDash",
            "fontsize", "size",
            "lon", "longitude",
            "lat", "latitude");

    /** Output-column names eligible for wildcard {@code *} mapping. */
    private static final Set<String> KNOWN_CHANNELS = Set.of(
            "x", "y", "color", "fill", "size", "shape", "opacity",
            "theta", "radius", "text", "detail", "tooltip");

    private static boolean isPositional(String channel) {
        return POSITIONAL.contains(channel);
    }

    private static final Set<String> POSITIONAL = Set.of(
            "x", "y", "x2", "y2", "xend", "yend", "theta", "theta2",
            "radius", "radius2", "latitude", "longitude");

    private static Map<String, String> markTable() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("point", "point");
        m.put("line", "line");
        m.put("path", "line");
        m.put("bar", "bar");
        m.put("area", "area");
        m.put("tile", "rect");
        m.put("polygon", "line");
        m.put("ribbon", "area");
        m.put("histogram", "bar");
        m.put("density", "area");
        m.put("violin", "line");
        m.put("boxplot", "boxplot");
        m.put("text", "text");
        m.put("segment", "rule");
        m.put("smooth", "line");
        m.put("rule", "rule");
        m.put("range", "rule");
        m.put("spatial", "geoshape");
        return m;
    }
}
