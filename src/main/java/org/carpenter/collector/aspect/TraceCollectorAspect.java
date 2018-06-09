package org.carpenter.collector.aspect;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.carpenter.collector.util.CollectUtil;
import org.carpenter.core.dto.argument.GeneratedArgument;
import org.carpenter.core.dto.trace.TraceAnalyzeDto;
import org.carpenter.core.dto.unit.method.MethodCallTraceInfo;
import org.carpenter.core.exception.CallerNotFoundException;
import org.object2source.SourceGenerator;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.aspectj.runtime.reflect.AspectMethodSignatureHelper.getParameterTypes;
import static org.aspectj.runtime.reflect.AspectMethodSignatureHelper.getReturnType;
import static org.carpenter.collector.util.CollectUtil.*;
import static org.carpenter.core.util.ConvertUtil.toServiceProperties;
import static org.object2source.util.AssigmentUtil.hasZeroArgConstructor;
import static org.object2source.util.GenerationUtil.*;

@Aspect
public class TraceCollectorAspect {
    private static final SourceGenerator SG = CollectUtil.getSgInstance();

    private static final ExecutorService EXECUTOR_SERVICE = Executors.newFixedThreadPool(50);

    @Pointcut("execution(* org.carpenter.collector..*(..))")
    public void thisLib() {
    }

    @Pointcut("execution(* org.carpenter.core..*(..))")
    public void thisCoreLib() {
    }

    @Pointcut("execution(* org.object2source..*(..))")
    public void object2source() {
    }

    @Pointcut("execution(* java..*(..))")
    public void java() {
    }

    @Pointcut("execution(* javax..*(..))")
    public void javax() {
    }

    @Pointcut("execution(* sun..*(..))")
    public void sun() {
    }

    @Pointcut("execution(* com.sun..*(..))")
    public void comsun() {
    }

    @Pointcut("execution(* org.aspectj..*(..))")
    public void aspectLibParts() {
    }

    @Pointcut("execution(* *(..))")
    public void callMethod() {
    }

    @Around("callMethod() && !thisLib() && !thisCoreLib() && !object2source() && !java() && !javax() && !sun() && !comsun() && !aspectLibParts()")
    public Object aroundMethod(final ProceedingJoinPoint joinPoint) throws Throwable {
        Object ret = joinPoint.proceed();
        logMethodCall(joinPoint, ret);
        return ret;
    }

    private void logMethodCall(final JoinPoint joinPoint, final Object ret) throws CallerNotFoundException {
        final String joinMethod = joinPoint.getSignature().getName();
        final Class targetClass = getJoinClass(joinPoint);

        if (deniedMethod(joinMethod) || deniedMethodSymbol(joinMethod) || deniedClass(targetClass)) return;

        final String joinClass = targetClass.getName();
        final String threadName = Thread.currentThread().getName();
        final StackTraceElement[] stackTrace = (new Throwable()).getStackTrace();
        final TraceAnalyzeDto traceAnalyzeTransport = createTraceAnalyzeData(stackTrace, joinPoint, threadName);

        if (allowedPackageForGeneration(traceAnalyzeTransport.getUpLevelElementClassName())) {
            Runnable callProcessor = new Runnable() {
                @Override
                public void run() {
                    TraceAnalyzeDto traceAnalyzeDto = getTraceAnalyzeDto(traceAnalyzeTransport, joinPoint, threadName, stackTrace);

                    List<Class> classHierarchy = getClassHierarchy(targetClass);

                    Object[] args = joinPoint.getArgs();
                    Class retType = getReturnType(joinPoint);

                    MethodCallTraceInfo targetMethod = new MethodCallTraceInfo();
                    targetMethod.setClassName(joinClass);
                    targetMethod.setDeclaringTypeName(joinPoint.getSignature().getDeclaringTypeName());
                    targetMethod.setUnitName(joinMethod);
                    targetMethod.setMemberClass(targetClass.isMemberClass());
                    targetMethod.setArguments(createGeneratedArgumentList(getParameterTypes(joinPoint), args, joinMethod, classHierarchy, SG));
                    targetMethod.setClassModifiers(targetClass.getModifiers());
                    targetMethod.setMethodModifiers(joinPoint.getSignature().getModifiers());
                    targetMethod.setVoidMethod(retType.equals(Void.TYPE));
                    targetMethod.setClassHierarchy(getClassHierarchyStr(classHierarchy));
                    targetMethod.setInterfacesHierarchy(getInterfacesHierarchyStr(targetClass));
                    targetMethod.setServiceFields(toServiceProperties(getAllFieldsOfClass(classHierarchy)));
                    targetMethod.setClassHasZeroArgConstructor(hasZeroArgConstructor(targetClass, false));

                    // технические поля
                    targetMethod.setKey(createMethodKey(joinPoint, threadName));
                    targetMethod.setTraceAnalyzeData(traceAnalyzeDto);

                    GeneratedArgument returnValue = new GeneratedArgument(retType.getName(), SG.createDataProviderMethod(ret));
                    returnValue.setInterfacesHierarchy(getInterfacesHierarchyStr(retType));
                    returnValue.setAnonymousClass(getLastClassShort(retType.getName()).matches("\\d+"));
                    returnValue.setNearestInstantAbleClass(returnValue.isAnonymousClass() ? getFirstPublicType(retType).getName() : retType.getName());
                    targetMethod.setReturnArg(returnValue);
                    targetMethod.setCallTime(System.nanoTime());

                    saveObjectDump(targetMethod, joinPoint, threadName, joinClass, Objects.hash(args), traceAnalyzeDto.getUpLevelElementKey());
                }
            };
            EXECUTOR_SERVICE.submit(callProcessor);
        }
    }
}
