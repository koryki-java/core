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
package ai.koryki.iql.query.viz;

import java.util.ArrayList;
import java.util.List;

/**
 * The parsed {@code VISUALISE} clause of a query: a Grammar-of-Graphics spec
 * that binds the query's (already typed) output columns to visual channels.
 * Consumed by the Vega-Lite emitter; it carries no data itself.
 */
public class Visualise {

    private List<Mapping> global = new ArrayList<>();
    private List<Layer> layers = new ArrayList<>();
    private List<ScaleSpec> scales = new ArrayList<>();
    private FacetSpec facet;
    private Projection project;
    private List<Label> labels = new ArrayList<>();

    public List<Mapping> getGlobal() {
        return global;
    }

    public void setGlobal(List<Mapping> global) {
        this.global = global;
    }

    public List<Layer> getLayers() {
        return layers;
    }

    public void setLayers(List<Layer> layers) {
        this.layers = layers;
    }

    public List<ScaleSpec> getScales() {
        return scales;
    }

    public void setScales(List<ScaleSpec> scales) {
        this.scales = scales;
    }

    public FacetSpec getFacet() {
        return facet;
    }

    public void setFacet(FacetSpec facet) {
        this.facet = facet;
    }

    public Projection getProject() {
        return project;
    }

    public void setProject(Projection project) {
        this.project = project;
    }

    public List<Label> getLabels() {
        return labels;
    }

    public void setLabels(List<Label> labels) {
        this.labels = labels;
    }
}
