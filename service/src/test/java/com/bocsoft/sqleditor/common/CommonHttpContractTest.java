package com.bocsoft.sqleditor.common;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.bocsoft.sqleditor.auth.AuthContext;
import com.bocsoft.sqleditor.datasource.DataSourceController;
import com.bocsoft.sqleditor.datasource.DataSourceService;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Collections;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class CommonHttpContractTest {
    private MockMvc mvc;private DataSourceService service;
    @BeforeEach void setUp(){service=mock(DataSourceService.class);ObjectMapper mapper=new ObjectMapper().findAndRegisterModules().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);LocalValidatorFactoryBean validator=new LocalValidatorFactoryBean();validator.afterPropertiesSet();mvc=MockMvcBuilders.standaloneSetup(new DataSourceController(service)).setControllerAdvice(new GlobalExceptionHandler()).setMessageConverters(new MappingJackson2HttpMessageConverter(mapper)).setValidator(validator).addFilters(new RequestIdFilter()).build();AuthContext context=new AuthContext("u","user","User","p","Product",Instant.now().plusSeconds(60));SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(context,null,Collections.emptyList()));}
    @AfterEach void clear(){SecurityContextHolder.clearContext();}
    @Test void rejectsUnknownOwnershipFieldWithCorrelatedError()throws Exception{mvc.perform(post("/api/v1/data-sources").contentType(MediaType.APPLICATION_JSON).header("X-Request-Id","invalid").content("{\"name\":\"n\",\"engine\":\"MYSQL\",\"host\":\"h\",\"port\":3306,\"username\":\"u\",\"password\":\"p\",\"sslMode\":\"DISABLED\",\"connectTimeoutSeconds\":10,\"properties\":{},\"productId\":\"attacker\"}" )).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED")).andExpect(jsonPath("$.details.fieldErrors[0].field").value("productId")).andExpect(header().exists("X-Request-Id"));verifyNoInteractions(service);}
    @Test void returnsEmpty204ForDelete()throws Exception{doNothing().when(service).delete(any(),eq("id"),eq(1L));mvc.perform(delete("/api/v1/data-sources/id?version=1")).andExpect(status().isNoContent()).andExpect(content().string(""));}
}
