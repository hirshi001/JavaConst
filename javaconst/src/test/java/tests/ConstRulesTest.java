package tests;

import com.hirshi001.constcheck.ConstRules;
import com.hirshi001.constcheck.ConstRules.RuleResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ConstRulesTest {

    @Test
    public void checkConstFunMethodAllowsNonStaticReferenceReturn() {
        RuleResult result = ConstRules.checkConstFunMethod(false, false);
        assertFalse(result.isError());
    }

    @Test
    public void checkConstFunMethodRejectsStaticMethods() {
        RuleResult result = ConstRules.checkConstFunMethod(true, false);
        assertTrue(result.isError());
        assertEquals("@ConstFun methods must be non-static", result.message());
    }

    @Test
    public void checkConstFunMethodRejectsPrimitiveReturnType() {
        RuleResult result = ConstRules.checkConstFunMethod(false, true);
        assertTrue(result.isError());
        assertEquals("@ConstFun methods cannot have a primitive return type", result.message());
    }

    @Test
    public void checkConstFunMethodReportsStaticBeforePrimitiveReturn() {
        RuleResult result = ConstRules.checkConstFunMethod(true, true);
        assertTrue(result.isError());
        assertEquals("@ConstFun methods must be non-static", result.message());
    }

    @Test
    public void checkConstFunFieldAssignmentRejectsClassMemberInsideConstFun() {
        RuleResult result = ConstRules.checkConstFunFieldAssignment(true, true, false);
        assertTrue(result.isError());
        assertEquals("Cannot assign to a field inside a @ConstFun method", result.message());
    }

    @Test
    public void checkConstFunFieldAssignmentAllowsClassMemberOutsideConstFun() {
        RuleResult result = ConstRules.checkConstFunFieldAssignment(true, false, false);
        assertFalse(result.isError());
    }

    @Test
    public void checkConstFunFieldAssignmentAllowsLocalInsideConstFun() {
        RuleResult result = ConstRules.checkConstFunFieldAssignment(false, true, false);
        assertFalse(result.isError());
    }

    @Test
    public void checkConstFunFieldAssignmentAllowsMutFieldInsideConstFun() {
        RuleResult result = ConstRules.checkConstFunFieldAssignment(true, true, true);
        assertFalse(result.isError());
    }

    @Test
    public void checkMutFieldRejectsStaticFields() {
        RuleResult result = ConstRules.checkMutField(true);
        assertTrue(result.isError());
        assertEquals("@Mut cannot be applied to static fields", result.message());
    }

    @Test
    public void checkMutFieldAllowsInstanceFields() {
        RuleResult result = ConstRules.checkMutField(false);
        assertFalse(result.isError());
    }

    @Test
    public void checkConstFunInternalCallRejectsNonConstFunThisCall() {
        RuleResult result = ConstRules.checkConstFunInternalCall(true, true, false);
        assertTrue(result.isError());
        assertEquals(
                "Cannot call non-@ConstFun instance method on this inside a @ConstFun method",
                result.message()
        );
    }

    @Test
    public void checkConstFunInternalCallAllowsConstFunThisCall() {
        RuleResult result = ConstRules.checkConstFunInternalCall(true, true, true);
        assertFalse(result.isError());
    }

    @Test
    public void checkConstFunInternalCallAllowsOutsideConstFun() {
        RuleResult result = ConstRules.checkConstFunInternalCall(false, true, false);
        assertFalse(result.isError());
    }
}
