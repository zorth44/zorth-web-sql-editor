package com.bocsoft.sqleditor.script.persistence;

import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface ScriptMapper {
    int insert(ScriptRecord record);
    ScriptRecord findOwned(@Param("id") String id, @Param("userId") String userId);
    int countOwned(@Param("userId") String userId);
    List<ScriptRecord> listOwned(@Param("userId") String userId, @Param("keyword") String keyword,
        @Param("dataSourceId") String dataSourceId, @Param("database") String database,
        @Param("cursorUpdatedAt") Instant cursorUpdatedAt, @Param("cursorId") String cursorId,
        @Param("limit") int limit);
    int updateCurrent(ScriptRecord record);
    int deleteCurrent(@Param("id") String id, @Param("userId") String userId, @Param("version") long version);
}
