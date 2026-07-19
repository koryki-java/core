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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One rendered layer: a {@code DRAW <mark>} (data-bound) or {@code PLACE <mark>}
 * (annotation, aesthetics set as literals only). Layers stack in declaration
 * order, first drawn first (bottom).
 */
public class Layer {

    private String mark;
    private boolean place;
    private List<Mapping> mapping = new ArrayList<>();
    private List<Mapping> remapping = new ArrayList<>();
    private Map<String, Object> settings = new LinkedHashMap<>();

    public String getMark() {
        return mark;
    }

    public void setMark(String mark) {
        this.mark = mark;
    }

    public boolean isPlace() {
        return place;
    }

    public void setPlace(boolean place) {
        this.place = place;
    }

    public List<Mapping> getMapping() {
        return mapping;
    }

    public void setMapping(List<Mapping> mapping) {
        this.mapping = mapping;
    }

    public List<Mapping> getRemapping() {
        return remapping;
    }

    public void setRemapping(List<Mapping> remapping) {
        this.remapping = remapping;
    }

    public Map<String, Object> getSettings() {
        return settings;
    }

    public void setSettings(Map<String, Object> settings) {
        this.settings = settings;
    }
}
