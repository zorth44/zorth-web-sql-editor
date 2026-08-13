package com.bocsoft.sqleditor.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.bocsoft.sqleditor.common.ApiException;
import java.time.Instant;
import javax.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerExceptionResolver;

class AuthContextFilterTest {
    @Test void installsResolvedPrincipalWithoutClientIdentity(){AuthContextResolver resolver=mock(AuthContextResolver.class);AuthContext context=new AuthContext("real-user","user","User","real-product","Product",Instant.now().plusSeconds(60));when(resolver.resolve("token")).thenReturn(context);HandlerExceptionResolver errors=(request,response,handler,error)->null;AuthContextFilter filter=new AuthContextFilter(resolver,errors);MockHttpServletRequest request=new MockHttpServletRequest("GET","/api/v1/session");request.addHeader("Authorization","Bearer token");request.addParameter("productId","attacker-product");MockHttpServletResponse response=new MockHttpServletResponse();javax.servlet.FilterChain chain=(req,res)->assertThat(CurrentAuth.get().getProductId()).isEqualTo("real-product");try{filter.doFilter(request,response,chain);}catch(Exception e){throw new AssertionError(e);}assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();}
    @Test void rejectsMissingBearerBeforeChain(){AuthContextResolver resolver=mock(AuthContextResolver.class);HandlerExceptionResolver errors=(request,response,handler,error)->{((HttpServletResponse)response).setStatus(((ApiException)error).getStatus().value());return null;};AuthContextFilter filter=new AuthContextFilter(resolver,errors);MockHttpServletRequest request=new MockHttpServletRequest("GET","/api/v1/session");MockHttpServletResponse response=new MockHttpServletResponse();try{filter.doFilter(request,response,new MockFilterChain());}catch(Exception e){throw new AssertionError(e);}assertThat(response.getStatus()).isEqualTo(401);verifyNoInteractions(resolver);}
}
