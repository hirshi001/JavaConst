package com.hirshi001.constcheck;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.util.Types;

import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.util.Trees;

public class CallChainFlattener {

    static public record CallStep(
        ExecutableElement method
    ) {}

    
    private CallChainFlattener() {}

    public static List<CallStep> flatten(MethodInvocationTree expr, Trees trees, Types types) {
        Objects.requireNonNull(expr, "expr");
        Objects.requireNonNull(trees, "trees");
        Objects.requireNonNull(types, "types");

        List<CallStep> result = new ArrayList<>();
        flattenInto(expr, result);
        return result;
    }

    private static void flattenInto(ExpressionTree expression, List<CallStep> out) {
        if (!(expression instanceof MethodInvocationTree methodInvocation)) {
            return;
        }

        ExpressionTree receiver = getReceiverExpression(methodInvocation);
        if (receiver instanceof MethodInvocationTree receiverInvocation) {
            flattenInto(receiverInvocation, out);
        }

        ExecutableElement method = resolveMethodElement(methodInvocation);
        if (method != null) {
            out.add(new CallStep(method));
        }
    }

    private static ExpressionTree getReceiverExpression(MethodInvocationTree invocation) {
        ExpressionTree methodSelect = invocation.getMethodSelect();
        if (methodSelect instanceof MemberSelectTree memberSelect) {
            return memberSelect.getExpression();
        }
        if (methodSelect instanceof IdentifierTree) {
            // Implicit receiver call (this/static import), no explicit receiver expression in AST.
            return null;
        }
        return null;
    }

    private static ExecutableElement resolveMethodElement(MethodInvocationTree invocation) {
        Object methodSelect = invocation.getMethodSelect();
        Class<?> current = methodSelect.getClass();
        while (current != null) {
            try {
                var symField = current.getDeclaredField("sym");
                symField.setAccessible(true);
                Object sym = symField.get(methodSelect);
                if (sym instanceof ExecutableElement executableElement) {
                    return executableElement;
                }
                return null;
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            } catch (IllegalAccessException ignored) {
                return null;
            }
        }
        return null;
    }
}
