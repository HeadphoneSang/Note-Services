package org.template.interceptor;

import com.chcbcraft.dto.context.UserContext;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UserContextInterceptor 单元测试（与 auth-module 同款拦截器的 note 副本）
 */
class UserContextInterceptorTest {

    private final UserContextInterceptor interceptor = new UserContextInterceptor();

    private final Object handler = new Object();

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void validHeadersPopulateUserContext() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(UserContextInterceptor.USER_ID_HEADER, "42");
        request.addHeader(UserContextInterceptor.ACCOUNT_HEADER, "alice");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertTrue(interceptor.preHandle(request, response, handler));
        assertEquals(42, UserContext.getUserId());
        assertEquals("alice", UserContext.getAccount());
    }

    @Test
    void missingOrBadUserIdHeaderReturns401() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(request, response, handler));
        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.getStatus());

        request = new MockHttpServletRequest();
        request.addHeader(UserContextInterceptor.USER_ID_HEADER, "abc");
        response = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(request, response, handler));
        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.getStatus());
    }

    @Test
    void afterCompletionClearsContext() throws Exception {
        UserContext.set(42, "alice");
        interceptor.afterCompletion(null, null, handler, null);
        assertNull(UserContext.getUserId());
    }
}
