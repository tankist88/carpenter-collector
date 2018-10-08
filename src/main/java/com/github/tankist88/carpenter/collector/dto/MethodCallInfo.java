package com.github.tankist88.carpenter.collector.dto;

import com.github.tankist88.carpenter.core.dto.trace.TraceAnalyzeDto;
import com.github.tankist88.object2source.dto.ProviderResult;

public class MethodCallInfo {
    private String methodName;
    private Class clazz;
    private TraceAnalyzeDto traceAnalyze;
    private ProviderResult[] argsProviders;
    private ProviderResult returnProvider;
    private Class retType;
    private String declaringTypeName;
    private Class[] parameterTypes;
    private Class[] argTypes;
    private int methodModifiers;
    private String methodKey;
    private ProviderResult targetProvider;
    private int targetHashCode;
    private int returnArgHashCode;
    private long startTime;
    private long endTime;

    public String getMethodName() {
        return methodName;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    public Class getClazz() {
        return clazz;
    }

    public void setClazz(Class clazz) {
        this.clazz = clazz;
    }

    public TraceAnalyzeDto getTraceAnalyze() {
        return traceAnalyze;
    }

    public void setTraceAnalyze(TraceAnalyzeDto traceAnalyze) {
        this.traceAnalyze = traceAnalyze;
    }

    public ProviderResult[] getArgsProviders() {
        return argsProviders;
    }

    public void setArgsProviders(ProviderResult[] argsProviders) {
        this.argsProviders = argsProviders;
    }

    public ProviderResult getReturnProvider() {
        return returnProvider;
    }

    public void setReturnProvider(ProviderResult returnProvider) {
        this.returnProvider = returnProvider;
    }

    public Class getRetType() {
        return retType;
    }

    public void setRetType(Class retType) {
        this.retType = retType;
    }

    public String getDeclaringTypeName() {
        return declaringTypeName;
    }

    public void setDeclaringTypeName(String declaringTypeName) {
        this.declaringTypeName = declaringTypeName;
    }

    public Class[] getParameterTypes() {
        return parameterTypes;
    }

    public void setParameterTypes(Class[] parameterTypes) {
        this.parameterTypes = parameterTypes;
    }

    public Class[] getArgTypes() {
        return argTypes;
    }

    public void setArgTypes(Class[] argTypes) {
        this.argTypes = argTypes;
    }

    public int getMethodModifiers() {
        return methodModifiers;
    }

    public void setMethodModifiers(int methodModifiers) {
        this.methodModifiers = methodModifiers;
    }

    public String getMethodKey() {
        return methodKey;
    }

    public void setMethodKey(String methodKey) {
        this.methodKey = methodKey;
    }

    public ProviderResult getTargetProvider() {
        return targetProvider;
    }

    public void setTargetProvider(ProviderResult targetProvider) {
        this.targetProvider = targetProvider;
    }

    public int getTargetHashCode() {
        return targetHashCode;
    }

    public void setTargetHashCode(int targetHashCode) {
        this.targetHashCode = targetHashCode;
    }

    public int getReturnArgHashCode() {
        return returnArgHashCode;
    }

    public void setReturnArgHashCode(int returnArgHashCode) {
        this.returnArgHashCode = returnArgHashCode;
    }

    public long getStartTime() {
        return startTime;
    }

    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }

    public long getEndTime() {
        return endTime;
    }

    public void setEndTime(long endTime) {
        this.endTime = endTime;
    }
}
