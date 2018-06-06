package org.carpenter.collector.util;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.SerializationUtils;
import org.aspectj.lang.JoinPoint;
import org.carpenter.collector.aspect.TraceCollectorAspect;
import org.carpenter.core.dto.argument.GeneratedArgument;
import org.carpenter.core.dto.trace.TraceAnalyzeDto;
import org.carpenter.core.exception.CallerNotFoundException;
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

import static org.carpenter.core.property.AbstractGenerationProperties.*;
import static org.carpenter.core.util.TypeHelper.getMethodArgGenericTypeStr;
import static org.object2source.util.GenerationUtil.*;

public class CollectUtil {
    private static final Logger logger = LoggerFactory.getLogger(CollectUtil.class);

    private static final String[] DENIED_METHODS = {"hashCode","equals","compare"};
    private static final String[] DENIED_METHOD_SYMBOLS = {"$"};

    public static TraceAnalyzeDto createTraceAnalyzeData(StackTraceElement[] stackTrace, JoinPoint joinPoint, String threadName) throws CallerNotFoundException {
        TraceAnalyzeDto result = new TraceAnalyzeDto();
        StackTraceElement upLevelElement = null;
        boolean anonymousCall = false;
        String anonymousCallerClass = null;
        StackTraceElement tmpAnonymousUpLevelElement = null;
        int level = 1;
        for (int i = 1; i < stackTrace.length; i++) {
            if (    stackTrace[i].getClassName().equals(TraceCollectorAspect.class.getName()) ||
                    stackTrace[i].getClassName().equals(Thread.class.getName()) ||
                    stackTrace[i].getClassName().equals(CollectUtil.class.getName()))
            {
                continue;
            }
            if (!anonymousCall && level == 0 && !deniedClassAndMethod(stackTrace[i]) && !deniedPackage(stackTrace[i]) && !sameMethod(joinPoint, stackTrace[i])) {
                upLevelElement = stackTrace[i];
                break;
            }
            if (anonymousCall && level == 0 && stackTrace[i].getClassName().equals(anonymousCallerClass) && !sameMethod(joinPoint, stackTrace[i])) {
                upLevelElement = stackTrace[i];
                break;
            }
            if (level > 0 && sameMethod(joinPoint, stackTrace[i])) {
                level--;
            }
            if(!anonymousCall) {
                anonymousCall = getLastClassShort(stackTrace[i].getClassName()).matches("\\d+");
                if (anonymousCall && anonymousCallerClass == null) {
                    anonymousCallerClass = getOwnerParentClass(stackTrace[i].getClassName());
                }
            } else if (!getLastClassShort(stackTrace[i].getClassName()).matches("\\d+")) {
                tmpAnonymousUpLevelElement = stackTrace[i];
            }
        }
        if(upLevelElement != null) {
            result.setUpLevelElementKey(CollectUtil.getMethodKey(upLevelElement, threadName));
            result.setUpLevelElementClassName(upLevelElement.getClassName());
        } else if (tmpAnonymousUpLevelElement != null) {
            result.setUpLevelElementKey(CollectUtil.getMethodKey(tmpAnonymousUpLevelElement, threadName));
            result.setUpLevelElementClassName(tmpAnonymousUpLevelElement.getClassName());
        } else {
            throw new CallerNotFoundException("Cant' find caller for " + createMethodKey(joinPoint, threadName));
        }
        return result;
    }

    private static boolean sameMethod(JoinPoint joinPoint, StackTraceElement st) {
        String className = joinPoint.getSourceLocation().getWithinType().getName();
        String methodName = joinPoint.getSignature().getName();
        return className.equals(st.getClassName()) && methodName.equals(clearAspectMethod(st.getMethodName()));
    }

    public static List<GeneratedArgument> createGeneratedArgumentList(Class[] types, Object[] args, String methodName, List<Class> classHierarchy, SourceGenerator sg) {
        List<GeneratedArgument> argList = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            Class type = args[i] != null ? args[i].getClass() : types[i];
            ProviderResult providerResult = !Modifier.isPrivate(type.getModifiers()) ? sg.createDataProviderMethod(args[i]) : null;
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

    public static SourceGenerator getSgInstance() {
        GenerationProperties props = GenerationPropertiesFactory.loadProps();
        Set<String> excludedPackages = new HashSet<>(
                Arrays.asList(props.getExcludedPackagesForDp())
        );
        String utilClass = props.getDataProviderClassPattern() + COMMON_UTIL_POSTFIX;
        SourceGenerator sg = new SourceGenerator(TAB, excludedPackages, utilClass);
        sg.setExceptionWhenMaxODepth(false);
        sg.setMaxObjectDepth(15);
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

    public static boolean allowedPackageForGeneration(String className) {
        if(className == null) return false;
        for (String p : GenerationPropertiesFactory.loadProps().getAllowedPackagesForTests()) {
            if(className.startsWith(p)) return true;
        }
        return false;
    }

    private static boolean deniedPackage(StackTraceElement st) {
        return st == null || deniedPackage(st.getClassName());
    }

    public static boolean deniedPackage(String className) {
        for (String ex : GenerationPropertiesFactory.loadProps().getExcludedPackagesForTraceCollect()) {
            if(className.startsWith(ex)) {
                return true;
            }
        }
        return false;
    }

    public static boolean deniedMethod(String methodName) {
        for (String m : DENIED_METHODS) {
            if(methodName.startsWith(m)) return true;
        }
        return false;
    }

    private static boolean deniedClassAndMethod(StackTraceElement st) {
        if(st == null) return true;
        for (String m : DENIED_METHOD_SYMBOLS) {
            if(clearAspectMethod(st.getMethodName()).contains(m) || st.getClassName().contains(m)) return true;
        }
        return false;
    }

    public static boolean deniedMethodSymbol(String methodName) {
        for (String m : DENIED_METHOD_SYMBOLS) {
            if(methodName.contains(m)) return true;
        }
        return false;
    }

    private static String getMethodKey(String className, String method, String threadName) {
        return threadName + "_" + className + "_" + method;
    }

    private static String getMethodKey(StackTraceElement st, String threadName) {
        String className = st.getClassName();
        String method = clearAspectMethod(st.getMethodName());
        return getMethodKey(className, method, threadName);
    }

    public static String createMethodKey(JoinPoint joinPoint, String threadName) {
        String joinClass = joinPoint.getSourceLocation().getWithinType().getName();
        String joinMethod = joinPoint.getSignature().getName();
        return getMethodKey(joinClass, joinMethod, threadName);
    }

    public static void saveObjectDump(Serializable object, JoinPoint joinPoint, String threadName, String className, int argsHashCode, String upLevelKey) {
        try {
            String key = createMethodKey(joinPoint, threadName)+ "_" + argsHashCode + "_" + upLevelKey;
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

    public static TraceAnalyzeDto getTraceAnalyzeDto(TraceAnalyzeDto traceAnalyzeTransport, JoinPoint joinPoint, String threadName, StackTraceElement[] stackTrace) {
        try {
            return traceAnalyzeTransport != null ? traceAnalyzeTransport : createTraceAnalyzeData(stackTrace, joinPoint, threadName);
        } catch (CallerNotFoundException ce) {
            String joinClass = getJoinClass(joinPoint).getName();
            String joinMethod = joinPoint.getSignature().getName();
            String errorMsg = "FATAL ERROR. Can't determine caller for " + joinClass + "." + joinMethod;
            logger.error(errorMsg, ce);
            throw new IllegalStateException(errorMsg, ce);
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
}

