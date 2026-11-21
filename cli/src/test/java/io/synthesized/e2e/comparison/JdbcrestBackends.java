/*
 * Copyright 2026 Synthesized Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.synthesized.e2e.comparison;

import io.restassured.specification.RequestSpecification;
import io.synthesized.e2e.comparison.Backend.Endpoint;
import org.atpfivt.comparison.api.ComparisonSource;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public final class JdbcrestBackends implements ComparisonSource<RequestSpecification> {
    @Override
    public Class<RequestSpecification> argumentType() {
        return RequestSpecification.class;
    }

    @Override
    public List<Variant<RequestSpecification>> variants() {
        var variants = new ArrayList<>(List.of(
                backend("jooq_postgres", JooqPostgres::new),
                backend("jooq_h2", JooqH2::new),
                backend("postgrest", Postgrest::new)));
        if ("pro".equalsIgnoreCase(System.getProperty("jooq.edition", "oss"))) {
            variants.add(backend("jooq_hana", JooqHana::new));
        }
        return List.copyOf(variants);
    }

    private static Variant<RequestSpecification> backend(String name, Supplier<? extends Backend> create) {
        return new Variant<>(name, store -> store.computeIfAbsent(name, key -> {
            try {
                return create.get().start(store);
            } catch (Exception e) {
                throw new IllegalStateException("Failed to start " + name, e);
            }
        }, Endpoint.class).request());
    }
}
