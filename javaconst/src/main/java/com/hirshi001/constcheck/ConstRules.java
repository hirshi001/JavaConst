package com.hirshi001.constcheck;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import java.util.List;

public class ConstRules {

    private static final String CONST_ANNOTATION = Const.class.getCanonicalName();
    private static final String CONST_FUN_ANNOTATION = ConstFun.class.getCanonicalName();
    private static final String MUT_ANNOTATION = Mut.class.getCanonicalName();

    public record CallArgument(
            boolean isConst
    ) {}

    public record CallInfo(
            ExecutableElement method,
            List<CallArgument> arguments,
            TypeMirror receiverType,
            Element receiverElement,
            boolean isStatic,
            boolean isImplicitThis
    ) {}

    private static final String NON_CONST_METHOD_ON_CONST_RECEIVER =
            "Cannot call non-@Const method on a @Const variable";
    private static final String CONST_TO_NON_CONST_PARAM =
            "@Const value passed to non-@Const parameter";
    private static final String CONST_TO_NON_CONST_ASSIGNMENT =
            "Cannot assign @Const value to non-@Const variable";
    private static final String CONST_FUN_STATIC_METHOD =
            "@ConstFun methods must be non-static";
    private static final String CONST_FUN_PRIMITIVE_RETURN =
            "@ConstFun methods cannot have a primitive return type";
    private static final String CONST_FUN_FIELD_ASSIGNMENT =
            "Cannot assign to a field inside a @ConstFun method";
    private static final String MUT_STATIC_FIELD =
            "@Mut cannot be applied to static fields";
    private static final String CONST_FUN_NON_CONST_METHOD_CALL =
            "Cannot call non-@ConstFun instance method on this inside a @ConstFun method";

    public static record RuleResult(boolean isError, String message) {
    }

    private static boolean hasAnnotation(Element element, String annotation) {
        if (element == null) {
            return false;
        }

        for (AnnotationMirror mirror : element.getAnnotationMirrors()) {
            if (mirror.getAnnotationType().toString().equals(annotation)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isConstVariable(VariableElement element) {
        return hasAnnotation(element, CONST_ANNOTATION);
    }

    public static boolean isConstValueFromVariable(VariableElement element) {
        return isConstVariable(element);
    }

    public static boolean isConstMethod(ExecutableElement element) {
        return hasAnnotation(element, CONST_FUN_ANNOTATION);
    }

    public static boolean isConstValueFromMethodReturn(ExecutableElement element) {
        return hasAnnotation(element, CONST_ANNOTATION);
    }

    public static boolean isMutVariable(VariableElement element) {
        return hasAnnotation(element, MUT_ANNOTATION);
    }

    public static RuleResult checkAssignment(boolean isLhsConst, boolean isRhsConst) {
        if (isRhsConst && !isLhsConst) {
            return new RuleResult(true, CONST_TO_NON_CONST_ASSIGNMENT);
        }
        return new RuleResult(false, null);
    }

    public static RuleResult checkConstFunMethod(boolean isStatic, boolean hasPrimitiveReturnType) {
        if (isStatic) {
            return new RuleResult(true, CONST_FUN_STATIC_METHOD);
        }
        if (hasPrimitiveReturnType) {
            return new RuleResult(true, CONST_FUN_PRIMITIVE_RETURN);
        }
        return new RuleResult(false, null);
    }

    public static RuleResult checkConstFunFieldAssignment(
            boolean isClassMemberAssignment,
            boolean isInsideConstFunMethod,
            boolean isMutField
    ) {
        if (isClassMemberAssignment && isInsideConstFunMethod && !isMutField) {
            return new RuleResult(true, CONST_FUN_FIELD_ASSIGNMENT);
        }
        return new RuleResult(false, null);
    }

    public static RuleResult checkMutField(boolean isStatic) {
        if (isStatic) {
            return new RuleResult(true, MUT_STATIC_FIELD);
        }
        return new RuleResult(false, null);
    }

    public static RuleResult checkConstFunInternalCall(
            boolean isInsideConstFunMethod,
            boolean isSameClassThisInstanceCall,
            boolean isInvokedMethodConstFun
    ) {
        if (isInsideConstFunMethod && isSameClassThisInstanceCall && !isInvokedMethodConstFun) {
            return new RuleResult(true, CONST_FUN_NON_CONST_METHOD_CALL);
        }
        return new RuleResult(false, null);
    }

    private static boolean isReceiverConst(CallInfo callInfo) {
        if (callInfo.isImplicitThis()) {
            return false;
        }
        if (callInfo.receiverElement() instanceof VariableElement receiverVariable) {
            return isConstVariable(receiverVariable);
        }
        return false;
    }

    public static RuleResult check(CallInfo callInfo) {
        boolean isMethodConst = isConstMethod(callInfo.method());
        if (isReceiverConst(callInfo) && !isMethodConst) {
            return new RuleResult(true, NON_CONST_METHOD_ON_CONST_RECEIVER);
        }

        List<? extends VariableElement> params = callInfo.method().getParameters();
        for (int i = 0; i < callInfo.arguments().size(); i++) {
            CallArgument arg = callInfo.arguments().get(i);
            boolean isParamConst = i < params.size() && isConstVariable(params.get(i));
            if (!isParamConst && arg.isConst()) {
                return new RuleResult(true, CONST_TO_NON_CONST_PARAM);
            }
        }

        return new RuleResult(false, null);
    }
}
