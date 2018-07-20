package org.carpenter.collector.util;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.SerializationUtils;
import org.aspectj.lang.JoinPoint;
import org.carpenter.collector.dto.MethodCallInfo;
import org.carpenter.core.dto.argument.GeneratedArgument;
import org.carpenter.core.dto.trace.TraceAnalyzeDto;
import org.carpenter.core.property.GenerationProperties;
import org.carpenter.core.property.GenerationPropertiesFactory;
import org.object2source.SourceGenerator;
import org.object2source.dto.ProviderResult;
import org.object2source.extension.Extension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.lang.reflect.Modifier;
import java.util.*;

import static org.aspectj.runtime.reflect.AspectMethodSignatureHelper.getParameterTypes;
import static org.aspectj.runtime.reflect.AspectMethodSignatureHelper.getReturnType;
import static org.carpenter.core.property.AbstractGenerationProperties.*;
import static org.carpenter.core.util.TypeHelper.getMethodArgGenericTypeStr;
import static org.object2source.util.GenerationUtil.*;

public class CollectUtil {
    private static final Logger logger = LoggerFactory.getLogger(CollectUtil.class);

    private static final String[] DENIED_METHODS = {"hashCode","equals","compare"};
    private static final String[] DENIED_METHOD_SYMBOLS = {"$"};

    public static final SourceGenerator SG = getSgInstance();

    public static List<GeneratedArgument> createGeneratedArgumentList(Class[] types, Class[] argTypes, String methodName, List<Class> classHierarchy, ProviderResult[] argsProviderArr) {
        List<GeneratedArgument> argList = new ArrayList<>();
        for (int i = 0; i < argTypes.length; i++) {
            Class type = argTypes[i];
            ProviderResult providerResult = !Modifier.isPrivate(type.getModifiers()) ? argsProviderArr[i] : null;
            GeneratedArgument genArg = new GeneratedArgument(type.getName(), providerResult);
            try {
                genArg.setGenericString(getMethodArgGenericTypeStr(classHierarchy, methodName, i, types));
            } catch (NoSuchMethodException e) {
                genArg.setGenericString(null);
            }
            genArg.setInterfacesHierarchy(getInterfacesHierarchyStr(type));
            genArg.setAnonymousClass(getLastClassShort(type.getName()).matches("\\d+"));
            genArg.setNearestInstantAbleClass(genArg.isAnonymousClass() ? getFirstPublicType(type).getName() : type.getName());
            argList.add(genArg);
        }
        return argList;
    }

    private static SourceGenerator getSgInstance() {
        GenerationProperties props = GenerationPropertiesFactory.loadProps();
        Set<String> allowedPackages = new HashSet<>(Arrays.asList(props.getAllowedPackagesForDp()));
        String utilClass = props.getDataProviderClassPattern() + COMMON_UTIL_POSTFIX;
        SourceGenerator sg = new SourceGenerator(TAB, allowedPackages, utilClass);
        sg.setExceptionWhenMaxODepth(false);
        sg.setMaxObjectDepth(10);
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
        for (String p : GenerationPropertiesFactory.loadProps().getExcludedThreadNames()) {
            if(threadName.contains(p)) return true;
        }
        return false;
    }

    public static boolean allowedPackageForGeneration(String className) {
        if(className == null) return false;
        for (String p : GenerationPropertiesFactory.loadProps().getAllowedPackagesForTests()) {
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

    public static void saveObjectDump(Serializable object, String methodKey, String className, String upLevelKey) {
        try {
            String key = methodKey + "_" + upLevelKey;
            String keyHash = DigestUtils.md5Hex(key);
            String packageFileStruct = GenerationPropertiesFactory.loadProps().getObjectDumpDir() + "/" + getPackage(className).replaceAll("\\.", "/");
            FileUtils.forceMkdir(new File(packageFileStruct));
            File file = new File(packageFileStruct, keyHash + "." + OBJ_FILE_EXTENSION);
            DataOutputStream dos = new DataOutputStream(new FileOutputStream(file));
            byte[] bytes = SerializationUtils.serialize(object);
            dos.writeInt(bytes.length);
            dos.write(bytes);
            dos.close();
        } catch (IOException iex) {
            String errorMsg = "Can't save object dump! " + iex.getMessage();
            logger.error(errorMsg);
            throw new IllegalStateException(errorMsg, iex);
        }
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

    public static MethodCallInfo createMethodCallInfo(JoinPoint joinPoint, ProviderResult[] argsProviders, Object ret, int ownArgsHashCode, TraceAnalyzeDto traceAnalyzeDto, String threadName) {
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

        return result;
    }
}

