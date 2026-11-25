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
import org.atpfivt.comparison.api.ComparisonContext;
import org.atpfivt.comparison.api.ComparisonSource;
import org.atpfivt.comparison.api.Variant;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.jupiter.api.extension.support.TypeBasedParameterResolver;

import java.util.List;
import java.util.function.Supplier;

public final class JdbcrestBackends implements ComparisonSource<JdbcrestBackends.BackendType> {
    private static final Namespace NAMESPACE = Namespace.create(JdbcrestBackends.class);

    public enum BackendType implements Variant {
        JOOQ_POSTGRES("jooq_postgres", JooqPostgres::new),
        H2("jooq_h2", JooqH2::new),
        POSTGREST("postgrest", Postgrest::new),
        SAP_HANA("jooq_hana", JooqHana::new);

        private final String testName;
        private final Supplier<? extends Backend> create;

        BackendType(String testName, Supplier<? extends Backend> create) {
            this.testName = testName;
            this.create = create;
        }

        @Override
        public String testName() {
            return testName;
        }
    }

    @Override
    public List<BackendType> variants() {
        if ("pro".equalsIgnoreCase(System.getProperty("jooq.edition", "oss"))) {
            return List.of(BackendType.values());
        }
        return List.of(BackendType.JOOQ_POSTGRES, BackendType.H2, BackendType.POSTGREST);
    }

    @Override
    public List<ParameterResolver> parameterResolvers(BackendType variant) {
        return List.of(new TypeBasedParameterResolver<RequestSpecification>() {
            @Override
            public RequestSpecification resolveParameter(ParameterContext parameter, ExtensionContext context) {
                var store = ComparisonContext.classContext(context).getStore(NAMESPACE);
                return store.computeIfAbsent(variant, key -> {
                    try {
                        return key.create.get().start(store);
                    } catch (Exception e) {
                        throw new IllegalStateException("Failed to start " + key.name(), e);
                    }
                }, Endpoint.class).request();
            }
        });
    }
}
