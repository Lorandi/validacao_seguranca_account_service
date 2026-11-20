package br.edu.ifrs.account.enums;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AccountStatusEnumTest {

    @Test
    void activeShouldHaveActiveLabel() {
        assertEquals("Active", AccountStatusEnum.ACTIVE.getLabel());
    }

    @Test
    void blockedShouldHaveBlockedLabel() {
        assertEquals("Blocked", AccountStatusEnum.BLOCKED.getLabel());
    }

    @Test
    void activeShouldBeActiveAndNotBlocked() {
        assertTrue(AccountStatusEnum.ACTIVE.isActive());
        assertFalse(AccountStatusEnum.ACTIVE.isBlocked());
    }

    @Test
    void blockedShouldBeBlockedAndNotActive() {
        assertTrue(AccountStatusEnum.BLOCKED.isBlocked());
        assertFalse(AccountStatusEnum.BLOCKED.isActive());
    }

    @Test
    void toggleShouldChangeActiveToBlocked() {
        assertEquals(AccountStatusEnum.BLOCKED, AccountStatusEnum.ACTIVE.toggle());
    }

    @Test
    void toggleShouldChangeBlockedToActive() {
        assertEquals(AccountStatusEnum.ACTIVE, AccountStatusEnum.BLOCKED.toggle());
    }

    @Test
    void toStringShouldReturnTheLabelOfEachStatus() {
        assertEquals("Active", AccountStatusEnum.ACTIVE.toString());
        assertEquals("Blocked", AccountStatusEnum.BLOCKED.toString());
    }
}
