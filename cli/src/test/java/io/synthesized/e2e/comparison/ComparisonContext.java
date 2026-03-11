package io.synthesized.e2e.comparison;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.TestTemplateInvocationContext;

public interface ComparisonContext extends
        TestTemplateInvocationContext,
        BeforeAllCallback,
        AfterAllCallback {
}
