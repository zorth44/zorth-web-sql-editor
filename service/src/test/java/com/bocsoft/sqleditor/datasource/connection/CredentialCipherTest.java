package com.bocsoft.sqleditor.datasource.connection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CredentialCipherTest {
    private static String key(int marker){byte[] value=new byte[32];java.util.Arrays.fill(value,(byte)marker);return Base64.getEncoder().encodeToString(value);}
    @Test void encryptsWithUniqueIvsAndAuthenticatesCiphertext(){CredentialCipher cipher=new CredentialCipher("v1",Collections.singletonMap("v1",key(7)),new SecureRandom());EncryptedCredential first=cipher.encrypt("密钥-value");EncryptedCredential second=cipher.encrypt("密钥-value");assertThat(first.getCiphertext()).isNotEqualTo(second.getCiphertext());assertThat(first.getIv()).isNotEqualTo(second.getIv());assertThat(cipher.decrypt(first.getCiphertext(),first.getIv(),"v1")).isEqualTo("密钥-value");String tampered=first.getCiphertext().substring(0,first.getCiphertext().length()-2)+"AA";assertThatThrownBy(()->cipher.decrypt(tampered,first.getIv(),"v1")).isInstanceOf(IllegalStateException.class).hasMessage("Credential decryption failed");}
    @Test void rejectsBadKeysAndUnknownVersions(){assertThatThrownBy(()->new CredentialCipher("v1",Collections.singletonMap("v1","bad"),new SecureRandom())).isInstanceOf(IllegalStateException.class);CredentialCipher cipher=new CredentialCipher("v1",Collections.singletonMap("v1",key(1)),new SecureRandom());EncryptedCredential encrypted=cipher.encrypt("x");assertThatThrownBy(()->cipher.decrypt(encrypted.getCiphertext(),encrypted.getIv(),"old")).isInstanceOf(IllegalStateException.class).hasMessage("Credential key version is unavailable");}
    @Test void rejectsWrongKeyAndMalformedBase64(){Map<String,String> keys=new LinkedHashMap<String,String>();keys.put("v1",key(1));keys.put("v2",key(2));CredentialCipher cipher=new CredentialCipher("v1",keys,new SecureRandom());EncryptedCredential encrypted=cipher.encrypt("secret");assertThatThrownBy(()->cipher.decrypt(encrypted.getCiphertext(),encrypted.getIv(),"v2")).isInstanceOf(IllegalStateException.class);assertThatThrownBy(()->cipher.decrypt("not-base64",encrypted.getIv(),"v1")).isInstanceOf(IllegalStateException.class);assertThatThrownBy(()->cipher.decrypt(encrypted.getCiphertext(),"not-base64","v1")).isInstanceOf(IllegalStateException.class);}
}
