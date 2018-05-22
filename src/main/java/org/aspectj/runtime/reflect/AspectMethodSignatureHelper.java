package org.aspectj.runtime.reflect;

import org.aspectj.lang.JoinPoint;

public class AspectMethodSignatureHelper {
    public static Class getReturnType(JoinPoint joinPoint) {
        return ((MethodSignatureImpl)joinPoint.getSignature()).getReturnType();
    }

    public static Class[] getParameterTypes(JoinPoint joinPoint) {
        return ((MethodSignatureImpl)joinPoint.getSignature()).getParameterTypes();
    }
}
