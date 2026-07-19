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
 * A {@code PROJECT [aes..] TO coord [SETTING ..]} clause selecting the
 * coordinate system (cartesian, polar, ...). Optional leading aesthetics
 * reorder/rename the position channels.
 */
public class Projection {

    private List<String> aesthetics = new ArrayList<>();
    private String coord;
    private Map<String, Object> settings = new LinkedHashMap<>();

    public List<String> getAesthetics() {
        return aesthetics;
    }

    public void setAesthetics(List<String> aesthetics) {
        this.aesthetics = aesthetics;
    }

    public String getCoord() {
        return coord;
    }

    public void setCoord(String coord) {
        this.coord = coord;
    }

    public Map<String, Object> getSettings() {
        return settings;
    }

    public void setSettings(Map<String, Object> settings) {
        this.settings = settings;
    }
}
