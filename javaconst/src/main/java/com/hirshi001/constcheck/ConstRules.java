package com.hirshi001.constcheck;

import java.util.List;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;

public class ConstRules {


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

    private static final String CONST_ANNOTATION = Const.class.getCanonicalName();
    private static final String CONST_FUN_ANNOTATION = "com.hirshi001.constcheck.ConstFun";

    static record RuleResult(boolean isError, String message) {
    }

    private static boolean hasAnnotation(Element element, String annotation) {
        if (element == null) return false;
    
        for (AnnotationMirror mirror : element.getAnnotationMirrors()) {
            if (mirror.getAnnotationType().toString()
                    .equals(annotation)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isConstVariable(VariableElement element) {
        return hasAnnotation(element, CONST_ANNOTATION);
    }

    public static boolean isConstMethod(ExecutableElement element) {
        return hasAnnotation(element, CONST_FUN_ANNOTATION);
    }

    public static boolean isConstReturnType(ExecutableElement element) {
        return hasAnnotation(element, CONST_ANNOTATION);
    }


    public static RuleResult check(CallInfo callInfo) {
        boolean isMethodConst = isConstMethod(callInfo.method);

        // using a variable to call the method
        if (callInfo.receiverElement instanceof VariableElement receiverVariable) {
            boolean isReceiverConst = isConstVariable(receiverVariable);
            if (isReceiverConst && !isMethodConst) {
                return new RuleResult(true, "Cannot call non-@Const method on a @Const variable");
            }
        }

        // check that @Const values are not passed to non-@Const parameters
        List<? extends VariableElement> params = callInfo.method.getParameters();

        List<CallArgument> args = callInfo.arguments();

        for (int i = 0; i < params.size() && i < args.size(); i++) {

            VariableElement param = params.get(i);
            CallArgument arg = args.get(i);


            boolean isParamConst = isConstVariable(param);
            boolean isArgConst = arg.isConst();

            if (!isParamConst && isArgConst) {
                return new RuleResult(
                    true,
                    "@Const value passed to non-@Const parameter"
                );
            }
        }
        
        return new RuleResult(false, null);
    }
    
}
