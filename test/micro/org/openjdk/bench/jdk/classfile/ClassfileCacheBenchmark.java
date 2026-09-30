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

package org.openjdk.bench.jdk.classfile;

import java.io.IOException;
import java.io.InputStream;
import java.lang.classfile.Attribute;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeElement;
import java.lang.classfile.CodeModel;
import java.lang.classfile.FieldModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.attribute.ExceptionsAttribute;
import java.lang.classfile.attribute.InnerClassesAttribute;
import java.lang.classfile.attribute.LineNumberTableAttribute;
import java.lang.classfile.attribute.LocalVariableTableAttribute;
import java.lang.classfile.attribute.LocalVariableTypeTableAttribute;
import java.lang.classfile.attribute.MethodParametersAttribute;
import java.lang.classfile.attribute.RuntimeInvisibleAnnotationsAttribute;
import java.lang.classfile.attribute.RuntimeVisibleAnnotationsAttribute;
import java.lang.classfile.attribute.StackMapTableAttribute;
import java.lang.classfile.instruction.FieldInstruction;
import java.lang.classfile.instruction.InvokeDynamicInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.classfile.instruction.NewObjectInstruction;
import java.lang.classfile.instruction.TypeCheckInstruction;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

/**
 * Exercises lazy Class-File API state through user-oriented traversal patterns.
 *
 * A shallow traversal lists members of freshly parsed models without visiting
 * code or attributes.  A deep traversal visits those parts of fresh models
 * once.  A repeated deep traversal revisits models parsed and traversed during
 * trial setup.
 */
