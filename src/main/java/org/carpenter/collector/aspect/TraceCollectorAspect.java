package org.carpenter.collector.aspect;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.runtime.reflect.AspectMethodSignatureHelper;
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

import static org.carpenter.collector.util.CollectUtil.DumpType.IN;
import static org.carpenter.collector.util.CollectUtil.DumpType.OUT;
import static org.carpenter.collector.util.CollectUtil.*;
import static org.carpenter.core.util.ConvertUtil.toServiceProperties;
import static org.object2source.util.GenerationUtil.*;

@Aspect
public class TraceCollectorAspect {
    private static final SourceGenerator SG = CollectUtil.getSgInstance();

    private static final ExecutorService EXECUTOR_SERVICE = Executors.newFixedThreadPool(50);

    @Pointcut("execution(* org.carpenter.collector..*(..))")
    public void thisLib() {
    }

    @Pointcut("execution(* java..*(..))")
    public void java() {
    }

    @Pointcut("execution(* sun..*(..))")
    public void sun() {
    }

    @Pointcut("execution(* org.aspectj..*(..))")
    public void aspectLibParts() {
    }

    @Pointcut("execution(* *(..))")
    public void callMethod() {
    }

    @Before("callMethod() && !thisLib() && !java() && !sun() && !aspectLibParts()")
    public void enterToMethod(final JoinPoint joinPoint) throws CallerNotFoundException {
        final String joinMethod = joinPoint.getSignature().getName();
        final Class targetClass = getJoinClass(joinPoint);
        final String joinClass = targetClass.getName();

        if(deniedMethod(joinMethod) || deniedMethodSymbol(joinMethod) || deniedPackage(joinClass)) return;

        StackTraceElement[] stackTraceTmp = null;
        TraceAnalyzeDto traceAnalyzeDtoTmp = null;

        final String threadName = Thread.currentThread().getName();

        if( allowedPackageForGeneration(joinClass) ||
            allowedPackageForGeneration(
                    (traceAnalyzeDtoTmp = createTraceAnalyzeData(
                            stackTraceTmp = (new Throwable()).getStackTrace(), joinPoint, threadName)).getUpLevelElementClassName()))
        {
            final StackTraceElement[] stackTrace = stackTraceTmp != null ? stackTraceTmp : (new Throwable()).getStackTrace();
            final TraceAnalyzeDto traceAnalyzeTransport = traceAnalyzeDtoTmp;

            Runnable beforeProcessor = new Runnable() {
                @Override
                public void run() {
                    TraceAnalyzeDto traceAnalyzeDto = getTraceAnalyzeDto(traceAnalyzeTransport, joinPoint, threadName, stackTrace);

                    List<Class> classHierarchy = getClassHierarchy(targetClass);

                    Object[] args = joinPoint.getArgs();

                    MethodCallTraceInfo targetMethod = new MethodCallTraceInfo();
                    targetMethod.setClassName(joinClass);
                    targetMethod.setDeclaringTypeName(joinPoint.getSignature().getDeclaringTypeName());
                    targetMethod.setUnitName(joinMethod);
                    targetMethod.setMemberClass(targetClass.isMemberClass());
                    targetMethod.setArguments(createGeneratedArgumentList(
                            AspectMethodSignatureHelper.getParameterTypes(joinPoint),
                            args, joinMethod, classHierarchy, SG));
                    targetMethod.setClassModifiers(targetClass.getModifiers());
                    targetMethod.setMethodModifiers(joinPoint.getSignature().getModifiers());
                    targetMethod.setVoidMethod(AspectMethodSignatureHelper.getReturnType(joinPoint).equals(Void.TYPE));
                    targetMethod.setClassHierarchy(getClassHierarchyStr(classHierarchy));
                    targetMethod.setInterfacesHierarchy(getInterfacesHierarchyStr(targetClass));
                    targetMethod.setServiceFields(toServiceProperties(getAllFieldsOfClass(classHierarchy)));

                    // технические поля
                    int argsHashCode = Objects.hash(args);
                    targetMethod.setValueArgumentsHashCode(argsHashCode);
                    targetMethod.setKey(createMethodKey(joinPoint, threadName));
                    targetMethod.setTraceAnalyzeData(traceAnalyzeDto);

                    saveObjectDump(IN, targetMethod, joinPoint, threadName, joinClass, argsHashCode, traceAnalyzeDto.getUpLevelElementKey());
                }
            };
            EXECUTOR_SERVICE.submit(beforeProcessor);
        }
    }

    @AfterReturning(pointcut = "callMethod() && !thisLib() && !java() && !sun() && !aspectLibParts()", returning = "ret")
    public void returnFromMethod(final JoinPoint joinPoint, final Object ret) throws CallerNotFoundException {
        final String joinMethod = joinPoint.getSignature().getName();
        final String joinClass = getJoinClass(joinPoint).getName();

        if(deniedMethod(joinMethod) || deniedMethodSymbol(joinMethod) || deniedPackage(joinClass)) return;

        final String threadName = Thread.currentThread().getName();

        StackTraceElement[] stackTraceTmp = null;
        TraceAnalyzeDto traceAnalyzeDtoTmp = null;

        if( allowedPackageForGeneration(joinClass) ||
            allowedPackageForGeneration((traceAnalyzeDtoTmp = createTraceAnalyzeData(stackTraceTmp = (new Throwable()).getStackTrace(), joinPoint, threadName)).getUpLevelElementClassName()))
        {
            final StackTraceElement[] stackTrace = stackTraceTmp != null ? stackTraceTmp : (new Throwable()).getStackTrace();
            final TraceAnalyzeDto traceAnalyzeTransport = traceAnalyzeDtoTmp;

            Runnable afterProcessor = new Runnable() {
                @Override
                public void run() {
                    TraceAnalyzeDto traceAnalyzeDto = getTraceAnalyzeDto(traceAnalyzeTransport, joinPoint, threadName, stackTrace);
                    int argsHashCode = Objects.hash(joinPoint.getArgs());
                    GeneratedArgument returnValue = new GeneratedArgument(
                            AspectMethodSignatureHelper.getReturnType(joinPoint).getName(),
                            SG.createDataProviderMethod(ret));
                    saveObjectDump(OUT, returnValue, joinPoint, threadName, joinClass, argsHashCode, traceAnalyzeDto.getUpLevelElementKey());
                }
            };
            EXECUTOR_SERVICE.submit(afterProcessor);
        }
    }
}
