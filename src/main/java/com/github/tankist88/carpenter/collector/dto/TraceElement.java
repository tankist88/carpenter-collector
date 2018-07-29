package com.github.tankist88.carpenter.collector.dto;

import java.io.Serializable;

public class TraceElement implements Serializable {
    private int argsHashCode;
    private String className;
    private String methodName;

    public TraceElement(int argsHashCode, String className, String methodName) {
        this.argsHashCode = argsHashCode;
        this.className = className;
        this.methodName = methodName;
    }

    public int getArgsHashCode() {
        return argsHashCode;
    }

    public void setArgsHashCode(int argsHashCode) {
        this.argsHashCode = argsHashCode;
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public String getMethodName() {
        return methodName;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }
}