@BenchmarkMode(Mode.AverageTime)
@Warmup(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@State(Scope.Thread)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class ClassfileCacheBenchmark {
    private static final int MODELS_PER_OPERATION = 64;

    private final ClassFile classFile = newClassFile();
    private byte[] fixtureBytes;

    @Setup
    public void setup() {
        fixtureBytes = classBytes(Fixture.class);
    }

    /** Lists field and method names in freshly parsed models. */
    @Benchmark
    public int shallowTraversal() {
        return shallowTraversal(newModels(classFile, fixtureBytes));
    }

    /** Visits attributes, code, instructions, and referenced entries once. */
    @Benchmark
    public int deepTraversal() {
        return deepTraversal(newModels(classFile, fixtureBytes));
    }

    /** Repeats a deep traversal over models previously traversed during setup. */
    @Benchmark
    public int repeatedDeepTraversal(DeepState state) {
        return state.setupChecksum + deepTraversal(state.models);
    }

    private static ClassFile newClassFile() {
        return ClassFile.of(ClassFile.DebugElementsOption.DROP_DEBUG);
    }

    private static ClassModel[] newModels(ClassFile classFile, byte[] fixtureBytes) {
        ClassModel[] models = new ClassModel[MODELS_PER_OPERATION];
        for (int i = 0; i < models.length; i++) {
            models[i] = classFile.parse(fixtureBytes);
        }
        return models;
    }

    private static int shallowTraversal(ClassModel[] models) {
        int result = 0;
        for (ClassModel model : models) {
            List<FieldModel> fields = model.fields();
            for (int i = 0; i < fields.size(); i++) {
                result += fields.get(i).fieldName().stringValue().length();
            }
            List<MethodModel> methods = model.methods();
            for (int i = 0; i < methods.size(); i++) {
                result += methods.get(i).methodName().stringValue().length();
            }
        }
        return result;
    }

    private static int deepTraversal(ClassModel[] models) {
        int result = 0;
        for (ClassModel model : models) {
            result += deepTraversal(model);
        }
        return result;
    }

    private static int deepTraversal(ClassModel model) {
        int result = model.thisClass().name().stringValue().length();
        var superclass = model.superclass().orElse(null);
        if (superclass != null) {
            result += superclass.name().stringValue().length();
        }
        List<? extends java.lang.classfile.constantpool.ClassEntry> interfaces = model.interfaces();
        for (int i = 0; i < interfaces.size(); i++) {
            result += interfaces.get(i).name().stringValue().length();
        }
        result += resolveAttributes(model.attributes());

        List<FieldModel> fields = model.fields();
        for (int i = 0; i < fields.size(); i++) {
            FieldModel field = fields.get(i);
            result += field.fieldName().stringValue().length();
            result += field.fieldType().stringValue().length();
            result += resolveAttributes(field.attributes());
        }

        List<MethodModel> methods = model.methods();
        for (int i = 0; i < methods.size(); i++) {
            MethodModel method = methods.get(i);
            result += method.methodName().stringValue().length();
            result += method.methodType().stringValue().length();
            result += resolveAttributes(method.attributes());
            CodeModel code = method.code().orElse(null);
            if (code != null) {
                result += resolveCode(code);
            }
        }
        return result;
    }

    private static int resolveCode(CodeModel code) {
        int result = code.exceptionHandlers().size();
        List<CodeElement> elements = code.elementList();
        for (int i = 0; i < elements.size(); i++) {
            CodeElement element = elements.get(i);
            if (element instanceof FieldInstruction instruction) {
                result += instruction.field().hashCode();
            } else if (element instanceof InvokeDynamicInstruction instruction) {
                result += instruction.invokedynamic().bootstrap().hashCode();
            } else if (element instanceof InvokeInstruction instruction) {
                result += instruction.method().hashCode();
            } else if (element instanceof NewObjectInstruction instruction) {
                result += instruction.className().hashCode();
            } else if (element instanceof TypeCheckInstruction instruction) {
                result += instruction.type().hashCode();
            }
        }

        List<Attribute<?>> attributes = code.attributes();
        result += resolveAttributes(attributes);
        for (int i = 0; i < attributes.size(); i++) {
            Attribute<?> attribute = attributes.get(i);
            if (attribute instanceof LocalVariableTableAttribute table) {
                var locals = table.localVariables();
                for (int j = 0; j < locals.size(); j++) {
                    var local = locals.get(j);
                    result += local.name().stringValue().length();
                    result += local.type().stringValue().length();
                }
            } else if (attribute instanceof LocalVariableTypeTableAttribute table) {
                var locals = table.localVariableTypes();
                for (int j = 0; j < locals.size(); j++) {
                    var local = locals.get(j);
                    result += local.name().stringValue().length();
                    result += local.signature().stringValue().length();
                }
            }
        }
        return result;
    }

    private static int resolveAttributes(List<Attribute<?>> attributes) {
        int result = 0;
        for (int i = 0; i < attributes.size(); i++) {
            Attribute<?> attribute = attributes.get(i);
            result += attribute.attributeName().stringValue().length();
            if (attribute instanceof ExceptionsAttribute exceptions) {
                result += exceptions.exceptions().size();
            } else if (attribute instanceof InnerClassesAttribute innerClasses) {
                result += innerClasses.classes().size();
            } else if (attribute instanceof LineNumberTableAttribute lineNumbers) {
                result += lineNumbers.lineNumbers().size();
            } else if (attribute instanceof LocalVariableTableAttribute locals) {
                result += locals.localVariables().size();
            } else if (attribute instanceof LocalVariableTypeTableAttribute localTypes) {
                result += localTypes.localVariableTypes().size();
            } else if (attribute instanceof MethodParametersAttribute parameters) {
                result += parameters.parameters().size();
            } else if (attribute instanceof RuntimeInvisibleAnnotationsAttribute annotations) {
                result += annotations.annotations().size();
            } else if (attribute instanceof RuntimeVisibleAnnotationsAttribute annotations) {
                result += annotations.annotations().size();
            } else if (attribute instanceof StackMapTableAttribute stackMap) {
                result += stackMap.entries().size();
            }
        }
        return result;
    }

    private static byte[] classBytes(Class<?> clazz) {
        String resourceName = "/" + clazz.getName().replace('.', '/') + ".class";
        try (InputStream in = clazz.getResourceAsStream(resourceName)) {
            if (in == null) {
                throw new AssertionError("missing fixture class: " + clazz.getName());
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new AssertionError("cannot read fixture class: " + clazz.getName(), e);
        }
    }

    @Deprecated
    private static class Fixture {
        @Deprecated
        private Object fieldI;

        private static final String fieldS = "Hello";

        static void staticMethod() throws UnsupportedOperationException {
            String s = fieldS.toLowerCase() + fieldS.toUpperCase();
        }

        @Deprecated
        void exercise(List<String> list) throws SQLException, IOException {
            Object local = new Object();
            fieldI = local;
            local.toString();
            list.size();
            Runnable runnable = () -> staticMethod();
            runnable.run();
            if (local instanceof String string) {
                fieldI = string;
            }
        }
    }

    @State(Scope.Thread)
    public static class DeepState {
        private ClassModel[] models;
        private int setupChecksum;

        @Setup
        public void setup() {
            ClassFile classFile = newClassFile();
            models = newModels(classFile, classBytes(Fixture.class));
            setupChecksum = deepTraversal(models);
        }
    }
}
