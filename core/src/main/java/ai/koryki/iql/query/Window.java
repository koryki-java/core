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
package ai.koryki.iql.query;

import java.util.List;

public class Window {
    private List<Expression> partition;
    private List<Expression> order;

    private Order.SORT sort;
    private Limit upper;
    private Limit lower;

    public List<Expression> getPartition() {
        return partition;
    }

    public void setPartition(List<Expression> partition) {
        this.partition = partition;
    }

    public List<Expression> getOrder() {
        return order;
    }

    public void setOrder(List<Expression> order) {
        this.order = order;
    }

    public Order.SORT isOrderDesc() {
        return sort;
    }

    public void setOrderDesc(Order.SORT orderDesc) {
        this.sort = orderDesc;
    }

    public Limit getUpper() {
        return upper;
    }

    public void setUpper(Limit upper) {
        this.upper = upper;
    }

    public Limit getLower() {
        return lower;
    }

    public void setLower(Limit lower) {
        this.lower = lower;
    }
}
