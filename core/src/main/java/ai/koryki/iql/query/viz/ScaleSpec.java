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
 * A {@code SCALE} clause: how one aesthetic's data range maps to its visual
 * range. Shape: {@code SCALE [type] channel [FROM ..] [TO ..] [VIA ..] [SETTING ..] [RENAMING ..]}.
 */
public class ScaleSpec {

    private String channel;
    private String type;
    private List<Object> from;
    private Object to;
    private boolean toPalette;
    private String via;
    private Map<String, Object> settings = new LinkedHashMap<>();
    private List<Rename> renaming = new ArrayList<>();

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public List<Object> getFrom() {
        return from;
    }

    public void setFrom(List<Object> from) {
        this.from = from;
    }

    public Object getTo() {
        return to;
    }

    public void setTo(Object to) {
        this.to = to;
    }

    public boolean isToPalette() {
        return toPalette;
    }

    public void setToPalette(boolean toPalette) {
        this.toPalette = toPalette;
    }

    public String getVia() {
        return via;
    }

    public void setVia(String via) {
        this.via = via;
    }

    public Map<String, Object> getSettings() {
        return settings;
    }

    public void setSettings(Map<String, Object> settings) {
        this.settings = settings;
    }

    public List<Rename> getRenaming() {
        return renaming;
    }

    public void setRenaming(List<Rename> renaming) {
        this.renaming = renaming;
    }
}
