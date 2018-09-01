package com.github.tankist88.carpenter.collector.util;

import com.github.tankist88.carpenter.collector.dto.MethodCallInfo;
import com.github.tankist88.carpenter.core.dto.argument.GeneratedArgument;
import com.github.tankist88.carpenter.core.dto.trace.TraceAnalyzeDto;
import com.github.tankist88.carpenter.core.property.GenerationProperties;
import com.github.tankist88.object2source.SourceGenerator;
import com.github.tankist88.object2source.dto.ProviderResult;
import com.github.tankist88.object2source.extension.Extension;
import org.aspectj.lang.JoinPoint;

import java.lang.reflect.Modifier;
import java.util.*;

import static com.github.tankist88.carpenter.core.property.AbstractGenerationProperties.COMMON_UTIL_POSTFIX;
import static com.github.tankist88.carpenter.core.property.AbstractGenerationProperties.TAB;
import static com.github.tankist88.carpenter.core.property.GenerationPropertiesFactory.loadProps;
import static com.github.tankist88.carpenter.core.util.TypeHelper.getMethodArgGenericTypeStr;
import static com.github.tankist88.object2source.util.GenerationUtil.*;
import static org.aspectj.runtime.reflect.AspectMethodSignatureHelper.getParameterTypes;
import static org.aspectj.runtime.reflect.AspectMethodSignatureHelper.getReturnType;

public class CollectUtils {
    private static final String[] DENIED_METHODS = {"hashCode","equals","compare"};
    private static final String[] DENIED_METHOD_SYMBOLS = {"$"};

    public static final SourceGenerator SG = getSgInstance();

    public static GeneratedArgument createGeneratedArgument(Class clazz, ProviderResult provider) {
        GeneratedArgument ga = new GeneratedArgument(clazz.getName(), provider);
        ga.setInterfacesHierarchy(getInterfacesHierarchyStr(clazz));
        ga.setAnonymousClass(getLastClassShort(clazz.getName()).matches("\\d+"));
        ga.setNearestInstantAbleClass(
                ga.isAnonymousClass() || !Modifier.isPublic(clazz.getModifiers())
                        ? getFirstPublicType(clazz).getName()
                        : clazz.getName());
        return ga;
    }

    public static List<GeneratedArgument> createGeneratedArgumentList(
            Class[] types,
            Class[] argTypes,
            String methodName,
            List<Class> classHierarchy,
            ProviderResult[] argsProviderArr
    ) {
        List<GeneratedArgument> argList = new ArrayList<>();
        for (int i = 0; i < argTypes.length; i++) {
            Class type = argTypes[i];
            ProviderResult providerResult = !Modifier.isPrivate(type.getModifiers()) ? argsProviderArr[i] : null;
            GeneratedArgument genArg = createGeneratedArgument(type, providerResult);
            try {
                genArg.setGenericString(getMethodArgGenericTypeStr(classHierarchy, methodName, i, types));
            } catch (NoSuchMethodException e) {
                genArg.setGenericString(null);
            }
            argList.add(genArg);
        }
        return argList;
    }

    private static SourceGenerator getSgInstance() {
        GenerationProperties props = loadProps();
        Set<String> allowedPackages = new HashSet<>(Arrays.asList(props.getAllowedPackagesForDp()));
        String utilClass = props.getDataProviderClassPattern() + COMMON_UTIL_POSTFIX;
        SourceGenerator sg = new SourceGenerator(TAB, allowedPackages, utilClass);
        sg.setExceptionWhenMaxODepth(false);
        sg.setMaxObjectDepth(props.getMaxObjectDepth());
        for(String classname : props.getExternalExtensionClassNames()) {
            try {
                Extension ext = (Extension) Class.forName(classname).newInstance();
                sg.registerExtension(ext);
            } catch (ReflectiveOperationException reflEx) {
                throw new IllegalStateException(reflEx);
            }
        }
        return sg;
    }

    public static boolean excludedThreadName(String threadName) {
        if(threadName == null) return false;
        for (String p : loadProps().getExcludedThreadNames()) {
            if(threadName.contains(p)) return true;
        }
        return false;
    }

    public static boolean allowedPackageForGen(String className) {
        if(className == null) return false;
        for (String p : loadProps().getAllowedPackagesForTests()) {
            if(className.startsWith(p)) return true;
        }
        return false;
    }

    public static boolean deniedMethod(String methodName) {
        for (String m : DENIED_METHODS) {
            if(methodName.startsWith(m)) return true;
        }
        return false;
    }

    public static boolean deniedMethodSymbol(String methodName) {
        for (String m : DENIED_METHOD_SYMBOLS) {
            if(methodName.contains(m)) return true;
        }
        return false;
    }

    public static String getMethodKey(String className, String method, String threadName) {
        return threadName + "_" + className + "_" + method;
    }

    private static String createMethodKey(JoinPoint joinPoint, String threadName) {
        String joinClass = joinPoint.getSourceLocation().getWithinType().getName();
        String joinMethod = joinPoint.getSignature().getName();
        return getMethodKey(joinClass, joinMethod, threadName);
    }

    public static Class getJoinClass(JoinPoint joinPoint) {
        return joinPoint.getTarget() != null ? joinPoint.getTarget().getClass() : joinPoint.getSignature().getDeclaringType();
    }

    static String clearAspectMethod(String method) {
        if(method.contains("_aroundBody")) {
            return method.substring(0, method.indexOf("_aroundBody"));
        } else {
            return method;
        }
    }

    public static MethodCallInfo createMethodCallInfo(
            JoinPoint joinPoint,
            ProviderResult[] argsProviders,
            Object ret,
            int ownArgsHashCode,
            TraceAnalyzeDto traceAnalyzeDto,
            String threadName,
            ProviderResult targetProvider
    ) {
        MethodCallInfo result = new MethodCallInfo();

        result.setArgsProviders(argsProviders);

        Class[] parameterTypes = getParameterTypes(joinPoint);
        result.setParameterTypes(parameterTypes);

        Object[] args = joinPoint.getArgs();
        Class[] argTypes = new Class[args.length];
        for (int i = 0; i < args.length; i++) {
            argTypes[i] = args[i] != null ? args[i].getClass() : parameterTypes[i];
        }
        result.setArgTypes(argTypes);

        result.setClazz(getJoinClass(joinPoint));
        result.setDeclaringTypeName(joinPoint.getSignature().getDeclaringTypeName());

        String threadKey = threadName + ownArgsHashCode;
        result.setMethodKey(createMethodKey(joinPoint, threadKey));
        result.setMethodModifiers(joinPoint.getSignature().getModifiers());
        result.setMethodName(joinPoint.getSignature().getName());
        result.setRetType(getReturnType(joinPoint));
        result.setReturnProvider(SG.createDataProviderMethod(ret));
        result.setTraceAnalyze(traceAnalyzeDto);
        result.setTargetProvider(targetProvider);

        return result;
    }
}