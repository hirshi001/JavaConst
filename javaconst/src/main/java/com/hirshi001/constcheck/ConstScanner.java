package com.hirshi001.constcheck;

import com.hirshi001.constcheck.ConstRules.RuleResult;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.AssignmentTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;

import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import java.util.ArrayList;
import java.util.List;

public class ConstScanner extends TreePathScanner<Void, Void> {
    private final Trees trees;
    private final Types types;
    private final CompilationUnitTree compilationUnit;

    public ConstScanner(Trees trees, Types types, CompilationUnitTree compilationUnit) {
        this.trees = trees;
        this.types = types;
        this.compilationUnit = compilationUnit;
    }

    private ExecutableElement getChainTerminalMethod(MethodInvocationTree invocation) {
        List<CallChainFlattener.CallStep> steps = CallChainFlattener.flatten(invocation, trees, types);
        if (steps.isEmpty()) {
            return null;
        }
        return steps.get(steps.size() - 1).method();
    }

    private boolean endsWithConstMethod(MethodInvocationTree invocation) {
        ExecutableElement terminalMethod = getChainTerminalMethod(invocation);
        return terminalMethod != null && ConstRules.isConstMethod(terminalMethod);
    }

    @Override
    public Void visitMethodInvocation(MethodInvocationTree node, Void unused) {
        TreePath currentPath = getCurrentPath();
        ExpressionTree methodSelect = node.getMethodSelect();
        ExecutableElement methodElement = getChainTerminalMethod(node);
        if (methodElement == null) {
            Element methodResolved = trees.getElement(new TreePath(currentPath, methodSelect));
            if (methodResolved instanceof ExecutableElement executableElement) {
                methodElement = executableElement;
            }
        }
        if (methodElement == null) {
            return super.visitMethodInvocation(node, unused);
        }

        Element receiverElement = null;
        TypeMirror receiverType = null;
        boolean isImplicitThis = false;
        boolean isStatic = methodElement.getModifiers().contains(javax.lang.model.element.Modifier.STATIC);

        if (methodSelect instanceof MemberSelectTree memberSelect) {
            ExpressionTree receiverExpression = memberSelect.getExpression();
            TreePath receiverPath = new TreePath(currentPath, receiverExpression);
            receiverElement = trees.getElement(receiverPath);
            receiverType = trees.getTypeMirror(receiverPath);
        } else if (methodSelect instanceof IdentifierTree) {
            if (isStatic) {
                // Unqualified static calls (including static imports) have no instance receiver.
                receiverElement = null;
                receiverType = null;
            } else {
                receiverElement = trees.getScope(currentPath).getEnclosingClass();
                receiverType = receiverElement == null ? null : receiverElement.asType();
                isImplicitThis = true;
            }
        } else {
            return super.visitMethodInvocation(node, unused);
        }

        List<ConstRules.CallArgument> arguments = new ArrayList<>();
        for (ExpressionTree argument : node.getArguments()) {
            Element argElement = trees.getElement(new TreePath(currentPath, argument));

            boolean isConstArg = false;
            if (argElement instanceof VariableElement varElement) {
                isConstArg = ConstRules.isConstVariable(varElement);
            } else if (argument instanceof MethodInvocationTree methodInvocation) {
                isConstArg = endsWithConstMethod(methodInvocation);
            }

            arguments.add(new ConstRules.CallArgument(isConstArg));
        }

        ConstRules.CallInfo callInfo = new ConstRules.CallInfo(
                methodElement,
                arguments,
                receiverType,
                receiverElement,
                isStatic,
                isImplicitThis
        );

        RuleResult result = ConstRules.check(callInfo);
        if (result.isError()) {
            trees.printMessage(
                    Diagnostic.Kind.ERROR,
                    result.message(),
                    node,
                    compilationUnit
            );
        }

        return super.visitMethodInvocation(node, unused);
    }

    @Override
    public Void visitAssignment(AssignmentTree node, Void unused) {
        TreePath currentPath = getCurrentPath();

        TreePath lhsPath = new TreePath(currentPath, node.getVariable());
        Element lhsElement = trees.getElement(lhsPath);

        TreePath rhsPath = new TreePath(currentPath, node.getExpression());
        Element rhsElement = trees.getElement(rhsPath);

        if (lhsElement instanceof VariableElement lhsVariable
                && rhsElement instanceof VariableElement rhsVariable
                && ConstRules.isConstVariable(rhsVariable)
                && !ConstRules.isConstVariable(lhsVariable)) {
            trees.printMessage(
                    Diagnostic.Kind.ERROR,
                    "Cannot assign @Const value to non-@Const variable",
                    node,
                    compilationUnit
            );
        }

        return super.visitAssignment(node, unused);
    }
}
