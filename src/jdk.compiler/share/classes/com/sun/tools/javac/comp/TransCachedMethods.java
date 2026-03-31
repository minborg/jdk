/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package com.sun.tools.javac.comp;

import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symbol.DynamicVarSymbol;
import com.sun.tools.javac.code.Symbol.MethodSymbol;
import com.sun.tools.javac.code.Symbol.VarSymbol;
import com.sun.tools.javac.code.Symtab;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Type.ClassType;
import com.sun.tools.javac.code.Types;
import com.sun.tools.javac.jvm.PoolConstant.LoadableConstant;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCClassDecl;
import com.sun.tools.javac.tree.JCTree.JCExpression;
import com.sun.tools.javac.tree.JCTree.JCFieldAccess;
import com.sun.tools.javac.tree.JCTree.JCIdent;
import com.sun.tools.javac.tree.JCTree.JCMethodDecl;
import com.sun.tools.javac.tree.JCTree.JCMethodInvocation;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.tree.TreeTranslator;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.JCDiagnostic.DiagnosticPosition;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.ListBuffer;
import com.sun.tools.javac.util.Name;
import com.sun.tools.javac.util.Names;

import static com.sun.tools.javac.code.Flags.CACHED;
import static com.sun.tools.javac.code.Flags.FINAL;
import static com.sun.tools.javac.code.Flags.PARAMETER;
import static com.sun.tools.javac.code.Flags.PRIVATE;
import static com.sun.tools.javac.code.Flags.STATIC;
import static com.sun.tools.javac.code.Flags.SYNTHETIC;

public class TransCachedMethods extends TreeTranslator {

    protected static final Context.Key<TransCachedMethods> transConstantsKey = new Context.Key<>();

    public static TransCachedMethods instance(Context context) {
        TransCachedMethods instance = context.get(transConstantsKey);
        if (instance == null)
            instance = new TransCachedMethods(context);
        return instance;
    }

    private final Names names;
    private final Symtab syms;
    private TreeMaker make;
    private final Resolve rs;
    private final Types types;

    @SuppressWarnings("this-escape")
    protected TransCachedMethods(Context context) {
        context.put(transConstantsKey, this);
        names = Names.instance(context);
        syms = Symtab.instance(context);
        make = TreeMaker.instance(context);
        rs = Resolve.instance(context);
        types = Types.instance(context);
    }

    /** The currently enclosing class.
     */
    JCClassDecl currentClass;

    /** Environment for symbol lookup, set by translateTopLevelClass.
     */
    Env<AttrContext> attrEnv;

    ListBuffer<JCTree> pendingClassDefs;

    @Override
    public void visitMethodDef(JCMethodDecl tree) {
        boolean isConstantMethod = (tree.sym.flags() & CACHED) != 0;
        if (isConstantMethod) {
            tree.body = translateCachedMethod(tree);
            result = tree;
        } else {
            super.visitMethodDef(tree);
        }
    }

    @Override
    public void visitClassDef(JCClassDecl tree) {
        JCClassDecl prevClass = currentClass;
        ListBuffer<JCTree> prevPendingClassDefs = pendingClassDefs;
        try {
            currentClass = tree;
            pendingClassDefs = new ListBuffer<>();
            super.visitClassDef(tree);
            tree.defs = tree.defs.appendList(pendingClassDefs.toList());
        } finally {
            currentClass = prevClass;
            pendingClassDefs = prevPendingClassDefs;
        }
    }

    private JCTree.JCBlock translateCachedMethod(JCMethodDecl tree) {
        MethodSymbol initSym = dupToSyntheticInit(tree);
        VarSymbol cacheSym = makeCachedFieldSymbol(tree, syms.objectType);
        VarSymbol accessorSym = makeCachedMethodAccessor(tree, cacheSym, decorate(tree.name, "cached"), initSym);
        Symbol getterSym = rs.resolveInternalMethod(tree, attrEnv, accessorSym.type, names.fromString("getOrInit"), List.of(syms.objectType), List.nil());
        JCFieldAccess accessorMethodSelect = make.Select(make.Ident(accessorSym), getterSym);
        JCExpression receiver = tree.sym.isStatic() ? makeNull() : makeThis(currentClass.type, tree.sym);
        JCMethodInvocation accessorMethodApply = make.Apply(List.nil(), accessorMethodSelect, List.of(receiver)).setType(syms.objectType);
        return make.Block(0, List.of(make.Return(unboxIfNeeded(accessorMethodApply, tree.type.getReturnType()))));
    }

