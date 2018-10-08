package com.github.tankist88.carpenter.collector.aspect;

import com.github.tankist88.carpenter.collector.dto.MethodCallInfo;
import com.github.tankist88.carpenter.collector.dto.TraceElement;
import com.github.tankist88.carpenter.collector.util.ArgsHashCodeHolder;
import com.github.tankist88.carpenter.core.dto.trace.TraceAnalyzeDto;
import com.github.tankist88.carpenter.core.dto.unit.method.MethodCallTraceInfo;
import com.github.tankist88.object2source.dto.ProviderResult;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;

import java.util.List;
import java.util.concurrent.ExecutorService;

import static com.github.tankist88.carpenter.collector.util.CollectUtils.*;
import static com.github.tankist88.carpenter.collector.util.DumpUtils.saveObjectDump;
import static com.github.tankist88.carpenter.core.property.GenerationPropertiesFactory.loadProps;
import static com.github.tankist88.carpenter.core.util.ConvertUtil.toServiceProperties;
import static com.github.tankist88.object2source.util.AssigmentUtil.hasZeroArgConstructor;
import static com.github.tankist88.object2source.util.ExtensionUtil.isDynamicProxy;
import static com.github.tankist88.object2source.util.ExtensionUtil.isInvocationHandler;
import static com.github.tankist88.object2source.util.GenerationUtil.*;
import static java.util.concurrent.Executors.newFixedThreadPool;

@Aspect
public class TraceCollectorAspect {
    private static final ExecutorService EXECUTOR_SERVICE =
            newFixedThreadPool(loadProps().getCollectorThreadPoolSize());

    @Pointcut("execution(* com.github.tankist88.carpenter.collector..*(..))")
    public void thisLib() {
    }

    @Pointcut("execution(* com.github.tankist88.carpenter.core..*(..))")
    public void thisCoreLib() {
    }

    @Pointcut("execution(* com.github.tankist88.object2source..*(..))")
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

    @Around("callMethod() && " +
            "!thisLib() && " +
            "!thisCoreLib() && " +
            "!object2source() && " +
            "!java() && " +
            "!javax() && " +
            "!sun() && " +
            "!comsun() && " +
            "!aspectLibParts()")
    public Object aroundMethod(ProceedingJoinPoint pjp) throws Throwable {
        boolean skip = isSkip(pjp);

        TraceElement callerTraceElement = null;
        ProviderResult[] argsProviders = null;
        ProviderResult targetProvider = null;
        boolean isDynamicProxy = false;
        boolean isInvocationHandler = false;
        int targetHashCode = 0;
        long startTime = 0;

        if (!skip) {
            Class joinClass = getJoinClass(pjp);
            String joinClassName = joinClass.getName();
            callerTraceElement = ArgsHashCodeHolder.peek();
            if (allowedPackageForGen(joinClassName) || allowedPackageForGen(callerTraceElement.getClassName())) {
                isDynamicProxy = isDynamicProxy(joinClass);
                isInvocationHandler = isInvocationHandler(joinClass);
                if (!isDynamicProxy && !isInvocationHandler) {
                    startTime = System.nanoTime();
                    if (pjp.getTarget() != null) {
                        // Target object can not be, for example for static calls
                        targetProvider = SG.createFillObjectMethod(pjp.getTarget());
                        targetHashCode = pjp.getTarget().hashCode();
                    }
                    Object[] args = pjp.getArgs();
                    argsProviders = new ProviderResult[args.length];
                    for (int i = 0; i < args.length; i++) {
                        argsProviders[i] = SG.createDataProviderMethod(args[i]);
                    }
                }
            }
            if (!isDynamicProxy && !isInvocationHandler) {
                putArgsHashCode(pjp);
            }
        }

        Object ret = pjp.proceed();

        if (!skip && !isDynamicProxy && !isInvocationHandler) {
            logMethodCall(pjp, callerTraceElement, argsProviders, ret, targetProvider, targetHashCode, startTime);
        }

        return ret;
    }

    private boolean isSkip(JoinPoint joinPoint) {
        String joinMethod = joinPoint.getSignature().getName();
        String threadName = Thread.currentThread().getName();
        return deniedMethod(joinMethod) || deniedMethodSymbol(joinMethod) || excludedThreadName(threadName);
    }

