package com.bocsoft.sqleditor.datasource.connection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.bocsoft.sqleditor.datasource.persistence.DataSourceMapper;
import com.bocsoft.sqleditor.datasource.persistence.DataSourceRecord;
import java.security.SecureRandom;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

class CredentialRotationServiceTest {
    private static String key(int value){byte[] bytes=new byte[32];Arrays.fill(bytes,(byte)value);return Base64.getEncoder().encodeToString(bytes);}
    @Test void supportsDryRunAndIdempotentBatchedRotation(){DataSourceMapper mapper=mock(DataSourceMapper.class);PlatformTransactionManager tx=mock(PlatformTransactionManager.class);when(tx.getTransaction(any())).thenReturn(new SimpleTransactionStatus());Map<String,String> keys=new LinkedHashMap<String,String>();keys.put("v1",key(1));keys.put("v2",key(2));CredentialCipher oldCipher=new CredentialCipher("v1",keys,new SecureRandom());CredentialCipher currentCipher=new CredentialCipher("v2",keys,new SecureRandom());EncryptedCredential old=oldCipher.encrypt("database-secret");DataSourceRecord row=new DataSourceRecord();row.setId("id-1");row.setPasswordCiphertext(old.getCiphertext());row.setPasswordIv(old.getIv());row.setKeyVersion("v1");when(mapper.countByKeyVersion("v1")).thenReturn(1L,1L,0L);when(mapper.findCredentialsByKeyVersion("v1",null,10)).thenReturn(Collections.singletonList(row));when(mapper.findCredentialsByKeyVersion("v1","id-1",10)).thenReturn(Collections.emptyList());when(mapper.rotateCredential(eq("id-1"),eq("v1"),anyString(),anyString(),eq("v2"))).thenReturn(1);CredentialRotationService service=new CredentialRotationService(mapper,currentCipher,tx);CredentialRotationService.RotationResult dry=service.rotate("v1",true,10);assertThat(dry.getRotated()).isZero();assertThat(dry.getRemaining()).isEqualTo(1);CredentialRotationService.RotationResult result=service.rotate("v1",false,10);assertThat(result.getRotated()).isEqualTo(1);assertThat(result.getRemaining()).isZero();verify(mapper).rotateCredential(eq("id-1"),eq("v1"),argThat(value->!value.contains("database-secret")),anyString(),eq("v2"));}
}
