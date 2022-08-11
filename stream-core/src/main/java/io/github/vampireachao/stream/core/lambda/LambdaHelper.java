package io.github.vampireachao.stream.core.lambda;

import io.github.vampireachao.stream.core.bean.BeanHelper;
import io.github.vampireachao.stream.core.lambda.function.SerFunc;
import io.github.vampireachao.stream.core.optional.Opp;
import io.github.vampireachao.stream.core.reflect.ReflectHelper;
import io.github.vampireachao.stream.core.stream.Steam;

import java.io.Serializable;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.SerializedLambda;
import java.lang.reflect.*;
import java.util.Objects;
import java.util.WeakHashMap;


/**
 * LambdaHelper
 *
 * @author VampireAchao
 * @since 2022/5/29 9:19
 */
public class LambdaHelper {

    private static final WeakHashMap<String, LambdaExecutable> SERIALIZED_LAMBDA_EXECUTABLE_CACHE = new WeakHashMap<>();

    private LambdaHelper() {
        /* Do not new me! */
    }

    /**
     * Resolve the lambda to a {@link SerializedLambda} instance.
     *
     * @param lambda The lambda to resolve.
     * @return SerializedLambda
     */
    private static SerializedLambda serialize(Serializable lambda) {
        if (lambda instanceof SerializedLambda) {
            return (SerializedLambda) lambda;
        }
        final Class<? extends Serializable> clazz = lambda.getClass();
        if (!clazz.isSynthetic()) {
            throw new IllegalArgumentException("Not a lambda expression: " + clazz.getName());
        }
        try {
            final Method writeReplace = ReflectHelper.accessible(clazz.getDeclaredMethod("writeReplace"));
            final Object maybeSerLambda = writeReplace.invoke(lambda);
            if (Objects.nonNull(maybeSerLambda) && maybeSerLambda instanceof SerializedLambda) {
                return (SerializedLambda) maybeSerLambda;
            }
            throw new IllegalStateException("writeReplace result value is not java.lang.invoke.SerializedLambda");
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            throw new IllegalStateException(e);
        }
    }


    /**
     * Resolve the lambda to a {@link LambdaExecutable} instance.
     *
     * @param lambda The lambda to resolve.
     * @return LambdaExecutable
     */
    public static <T extends Serializable> LambdaExecutable resolve(T lambda) {
        Objects.requireNonNull(lambda, "lambda can not be null");
        if (lambda instanceof Proxy) {
            InvocationHandler handler = Proxy.getInvocationHandler(lambda);
            MethodHandle methodHandle = ReflectHelper.getFieldValue(handler, "val$target");
            final Executable executable = MethodHandles.reflectAs(Executable.class, methodHandle);
            final LambdaExecutable lambdaExecutable = new LambdaExecutable(executable);
            lambdaExecutable.initByMethodHandle(methodHandle);
            return lambdaExecutable;
        }
        return SERIALIZED_LAMBDA_EXECUTABLE_CACHE.computeIfAbsent(lambda.getClass().getName(), key -> new LambdaExecutable(serialize(lambda)));
    }

    @SafeVarargs
    public static <T> String[] getPropertyNames(SerFunc<T, ?>... funcs) {
        return Steam.of(funcs).map(LambdaHelper::getPropertyName).toArray(String[]::new);
    }

    public static <T> String getPropertyName(SerFunc<T, ?> func) {
        return Opp.of(func).map(LambdaHelper::resolve).map(LambdaExecutable::getName).map(BeanHelper::getPropertyName).get();
    }

}
