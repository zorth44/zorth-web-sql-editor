package com.bocsoft.sqleditor.optimization;

import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import static org.junit.jupiter.api.Assertions.*;

class OptimizationConfigurationTest {
    OptimizationProperties ready(){
        OptimizationProperties p=new OptimizationProperties();p.setEnabled(true);p.setIntegrationReady(true);p.getAllowedUsers().add("user");p.getAllowedProducts().add("product");
        String key=Base64.getEncoder().encodeToString(new byte[32]);p.getCredentialKeys().put("v1",key);p.setCursorKey(key);return p;
    }
    @Test void disabledDefaultNeedsNoCredentialAndEnabledRequiresExplicitIntegration(){
        assertDoesNotThrow(()->new OptimizationConfiguration(new OptimizationProperties(),new MockEnvironment()).validate());
        OptimizationProperties p=ready();assertDoesNotThrow(()->new OptimizationConfiguration(p,new MockEnvironment()).validate());
        p.setIntegrationReady(false);assertThrows(IllegalStateException.class,()->new OptimizationConfiguration(p,new MockEnvironment()).validate());
        p.setIntegrationReady(true);p.getAllowedUsers().clear();assertThrows(IllegalStateException.class,()->new OptimizationConfiguration(p,new MockEnvironment()).validate());
    }
    @Test void keysBudgetsAndDevelopmentIdentityFailClosed(){
        OptimizationProperties p=ready();p.setCursorKey("");assertThrows(IllegalStateException.class,()->new OptimizationConfiguration(p,new MockEnvironment()).validate());
        OptimizationProperties header=ready();header.setDevelopmentUserHeader(true);
        assertThrows(IllegalStateException.class,()->new OptimizationConfiguration(header,new MockEnvironment()).validate());
        MockEnvironment dev=new MockEnvironment();dev.setActiveProfiles("test");assertDoesNotThrow(()->new OptimizationConfiguration(header,dev).validate());
        OptimizationProperties budget=ready();budget.setAttemptBudgetSeconds(100);assertThrows(IllegalStateException.class,()->new OptimizationConfiguration(budget,dev).validate());
    }
}
