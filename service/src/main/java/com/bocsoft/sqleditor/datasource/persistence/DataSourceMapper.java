package com.bocsoft.sqleditor.datasource.persistence;

import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface DataSourceMapper {
    int insert(DataSourceRecord record);
    DataSourceRecord findVisible(@Param("id") String id, @Param("productId") String productId);
    List<DataSourceRecord> list(@Param("productId") String productId,
                                @Param("keyword") String keyword,
                                @Param("cursorUpdatedAt") Instant cursorUpdatedAt,
                                @Param("cursorId") String cursorId,
                                @Param("limit") int limit);
    int updateCurrent(DataSourceRecord record);
    int deleteCurrent(@Param("id") String id, @Param("productId") String productId,
                      @Param("version") long version);
    int updateLastTest(@Param("id") String id, @Param("productId") String productId,
                       @Param("status") String status, @Param("testedAt") Instant testedAt,
                       @Param("message") String message);
    List<DataSourceRecord> findCredentialsByKeyVersion(@Param("keyVersion") String keyVersion,
                                                        @Param("afterId") String afterId,
                                                        @Param("limit") int limit);
    int rotateCredential(@Param("id") String id, @Param("oldVersion") String oldVersion,
                         @Param("ciphertext") String ciphertext, @Param("iv") String iv,
                         @Param("newVersion") String newVersion);
    long countByKeyVersion(@Param("keyVersion") String keyVersion);
}
