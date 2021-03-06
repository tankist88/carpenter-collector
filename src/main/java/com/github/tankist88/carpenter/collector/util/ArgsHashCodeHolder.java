package com.github.tankist88.carpenter.collector.util;

import com.github.tankist88.carpenter.collector.dto.TraceElement;

import java.util.Stack;

public class ArgsHashCodeHolder {
    private static final int MAX_STACK_DEPTH = 150;

    private static final ThreadLocal<Stack<TraceElement>> threadLocalScope = new ThreadLocal<Stack<TraceElement>>();

    private static Stack<TraceElement> getStack() {
        Stack<TraceElement> stack = threadLocalScope.get();
        if (stack == null) {
            stack = new Stack<TraceElement>();
            threadLocalScope.set(stack);
        }
        return stack;
    }

    public static TraceElement peek() {
        Stack<TraceElement> stack = getStack();
        if (stack.size() > 0) {
            return stack.peek();
        } else {
            return new TraceElement(0, null, null);
        }
    }

    public static TraceElement pop() {
        Stack<TraceElement> stack = getStack();
        if (stack.size() > 0) {
            return stack.pop();
        } else {
            return new TraceElement(0, null, null);
        }
    }

    public static void put(TraceElement hashCode) {
        Stack<TraceElement> stack = getStack();
        if (stack.size() + 1 >= MAX_STACK_DEPTH) {
            stack.removeElementAt(0);
        }
        stack.push(hashCode);
    }
}
