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
package io.synthesized.e2e;

import io.synthesized.e2e.comparison.ComparisonContext;
import io.synthesized.e2e.comparison.JooqPostgresContext;
import io.synthesized.e2e.comparison.JooqHanaContext;
import io.synthesized.e2e.comparison.PostgrestContext;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestTemplateInvocationContext;
import org.junit.jupiter.api.extension.TestTemplateInvocationContextProvider;
import org.testcontainers.containers.Network;

import java.util.List;
import java.util.stream.Stream;

public class ComparisonTestExtension implements TestTemplateInvocationContextProvider,
        BeforeAllCallback, AfterAllCallback {
    public static final ExtensionContext.Namespace NAMESPACE =
            ExtensionContext.Namespace.create(ComparisonTestExtension.class);


    public static final String NETWORK = "network";

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        ExtensionContext.Store store = context.getStore(NAMESPACE);
        final Network network = Network.newNetwork();
        store.put(NETWORK, network);
        for (BeforeAllCallback comparisonContext : comparisonContexts()) {
            comparisonContext.beforeAll(context);
        }
    }

    @Override
    public void afterAll(ExtensionContext context) throws Exception {
        List<? extends AfterAllCallback> contexts = comparisonContexts();
        for (int i = contexts.size() - 1; i >= 0; i--) {
            contexts.get(i).afterAll(context);
        }
        ExtensionContext.Store store = context.getStore(NAMESPACE);
        store.get(NETWORK, Network.class).close();
    }

    @Override
    public boolean supportsTestTemplate(ExtensionContext context) {
        return true;
    }

    @Override
    public Stream<TestTemplateInvocationContext> provideTestTemplateInvocationContexts(ExtensionContext context) {
        return comparisonContexts().stream().map(
                TestTemplateInvocationContext.class::cast);
    }

    private List<ComparisonContext> comparisonContexts() {
        if ("pro".equalsIgnoreCase(System.getProperty("jooq.edition", "oss"))) {
            return List.of(new JooqPostgresContext(), new PostgrestContext(), new JooqHanaContext());
        } else {
            return List.of(new JooqPostgresContext(), new PostgrestContext());
        }
    }

}
