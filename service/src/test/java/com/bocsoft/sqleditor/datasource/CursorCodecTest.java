package com.bocsoft.sqleditor.datasource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bocsoft.sqleditor.common.ApiException;
import com.bocsoft.sqleditor.config.SqlEditorProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CursorCodecTest {
    private CursorCodec codec;
    @BeforeEach void setUp(){SqlEditorProperties properties=new SqlEditorProperties();properties.getCursor().setSigningKey(Base64.getEncoder().encodeToString(new byte[32]));codec=new CursorCodec(new ObjectMapper().findAndRegisterModules(),properties);}
    @Test void roundTripsAndBindsQueryShape(){Instant instant=Instant.parse("2026-08-13T01:02:03.123Z");String token=codec.encode(instant,"id-b","orders",20);CursorPosition decoded=codec.decode(token,"orders",20);assertThat(decoded.getUpdatedAt()).isEqualTo(instant);assertThat(decoded.getId()).isEqualTo("id-b");assertThatThrownBy(()->codec.decode(token,"other",20)).isInstanceOf(ApiException.class);assertThatThrownBy(()->codec.decode(token,"orders",50)).isInstanceOf(ApiException.class);}
    @Test void rejectsTamperingMalformedAndOversize(){String token=codec.encode(Instant.now(),"id","",20);assertThatThrownBy(()->codec.decode(token.substring(0,token.length()-1)+"x","",20)).isInstanceOf(ApiException.class);assertThatThrownBy(()->codec.decode("not-a-cursor","",20)).isInstanceOf(ApiException.class);assertThatThrownBy(()->codec.decode(new String(new char[3000]).replace('\0','x'),"",20)).isInstanceOf(ApiException.class);}
}
