package com.hirshi001.constcheck;

import com.hirshi001.constcheck.ConstRules.RuleResult;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.AssignmentTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
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

    public boolean isDirectlyInsideConstFunMethod(TreePath path) {
        for (TreePath current = path; current != null; current = current.getParentPath()) {
            if (current.getLeaf() instanceof MethodTree) {
                Element element = trees.getElement(current);
                return element instanceof ExecutableElement executableElement
                        && ConstRules.isConstMethod(executableElement);
            }
        }
        return false;
    }

    private ExecutableElement getChainTerminalMethod(MethodInvocationTree invocation) {
        List<CallChainFlattener.CallStep> steps = CallChainFlattener.flatten(invocation, trees, types);
        if (steps.isEmpty()) {
            return null;
        }
        return steps.get(steps.size() - 1).method();
    }

    private boolean endsWithConstReturnType(MethodInvocationTree invocation) {
        ExecutableElement terminalMethod = getChainTerminalMethod(invocation);
        return terminalMethod != null && ConstRules.isConstValueFromMethodReturn(terminalMethod);
    }

    private boolean isConstExpression(ExpressionTree expression, TreePath currentPath) {
        if (expression instanceof MethodInvocationTree methodInvocation) {
            return endsWithConstReturnType(methodInvocation);
        }

        Element expressionElement = trees.getElement(new TreePath(currentPath, expression));
        if (expressionElement instanceof VariableElement variableElement) {
            return ConstRules.isConstValueFromVariable(variableElement);
        }

        return false;
    }

    private boolean isFieldDeclaredInTypeHierarchy(VariableElement field, Element enclosingClass) {
        Element fieldOwner = field.getEnclosingElement();
        if (!(fieldOwner instanceof TypeElement)) {
            return false;
        }
        return types.isAssignable(enclosingClass.asType(), fieldOwner.asType());
    }

    private boolean isThisOrSuperFieldAccess(ExpressionTree lhs, TreePath assignmentPath) {
        if (lhs instanceof IdentifierTree) {
            return true;
        }

        if (lhs instanceof MemberSelectTree memberSelect) {
            ExpressionTree receiver = memberSelect.getExpression();
            if (receiver instanceof IdentifierTree identifier) {
                String name = identifier.getName().toString();
                return "this".equals(name) || "super".equals(name);
            }

            Element receiverElement = trees.getElement(new TreePath(assignmentPath, receiver));
            Element enclosingClass = trees.getScope(assignmentPath).getEnclosingClass();
            return enclosingClass != null && enclosingClass.equals(receiverElement);
        }

        return false;
    }

    private boolean isClassMemberAssignment(ExpressionTree lhs, TreePath assignmentPath) {
        TreePath lhsPath = new TreePath(assignmentPath, lhs);
        Element lhsElement = trees.getElement(lhsPath);
        if (!(lhsElement instanceof VariableElement field) || field.getKind() != ElementKind.FIELD) {
            return false;
        }

        Element enclosingClass = trees.getScope(assignmentPath).getEnclosingClass();
        if (enclosingClass == null) {
            return false;
        }

        return isFieldDeclaredInTypeHierarchy(field, enclosingClass)
                && isThisOrSuperFieldAccess(lhs, assignmentPath);
    }

    private boolean isSameClassThisInstanceCall(
            ExecutableElement method,
            boolean isStatic,
            boolean isImplicitThis,
            ExpressionTree methodSelect,
            TreePath currentPath
    ) {
        if (isStatic) {
            return false;
        }

        Element enclosingClass = trees.getScope(currentPath).getEnclosingClass();
        if (enclosingClass == null) {
            return false;
        }

        boolean isThisCall = isImplicitThis;
        if (methodSelect instanceof MemberSelectTree memberSelect) {
            ExpressionTree receiver = memberSelect.getExpression();
            if (receiver instanceof IdentifierTree identifier) {
                isThisCall = "this".equals(identifier.getName().toString());
            } else {
                return false;
            }
        } else if (!(methodSelect instanceof IdentifierTree)) {
            return false;
        }

        if (!isThisCall) {
            return false;
        }

        Element methodOwner = method.getEnclosingElement();
        if (!(methodOwner instanceof TypeElement methodDeclaringType)) {
            return false;
        }

        return types.isAssignable(enclosingClass.asType(), methodDeclaringType.asType());
    }

    @Override
    public Void visitMethod(MethodTree node, Void unused) {
        Element methodElement = trees.getElement(getCurrentPath());
        if (methodElement instanceof ExecutableElement executableElement
                && ConstRules.isConstMethod(executableElement)) {
            boolean isStatic = executableElement.getModifiers().contains(javax.lang.model.element.Modifier.STATIC);
            boolean hasPrimitiveReturnType = executableElement.getReturnType().getKind().isPrimitive();
            RuleResult result = ConstRules.checkConstFunMethod(isStatic, hasPrimitiveReturnType);
            if (result.isError()) {
                trees.printMessage(
                        Diagnostic.Kind.ERROR,
                        result.message(),
                        node,
                        compilationUnit
                );
            }
        }
        return super.visitMethod(node, unused);
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
            boolean isConstArg = isConstExpression(argument, currentPath);
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

        RuleResult internalCallResult = ConstRules.checkConstFunInternalCall(
                isDirectlyInsideConstFunMethod(currentPath),
                isSameClassThisInstanceCall(methodElement, isStatic, isImplicitThis, methodSelect, currentPath),
                ConstRules.isConstMethod(methodElement)
        );
        if (internalCallResult.isError()) {
            trees.printMessage(
                    Diagnostic.Kind.ERROR,
                    internalCallResult.message(),
                    node,
                    compilationUnit
            );
        }

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
        ExpressionTree lhs = node.getVariable();
        TreePath lhsPath = new TreePath(currentPath, lhs);
        Element lhsElement = trees.getElement(lhsPath);

        boolean isMutField = lhsElement instanceof VariableElement lhsField
                && ConstRules.isMutVariable(lhsField);

        RuleResult constFunFieldResult = ConstRules.checkConstFunFieldAssignment(
                isClassMemberAssignment(lhs, currentPath),
                isDirectlyInsideConstFunMethod(currentPath),
                isMutField
        );
        if (constFunFieldResult.isError()) {
            trees.printMessage(
                    Diagnostic.Kind.ERROR,
                    constFunFieldResult.message(),
                    node,
                    compilationUnit
            );
        }

        if (lhsElement instanceof VariableElement lhsVariable) {
            RuleResult result = ConstRules.checkAssignment(
                    ConstRules.isConstVariable(lhsVariable),
                    isConstExpression(node.getExpression(), currentPath)
            );
            if (result.isError()) {
                trees.printMessage(
                        Diagnostic.Kind.ERROR,
                        result.message(),
                        node,
                        compilationUnit
                );
            }
        }

        return super.visitAssignment(node, unused);
    }

    @Override
    public Void visitVariable(VariableTree node, Void unused) {
        TreePath currentPath = getCurrentPath();
        Element variableElement = trees.getElement(currentPath);

        if (variableElement instanceof VariableElement variable) {
            if (variable.getKind() == ElementKind.FIELD && ConstRules.isMutVariable(variable)) {
                boolean isStatic = variable.getModifiers().contains(javax.lang.model.element.Modifier.STATIC);
                RuleResult mutResult = ConstRules.checkMutField(isStatic);
                if (mutResult.isError()) {
                    trees.printMessage(
                            Diagnostic.Kind.ERROR,
                            mutResult.message(),
                            node,
                            compilationUnit
                    );
                }
            }

            ExpressionTree initializer = node.getInitializer();
            if (initializer != null) {
                RuleResult result = ConstRules.checkAssignment(
                        ConstRules.isConstVariable(variable),
                        isConstExpression(initializer, currentPath)
                );
                if (result.isError()) {
                    trees.printMessage(
                            Diagnostic.Kind.ERROR,
                            result.message(),
                            node,
                            compilationUnit
                    );
                }
            }
        }

        return super.visitVariable(node, unused);
    }
}
