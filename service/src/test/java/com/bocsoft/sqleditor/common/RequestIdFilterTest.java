package com.bocsoft.sqleditor.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestIdFilterTest {
    @Test void reusesValidAndReplacesInvalidIds()throws Exception{RequestIdFilter filter=new RequestIdFilter();String valid=UUID.randomUUID().toString();MockHttpServletRequest request=new MockHttpServletRequest();request.addHeader(RequestIds.HEADER,valid);MockHttpServletResponse response=new MockHttpServletResponse();filter.doFilter(request,response,new MockFilterChain());assertThat(response.getHeader(RequestIds.HEADER)).isEqualTo(valid);MockHttpServletRequest invalid=new MockHttpServletRequest();invalid.addHeader(RequestIds.HEADER,"not-valid");MockHttpServletResponse replacement=new MockHttpServletResponse();filter.doFilter(invalid,replacement,new MockFilterChain());assertThat(UUID.fromString(replacement.getHeader(RequestIds.HEADER))).isNotNull();}
}
