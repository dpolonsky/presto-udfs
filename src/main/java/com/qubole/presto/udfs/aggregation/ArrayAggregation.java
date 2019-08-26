///*
// * Copyright 2013-2016 Qubole
// *
// * Licensed under the Apache License, Version 2.0 (the "License");
// * you may not use this file except in compliance with the License.
// * You may obtain a copy of the License at
// *
// *     http://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing, software
// * distributed under the License is distributed on an "AS IS" BASIS,
// * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// * See the License for the specific language governing permissions and
// * limitations under the License.
// */
//package com.qubole.presto.udfs.aggregation;
//
//import io.prestosql.metadata.BoundVariables;
//import io.prestosql.metadata.FunctionRegistry;
//import io.prestosql.metadata.Signature;
//import io.prestosql.metadata.SqlAggregationFunction;
//import io.prestosql.operator.aggregation.AccumulatorCompiler;
//import io.prestosql.operator.aggregation.AggregationMetadata;
//import io.prestosql.operator.aggregation.AggregationMetadata.ParameterMetadata;
//import io.prestosql.operator.aggregation.GenericAccumulatorFactoryBinder;
//import io.prestosql.operator.aggregation.InternalAggregationFunction;
//import io.prestosql.spi.block.Block;
//import io.prestosql.spi.block.BlockBuilder;
//import io.prestosql.spi.block.BlockBuilderStatus;
//import io.prestosql.spi.type.Decimals;
//import io.prestosql.spi.type.SqlDecimal;
//import io.prestosql.spi.type.StandardTypes;
//import io.prestosql.spi.type.Type;
//import io.prestosql.spi.type.TypeManager;
//import io.prestosql.spi.type.TypeSignature;
//import io.prestosql.spi.type.ArrayType;
//import com.google.common.annotations.VisibleForTesting;
//import com.google.common.collect.ImmutableList;
//import com.qubole.presto.udfs.aggregation.state.ArrayAggregationState;
//import com.qubole.presto.udfs.aggregation.state.ArrayAggregationStateFactory;
//import com.qubole.presto.udfs.aggregation.state.ArrayAggregationStateSerializer;
//import io.airlift.bytecode.DynamicClassLoader;
//import io.airlift.slice.DynamicSliceOutput;
//import io.airlift.slice.Slice;
//import io.airlift.slice.SliceInput;
//import io.airlift.slice.SliceOutput;
//import io.airlift.slice.Slices;
//
//import java.lang.invoke.MethodHandle;
//import java.util.ArrayList;
//import java.util.List;
//import java.util.Map;
//import java.util.stream.Collectors;
//
//import static io.prestosql.operator.aggregation.AggregationMetadata.ParameterMetadata.ParameterType.BLOCK_INDEX;
//import static io.prestosql.operator.aggregation.AggregationMetadata.ParameterMetadata.ParameterType.BLOCK_INPUT_CHANNEL;
//import static io.prestosql.operator.aggregation.AggregationMetadata.ParameterMetadata.ParameterType.STATE;
//import static io.prestosql.operator.aggregation.AggregationUtils.generateAggregationName;
//import static io.prestosql.spi.type.RealType.REAL;
//import static io.prestosql.util.Reflection.methodHandle;
//import static java.lang.Float.floatToRawIntBits;
//
//public class ArrayAggregation
//        extends SqlAggregationFunction
//{
//    private static final String NAME = "array_aggr";
//    private static final MethodHandle INPUT_FUNCTION = methodHandle(ArrayAggregation.class, "input", Type.class, ArrayAggregationState.class, Block.class, int.class);
//    private static final MethodHandle COMBINE_FUNCTION = methodHandle(ArrayAggregation.class, "combine", Type.class, ArrayAggregationState.class, ArrayAggregationState.class);
//    private static final MethodHandle OUTPUT_FUNCTION = methodHandle(ArrayAggregation.class, "output", ArrayAggregationState.class, BlockBuilder.class);
//
//    public ArrayAggregation()
//    {
//        super(NAME, ImmutableList.of(Signature.typeVariable("T")), ImmutableList.of(), TypeSignature.parseTypeSignature("array(T)"), ImmutableList.of(TypeSignature.parseTypeSignature("T")));
//    }
//
//    @Override
//    public String getDescription()
//    {
//        return "return an array of values";
//    }
//
//    @Override
//    public InternalAggregationFunction specialize(BoundVariables boundVariables, int arity, TypeManager typeManager, FunctionRegistry functionRegistry)
//    {
//        Type valueType = boundVariables.getTypeVariable("T");
//        return generateAggregation(valueType);
//    }
//
//    private static InternalAggregationFunction generateAggregation(Type valueType)
//    {
//        DynamicClassLoader classLoader = new DynamicClassLoader(ArrayAggregation.class.getClassLoader());
//
//        ArrayAggregationStateSerializer stateSerializer = new ArrayAggregationStateSerializer();
//        Type intermediateType = stateSerializer.getSerializedType();
//
//        List<Type> inputTypes = ImmutableList.of(valueType);
//        Type outputType = new ArrayType(valueType);
//        ArrayAggregationStateFactory stateFactory = new ArrayAggregationStateFactory(valueType);
//        AggregationMetadata metadata = new AggregationMetadata(
//                generateAggregationName(NAME, valueType.getTypeSignature(), (List) inputTypes.stream().map(Type::getTypeSignature).collect(Collectors.toList())),
//                createInputParameterMetadata(valueType),
//                INPUT_FUNCTION.bindTo(valueType),
//                COMBINE_FUNCTION.bindTo(valueType),
//                OUTPUT_FUNCTION.bindTo(valueType),
//                ArrayAggregationState.class,
//                stateSerializer,
//                stateFactory,
//                outputType);
//
//        GenericAccumulatorFactoryBinder factory = AccumulatorCompiler.generateAccumulatorFactoryBinder(metadata, classLoader);
//        return new InternalAggregationFunction(NAME, inputTypes, intermediateType, outputType, true, true, factory);
//    }
//
//    private static List<ParameterMetadata> createInputParameterMetadata(Type value)
//    {
//        return ImmutableList.of(new ParameterMetadata(STATE), new ParameterMetadata(BLOCK_INPUT_CHANNEL, value), new ParameterMetadata(BLOCK_INDEX));
//    }
//
//    public static void input(Type type, ArrayAggregationState state, Block value, int position)
//    {
//        if (state.getSliceOutput() == null) {
//            SliceOutput sliceOutput = new DynamicSliceOutput(12);
//            state.setEntries(0);
//            state.setSliceOutput(sliceOutput);
//        }
//        state.setEntries(state.getEntries() + 1);
//        appendTo(state.getType(), state.getSliceOutput(), value, position);
//    }
//
//    public static void input(ArrayAggregationState state, Block value, int position)
//    {
//        input(state.getType(), state, value, position);
//    }
//
//    private static void appendTo(Type type, SliceOutput output, Block block, int position)
//    {
//        if (type.getJavaType() == long.class) {
//            output.appendLong(type.getLong(block, position));
//        }
//        else if (type.getJavaType() == double.class) {
//            output.appendDouble(type.getDouble(block, position));
//        }
//        else if (type.getJavaType() == Slice.class) {
//            Slice s = type.getSlice(block, position);
//            output.appendInt(s.length());
//            output.appendBytes(s);
//        }
//        else if (type.getJavaType() == boolean.class) {
//            output.appendByte(type.getBoolean(block, position) ? 1 : 0);
//        }
//        else {
//            throw new IllegalArgumentException("Unsupported type: " + type.getJavaType().getSimpleName());
//        }
//    }
//
//    public static void combine(Type type, ArrayAggregationState state, ArrayAggregationState otherState)
//    {
//        SliceOutput s1 = state.getSliceOutput();
//        SliceOutput s2 = otherState.getSliceOutput();
//        if (s1 == null && s2 != null) {
//            state.setSliceOutput(s2);
//            state.setEntries(otherState.getEntries());
//        }
//        else if (s1 != null) {
//            s1.appendBytes(s2.slice());
//            state.setEntries(state.getEntries() + otherState.getEntries());
//        }
//    }
//
//    public static void combine(ArrayAggregationState state, ArrayAggregationState otherState)
//    {
//        combine(state.getType(), state, otherState);
//    }
//
//    public static void output(ArrayAggregationState state, BlockBuilder out)
//    {
//        if (state.getSliceOutput() == null) {
//            out.appendNull();
//        }
//        else {
//            SliceInput sliceInput = state.getSliceOutput().slice().getInput();
//            Type type = state.getType();
//            long entries = state.getEntries();
//            List<Object> values = toValues(type, sliceInput, entries);
//            Block block = arrayBlockOf(values, type);
//            out.writeObject(block);
//            /*Slice s = toStackRepresentation(values, type);
//            out.writeBytes(s, 0, s.length());*/
//            out.closeEntry();
//        }
//    }
//
//    private static List<Object> toValues(Type type, SliceInput input, long entries)
//    {
//        List<Object> ret = new ArrayList<Object>((int) entries);
//        for (int i = 0; i < entries; i++) {
//            Object o = null;
//            if (type.getJavaType() == long.class) {
//                o = new Long(input.readLong());
//            }
//            else if (type.getJavaType() == double.class) {
//                o = new Double(input.readDouble());
//            }
//            else if (type.getJavaType() == Slice.class) {
//                int length = input.readInt();
//                o = input.readSlice(length);
//            }
//            else if (type.getJavaType() == boolean.class) {
//                o = new Boolean(input.readByte() != 0);
//            }
//            else {
//                throw new IllegalArgumentException("Unsupported type: " + type.getJavaType().getSimpleName());
//            }
//            ret.add(o);
//        }
//        return ret;
//    }
//
//    public static Block arrayBlockOf(List<Object> values, Type elementType)
//    {
//        BlockBuilder blockBuilder = elementType.createBlockBuilder(new BlockBuilderStatus(), values.size());
//        for (Object value : values) {
//            appendToBlockBuilder(elementType, value, blockBuilder);
//        }
//        return blockBuilder.build();
//    }
//
//    @VisibleForTesting
//    public static void appendToBlockBuilder(Type type, Object element, BlockBuilder blockBuilder)
//    {
//        Class<?> javaType = type.getJavaType();
//        if (element == null) {
//            blockBuilder.appendNull();
//        }
//        else if (type.getTypeSignature().getBase().equals(StandardTypes.ARRAY) && element instanceof Iterable<?>) {
//            BlockBuilder subBlockBuilder = blockBuilder.beginBlockEntry();
//            for (Object subElement : (Iterable<?>) element) {
//                appendToBlockBuilder(type.getTypeParameters().get(0), subElement, subBlockBuilder);
//            }
//            blockBuilder.closeEntry();
//        }
//        else if (type.getTypeSignature().getBase().equals(StandardTypes.ROW) && element instanceof Iterable<?>) {
//            BlockBuilder subBlockBuilder = blockBuilder.beginBlockEntry();
//            int field = 0;
//            for (Object subElement : (Iterable<?>) element) {
//                appendToBlockBuilder(type.getTypeParameters().get(field), subElement, subBlockBuilder);
//                field++;
//            }
//            blockBuilder.closeEntry();
//        }
//        else if (type.getTypeSignature().getBase().equals(StandardTypes.MAP) && element instanceof Map<?, ?>) {
//            BlockBuilder subBlockBuilder = blockBuilder.beginBlockEntry();
//            for (Map.Entry<?, ?> entry : ((Map<?, ?>) element).entrySet()) {
//                appendToBlockBuilder(type.getTypeParameters().get(0), entry.getKey(), subBlockBuilder);
//                appendToBlockBuilder(type.getTypeParameters().get(1), entry.getValue(), subBlockBuilder);
//            }
//            blockBuilder.closeEntry();
//        }
//        else if (javaType == boolean.class) {
//            type.writeBoolean(blockBuilder, (Boolean) element);
//        }
//        else if (javaType == long.class) {
//            if (element instanceof SqlDecimal) {
//                type.writeLong(blockBuilder, ((SqlDecimal) element).getUnscaledValue().longValue());
//            }
//            else if (REAL.equals(type)) {
//                type.writeLong(blockBuilder, floatToRawIntBits(((Number) element).floatValue()));
//            }
//            else {
//                type.writeLong(blockBuilder, ((Number) element).longValue());
//            }
//        }
//        else if (javaType == double.class) {
//            type.writeDouble(blockBuilder, ((Number) element).doubleValue());
//        }
//        else if (javaType == Slice.class) {
//            if (element instanceof String) {
//                type.writeSlice(blockBuilder, Slices.utf8Slice(element.toString()));
//            }
//            else if (element instanceof byte[]) {
//                type.writeSlice(blockBuilder, Slices.wrappedBuffer((byte[]) element));
//            }
//            else if (element instanceof SqlDecimal) {
//                type.writeSlice(blockBuilder, Decimals.encodeUnscaledValue(((SqlDecimal) element).getUnscaledValue()));
//            }
//            else {
//                type.writeSlice(blockBuilder, (Slice) element);
//            }
//        }
//        else {
//            type.writeObject(blockBuilder, element);
//        }
//    }
//}
