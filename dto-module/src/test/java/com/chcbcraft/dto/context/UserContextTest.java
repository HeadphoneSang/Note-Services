package com.chcbcraft.dto.context;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * UserContext 单元测试
 */
class UserContextTest {

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void setThenGetReturnsSameValues() {
        UserContext.set(42, "alice");
        assertEquals(42, UserContext.getUserId());
        assertEquals("alice", UserContext.getAccount());
    }

    @Test
    void clearRemovesAllValues() {
        UserContext.set(42, "alice");
        UserContext.clear();
        assertNull(UserContext.getUserId());
        assertNull(UserContext.getAccount());
    }

    @Test
    void defaultStateIsEmpty() {
        assertNull(UserContext.getUserId());
        assertNull(UserContext.getAccount());
    }
}
