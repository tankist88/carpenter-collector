package org.carpenter.collector.aspect;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.carpenter.collector.dto.MethodCallInfo;
import org.carpenter.collector.util.ArgsHashCodeHolder;
import org.carpenter.core.dto.argument.GeneratedArgument;
import org.carpenter.core.dto.trace.TraceAnalyzeDto;
import org.carpenter.core.dto.unit.method.MethodCallTraceInfo;
import org.carpenter.core.exception.CallerNotFoundException;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.carpenter.collector.util.CollectUtil.*;
import static org.carpenter.core.util.ConvertUtil.toServiceProperties;
import static org.object2source.util.AssigmentUtil.hasZeroArgConstructor;
import static org.object2source.util.GenerationUtil.*;

@Aspect
public class TraceCollectorAspect {
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
    public Object aroundMethod(ProceedingJoinPoint pjp) throws Throwable {
        putArgsHashCode(pjp);
        Object ret = pjp.proceed();
        logMethodCall(pjp, ret);
        return ret;
    }

    private void putArgsHashCode(JoinPoint joinPoint) {
        String joinMethod = joinPoint.getSignature().getName();
        String joinClassName = getJoinClass(joinPoint).getName();
        if (!deniedMethod(joinMethod) && !deniedMethodSymbol(joinMethod) && allowedPackageForGeneration(joinClassName)) {
            ArgsHashCodeHolder.put(Objects.hash(joinPoint.getArgs()));
        }
    }

    private void logMethodCall(JoinPoint joinPoint, Object ret) throws CallerNotFoundException {
        String joinMethod = joinPoint.getSignature().getName();
        Class targetClass = getJoinClass(joinPoint);

        if (deniedMethod(joinMethod) || deniedMethodSymbol(joinMethod) || deniedClass(targetClass)) return;

        String joinClass = targetClass.getName();

        boolean testTargetClass = allowedPackageForGeneration(joinClass);
        int testClassArgHashCode = testTargetClass ? ArgsHashCodeHolder.get() : 0;
        if (testTargetClass) {
            ArgsHashCodeHolder.remove();
        }
        int callerArgsHashCode = ArgsHashCodeHolder.get();
        String threadName = Thread.currentThread().getName();
        String callerThreadKey = threadName + callerArgsHashCode;
        StackTraceElement[] stackTrace = (new Throwable()).getStackTrace();
        TraceAnalyzeDto traceAnalyzeDto = createTraceAnalyzeData(stackTrace, joinPoint, callerThreadKey);

        final MethodCallInfo info = createMethodCallInfo(joinPoint, ret, testClassArgHashCode,traceAnalyzeDto);

        if (testTargetClass || allowedPackageForGeneration(traceAnalyzeDto.getUpLevelElementClassName())) {
            Runnable callProcessor = new Runnable() {
                @Override
                public void run() {
                    List<Class> classHierarchy = getClassHierarchy(info.getClazz());

                    MethodCallTraceInfo targetMethod = new MethodCallTraceInfo();
                    targetMethod.setClassName(info.getClazz().getName());
                    targetMethod.setDeclaringTypeName(info.getDeclaringTypeName());
                    targetMethod.setUnitName(info.getMethodName());
                    targetMethod.setMemberClass(info.getClazz().isMemberClass());
                    targetMethod.setArguments(createGeneratedArgumentList(info.getParameterTypes(), info.getArgTypes(), info.getMethodName(), classHierarchy, info.getArgsProviders()));
                    targetMethod.setClassModifiers(info.getClazz().getModifiers());
                    targetMethod.setMethodModifiers(info.getMethodModifiers());
                    targetMethod.setVoidMethod(info.getRetType().equals(Void.TYPE));
                    targetMethod.setClassHierarchy(getClassHierarchyStr(classHierarchy));
                    targetMethod.setInterfacesHierarchy(getInterfacesHierarchyStr(info.getClazz()));
                    targetMethod.setServiceFields(toServiceProperties(getAllFieldsOfClass(classHierarchy)));
                    targetMethod.setClassHasZeroArgConstructor(hasZeroArgConstructor(info.getClazz(), false));

                    // технические поля
                    targetMethod.setKey(info.getMethodKey());
                    targetMethod.setTraceAnalyzeData(info.getTraceAnalyze());

                    GeneratedArgument returnValue = new GeneratedArgument(info.getRetType().getName(), info.getReturnProvider());
                    returnValue.setInterfacesHierarchy(getInterfacesHierarchyStr(info.getRetType()));
                    returnValue.setAnonymousClass(getLastClassShort(info.getRetType().getName()).matches("\\d+"));
                    returnValue.setNearestInstantAbleClass(returnValue.isAnonymousClass() ? getFirstPublicType(info.getRetType()).getName() : info.getRetType().getName());

                    targetMethod.setReturnArg(returnValue);
                    targetMethod.setCallTime(System.nanoTime());

                    saveObjectDump(targetMethod, info.getMethodKey(), info.getClazz().getName(), info.getTraceAnalyze().getUpLevelElementKey());
                }
            };
            EXECUTOR_SERVICE.submit(callProcessor);
        }
    }
}
