package com.bocsoft.sqleditor.datasource;

public interface PoolLifecycle {
    void invalidate(String dataSourceId);
}
