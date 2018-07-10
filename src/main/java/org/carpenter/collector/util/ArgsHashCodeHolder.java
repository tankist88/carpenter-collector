package org.carpenter.collector.util;

import java.util.Stack;

public class ArgsHashCodeHolder {
    private static final int MAX_STACK_DEPTH = 100;

    private static final ThreadLocal<Stack<Integer>> threadLocalScope = new ThreadLocal<>();

    private static Stack<Integer> getStack() {
        Stack<Integer> stack = threadLocalScope.get();
        if (stack == null) {
            stack = new Stack<>();
            threadLocalScope.set(stack);
        }
        return stack;
    }

    public static int peek() {
        Stack<Integer> stack = getStack();
        if (stack.size() > 0) {
            return stack.peek();
        } else {
            return 0;
        }
    }

    public static int pop() {
        Stack<Integer> stack = getStack();
        if (stack.size() > 0) {
            return stack.pop();
        } else {
            return 0;
        }
    }

    public static void put(int hashCode) {
        Stack<Integer> stack = getStack();
        if (stack.size() + 1 >= MAX_STACK_DEPTH) {
            stack.removeElementAt(0);
        }
        stack.push(hashCode);
    }
}
