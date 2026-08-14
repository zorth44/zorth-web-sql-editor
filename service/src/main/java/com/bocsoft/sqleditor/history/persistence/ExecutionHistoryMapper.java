package com.bocsoft.sqleditor.history.persistence;

import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface ExecutionHistoryMapper {
    int insert(ExecutionHistoryRecord record);
    int exists(@Param("id") String id);
    int finish(ExecutionHistoryRecord record);
    ExecutionHistoryRecord findOwned(@Param("id") String id,@Param("userId") String userId);
    List<ExecutionHistoryRecord> listOwned(@Param("userId") String userId,@Param("keyword") String keyword,
        @Param("dataSourceId") String dataSourceId,@Param("database") String database,
        @Param("status") String status,@Param("statementType") String statementType,
        @Param("cursorStartedAt") Instant cursorStartedAt,@Param("cursorId") String cursorId,@Param("limit") int limit);
    int markStale(@Param("before") Instant before,@Param("finishedAt") Instant finishedAt);
    int deleteFinishedBefore(@Param("before") Instant before);
}
