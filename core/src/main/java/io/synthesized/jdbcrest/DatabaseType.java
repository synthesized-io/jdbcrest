package io.synthesized.jdbcrest;

import java.util.function.Supplier;

public enum DatabaseType {

    POSTGRESQL(PostgreQueryTranspiler::new);

    private final Supplier<QueryTranspiler> supplierFactory;

    DatabaseType(Supplier<QueryTranspiler> supplierFactory) {
        this.supplierFactory = supplierFactory;
    }


    public QueryTranspiler getTranspiler() {
        return supplierFactory.get();
    }
}
