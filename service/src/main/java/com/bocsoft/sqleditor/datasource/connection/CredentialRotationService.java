package com.bocsoft.sqleditor.datasource.connection;

import com.bocsoft.sqleditor.datasource.persistence.DataSourceMapper;
import com.bocsoft.sqleditor.datasource.persistence.DataSourceRecord;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class CredentialRotationService {
    private final DataSourceMapper mapper; private final CredentialCipher cipher;
    private final TransactionTemplate transactions;
    public CredentialRotationService(DataSourceMapper mapper,CredentialCipher cipher,PlatformTransactionManager tx){this.mapper=mapper;this.cipher=cipher;this.transactions=new TransactionTemplate(tx);}
    public RotationResult rotate(String fromVersion,boolean dryRun,int batchSize){
        long remaining=mapper.countByKeyVersion(fromVersion); if(dryRun)return new RotationResult(0,remaining);
        long rotated=0;String after=null;
        while(true){List<DataSourceRecord> batch=mapper.findCredentialsByKeyVersion(fromVersion,after,batchSize);if(batch.isEmpty())break;
            final List<DataSourceRecord> current=batch;
            Integer changed=transactions.execute(status->{int count=0;for(DataSourceRecord record:current){String plaintext=cipher.decrypt(record.getPasswordCiphertext(),record.getPasswordIv(),record.getKeyVersion());EncryptedCredential encrypted=cipher.encrypt(plaintext);count+=mapper.rotateCredential(record.getId(),fromVersion,encrypted.getCiphertext(),encrypted.getIv(),encrypted.getKeyVersion());}return count;});
            rotated+=changed==null?0:changed;after=batch.get(batch.size()-1).getId();
        }
        return new RotationResult(rotated,mapper.countByKeyVersion(fromVersion));
    }
    public static final class RotationResult{private final long rotated;private final long remaining;RotationResult(long rotated,long remaining){this.rotated=rotated;this.remaining=remaining;}public long getRotated(){return rotated;}public long getRemaining(){return remaining;}}
}