    private void putArgsHashCode(JoinPoint joinPoint) {
        String joinMethod = joinPoint.getSignature().getName();
//        String joinClass = joinPoint.getSourceLocation().getWithinType().getName();
        String joinClass = getJoinClass(joinPoint).getName();
        HashCodeBuilder hashCodeBuilder = new HashCodeBuilder();
        for (Object o : joinPoint.getArgs()) {
            if (o != null) {
                hashCodeBuilder.append(HashCodeBuilder.reflectionHashCode(o));
            } else {
                hashCodeBuilder.append(0);
            }
        }
        ArgsHashCodeHolder.put(new TraceElement(hashCodeBuilder.toHashCode(), joinClass, joinMethod));
    }

    private void logMethodCall(
            JoinPoint joinPoint,
            TraceElement callerTraceElement,
            ProviderResult[] argsProviders,
            Object ret,
            ProviderResult targetProvider,
            int targetHashCode,
            long startTime
    ) {
        int ownArgsHashCode = ArgsHashCodeHolder.pop().getArgsHashCode();
        if (argsProviders != null) {
            // if argsProviders presented, will be save call info, otherwise not allowed method call for save
            String threadName = Thread.currentThread().getName();
            String callerClassName = callerTraceElement.getClassName();
            String callerMethodName = callerTraceElement.getMethodName();
            int callerArgsHashCode = callerTraceElement.getArgsHashCode();
            String callerThreadKey = threadName + callerArgsHashCode;
            TraceAnalyzeDto traceAnalyzeDto = new TraceAnalyzeDto();
            traceAnalyzeDto.setUpLevelElementKey(getMethodKey(callerClassName, callerMethodName, callerThreadKey));
            traceAnalyzeDto.setUpLevelElementClassName(callerTraceElement.getClassName());
            final MethodCallInfo info = createMethodCallInfo(
                    joinPoint,
                    argsProviders,
                    ret,
                    ownArgsHashCode,
                    traceAnalyzeDto,
                    threadName,
                    targetProvider,
                    targetHashCode,
                    startTime);
            dumpMethodCallInfo(info);
        }
    }

    private void dumpMethodCallInfo(final MethodCallInfo info) {
        Runnable callProcessor = new Runnable() {
            @Override
            public void run() {
                List<Class> classHierarchy = getClassHierarchy(info.getClazz());
                MethodCallTraceInfo targetMethod = new MethodCallTraceInfo();
                targetMethod.setClassName(info.getClazz().getName());
                targetMethod.setNearestInstantAbleClass(getNearestInstantAbleClass(info.getClazz()));
                targetMethod.setDeclaringTypeName(info.getDeclaringTypeName());
                targetMethod.setUnitName(info.getMethodName());
                targetMethod.setMemberClass(info.getClazz().isMemberClass());
                targetMethod.setArguments(
                        createGeneratedArgumentList(
                                info.getParameterTypes(),
                                info.getArgTypes(),
                                info.getMethodName(),
                                classHierarchy,
                                info.getArgsProviders()));
                targetMethod.setClassModifiers(info.getClazz().getModifiers());
                targetMethod.setMethodModifiers(info.getMethodModifiers());
                targetMethod.setVoidMethod(info.getRetType().equals(Void.TYPE));
                targetMethod.setClassHierarchy(getClassHierarchyStr(classHierarchy));
                targetMethod.setInterfacesHierarchy(getInterfacesHierarchyStr(info.getClazz()));
                targetMethod.setServiceFields(toServiceProperties(getAllFieldsOfClass(classHierarchy)));
                targetMethod.setMaybeServiceClass(isMaybeServiceClass(classHierarchy));
                targetMethod.setClassHasZeroArgConstructor(hasZeroArgConstructor(info.getClazz(), false));
                targetMethod.setKey(info.getMethodKey());
                targetMethod.setTraceAnalyzeData(info.getTraceAnalyze());
                targetMethod.setTargetObj(createGeneratedArgument(info.getClazz(), info.getTargetProvider(), info.getTargetHashCode()));
                targetMethod.setReturnArg(createGeneratedArgument(info.getRetType(), info.getReturnProvider(), info.getReturnArgHashCode()));
                targetMethod.setStartTime(info.getStartTime());
                targetMethod.setEndTime(info.getEndTime());
                targetMethod.setClassHashCode(info.getTargetHashCode());
                saveObjectDump(
                        targetMethod,
                        info.getMethodKey(),
                        info.getClazz().getName(),
                        info.getTraceAnalyze().getUpLevelElementKey(),
                        targetMethod.getServiceFields().hashCode());
            }
        };
        EXECUTOR_SERVICE.submit(callProcessor);
    }
}