    private VarSymbol makeCachedFieldSymbol(JCMethodDecl tree, Type cacheType) {
        VarSymbol cacheSym = new VarSymbol((tree.sym.isStatic() ? STATIC : 0) | PRIVATE | SYNTHETIC,
                decorate(tree.name, "cache"), cacheType, currentClass.sym);
        currentClass.sym.members().enter(cacheSym);
        return cacheSym;
    }

    private MethodSymbol dupToSyntheticInit(JCMethodDecl tree) {
        // create synthetic init symbol
        // note: the constant method is non-void, non-generic, and 0-ary
        MethodSymbol initSym = new MethodSymbol(
                (tree.sym.isStatic() ? STATIC : 0) | SYNTHETIC | PRIVATE,
                decorate(tree.name, "init"),
                tree.sym.type,
                currentClass.sym);
        currentClass.sym.members().enter(initSym);
        // create synthetic method tree
        JCMethodDecl initDef = make.MethodDef(initSym, translate(tree.body));
        pendingClassDefs.add(initDef);
        return initSym;
    }

    private Name decorate(Name base, String prefix) {
        return base.append('$', names.fromString(prefix));
    }

    private VarSymbol makeCachedMethodAccessor(DiagnosticPosition pos, VarSymbol cacheSym, Name name, MethodSymbol initSym) {
        List<Type> argTypes = List.of(syms.methodHandleLookupType,
                syms.stringType,
                syms.classType,
                syms.varHandleType,
                syms.methodHandleType);

        MethodSymbol bsm = rs.resolveInternalMethod(pos, attrEnv, syms.stableAccessorType,
                names.fromString("of"), argTypes, List.nil());

        return new DynamicVarSymbol(name, currentClass.sym, bsm.asHandle(), syms.stableAccessorType,
                new LoadableConstant[] {
                        makeFieldVarHandle(pos, cacheSym),
                        initSym.asHandle()
                });
    }

    private DynamicVarSymbol makeFieldVarHandle(DiagnosticPosition pos, VarSymbol cacheSym) {
        List<Type> argTypes = List.of(syms.methodHandleLookupType,
                syms.stringType,
                new ClassType(Type.noType, List.of(syms.varHandleType), syms.classType.tsym),
                syms.classType,
                syms.classType);

        Name bsmName = cacheSym.isStatic() ?
                names.fromString("staticFieldVarHandle") :
                names.fromString("fieldVarHandle");
        MethodSymbol bsm = rs.resolveInternalMethod(pos, attrEnv, syms.constantBootstrapsType,
                bsmName, argTypes, List.nil());

        return new DynamicVarSymbol(cacheSym.name, currentClass.sym, bsm.asHandle(), syms.varHandleType,
                new LoadableConstant[] {
                        (ClassType) types.erasure(currentClass.type),
                        (ClassType) types.erasure(cacheSym.type)
                });
    }

    private JCExpression makeLit(Type type, Object value) {
        return make.Literal(type.getTag(), value)
                .setType(type.constType(value));
    }

    private JCExpression unbox(JCExpression tree, Type primitive) {
        Type box = types.boxedClass(primitive).type;
        JCExpression casted = make.TypeCast(box, tree).setType(box);
        Symbol valueSym = rs.resolveInternalMethod(tree, attrEnv, box,
                primitive.tsym.name.append(names.Value), List.nil(), List.nil());
        return make.Apply(List.nil(), make.Select(casted, valueSym), List.nil()).setType(primitive);
    }

    private JCExpression unboxIfNeeded(JCExpression tree, Type returnType) {
        return returnType.isPrimitive()
                ? unbox(tree, returnType)
                : make.TypeCast(returnType, tree).setType(returnType);
    }

    private JCExpression makeNull() {
        return makeLit(syms.botType, null);
    }

    private JCIdent makeThis(Type type, Symbol owner) {
        VarSymbol _this = new VarSymbol(PARAMETER | FINAL | SYNTHETIC,
                names._this,
                type,
                owner);
        return make.Ident(_this);
    }

    /** Translate a toplevel class and return a list consisting of
     *  the translated class and translated versions of all inner classes.
     *  @param env   The attribution environment current at the class definition.
     *               We need this for resolving some additional symbols.
     *  @param cdef  The tree representing the class definition.
     */
    public JCTree translateTopLevelClass(Env<AttrContext> env, JCTree cdef, TreeMaker make) {
        try {
            attrEnv = env;
            this.make = make;
            currentClass = null;
            return translate(cdef);
        } finally {
            // note that recursive invocations of this method fail hard
            attrEnv = null;
            this.make = null;
            currentClass = null;
        }
    }
}
