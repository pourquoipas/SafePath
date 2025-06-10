/*
 * Copyright (c) 2025 Gianluca Terenziani
 *
 * Questo file è parte di SafePath.
 * SafePath è distribuito sotto i termini della licenza
 * Creative Commons Attribution-NonCommercial-ShareAlike 4.0 International.
 *
 * Dovresti aver ricevuto una copia della licenza insieme a questo progetto.
 * In caso contrario, la puoi trovare su: http://creativecommons.org/licenses/by-nc-sa/4.0/
 */
package net.gnius.safepath;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// =================================================================================
// CLASSE UTILITY SafePath
// =================================================================================
/**
 * Utility class for safely navigating object graphs using a string path.
 * This class provides a powerful {@code invoke} method that parses a path
 * and uses reflection to access fields and methods, with built-in null-safety
 * features.
 *
 * <p><b>Path Syntax (New in this version):</b></p>
 * <ul>
 * <li>{@code .} (Unsafe Access): Accesses a field or method. Throws a
 * {@code NullPointerException} if the preceding object is null.
 * Example: {@code .myField}, {@code .myMethod()}</li>
 * <li>{@code ?.} (Safe Access): Accesses a field or method. If the preceding
 * object is null, the chain short-circuits and evaluates to null
 * without throwing an exception. Example: {@code ?.myField}, {@code ?.myMethod()}</li>
 * <li>{@code myMethod(...)} (Method Invocation): Parentheses indicate a method call.</li>
 * <li>{@code #n} (Positional Parameter): Used inside method parentheses or after a '??'
 * operator to denote a parameter from the varargs list (0-indexed).
 * Example: {@code .calculate(#0, #1)}</li>
 * <li>{@code ??} (Null Coalescing Operator): Provides a default value if the
 * preceding expression is null. The default value is specified using a {@code #n}
 * placeholder. Example: {@code ?.user.getName() ?? #0}</li>
 * </ul>
 */
public final class SafePath {

    // Regex updated to capture positional parameters like #0, #12, etc.
    private static final Pattern PATH_TOKENIZER = Pattern.compile(
            "(\\.)([a-zA-Z_]\\w*)|" +    // Grp 1, 2: Unsafe access (.name)
                    "(\\?\\.)([a-zA-Z_]\\w*)|" + // Grp 3, 4: Safe access (?.name)
                    "(\\()|" +                  // Grp 5: Opening parenthesis
                    "(\\))|" +                  // Grp 6: Closing parenthesis
                    "(#)(\\d+)|" +              // Grp 7, 8: Positional parameter (#0)
                    "(\\?\\?)"                  // Grp 9: Null coalescing operator
    );

    private SafePath() {
        // Utility class, not meant to be instantiated.
    }

    @SuppressWarnings("unchecked")
    public static <T> Optional<T> invoke(Object root, String path, Object... params) {
        Matcher matcher = PATH_TOKENIZER.matcher(path);

        Object currentObject = root;
        int lastMatchEnd = 0;
        String lastOpName = "root";

        while (matcher.find(lastMatchEnd)) {
            if (matcher.start() > lastMatchEnd) {
                String unexpected = path.substring(lastMatchEnd, matcher.start()).trim();
                if (!unexpected.isEmpty()) {
                    throw new SafePathException("Unexpected token '" + unexpected + "' in path");
                }
            }

            if (matcher.group(9) != null) { // Matched '??'
                int coalesceEnd = matcher.end();
                if (!matcher.find(coalesceEnd) || matcher.group(7) == null) {
                    throw new SafePathException("Expected parameter placeholder like '#0' immediately after '??' operator.");
                }

                if (currentObject == null) {
                    int paramIndex = Integer.parseInt(matcher.group(8));
                    if (paramIndex >= params.length) {
                        throw new SafePathException("Parameter index #" + paramIndex + " is out of bounds for the '??' operator.");
                    }
                    currentObject = params[paramIndex];
                }
                lastMatchEnd = matcher.end();
                continue;
            }

            if (matcher.group(7) != null) {
                throw new SafePathException("Unexpected parameter placeholder '" + matcher.group() + "' found outside of method arguments or '??' operator.");
            }

            String op = matcher.group(1) != null ? "." : "?.";
            String name = matcher.group(2) != null ? matcher.group(2) : matcher.group(4);

            String previousOpName = lastOpName;
            lastOpName = name;

            if (currentObject == null) {
                if (op.equals(".")) {
                    throw new NullPointerException("Cannot invoke '" + name + "' because '" + previousOpName + "' is null");
                }
                if (!lookAheadForCoalesce(path, matcher.end())) {
                    return Optional.empty();
                }
                Matcher nextTokenMatcher = PATH_TOKENIZER.matcher(path);
                if (nextTokenMatcher.find(matcher.end())) {
                    lastMatchEnd = nextTokenMatcher.start();
                    continue;
                } else {
                    return Optional.empty();
                }
            }

            try {
                boolean isMethod = isFollowedByParentheses(path, matcher.end());
                if (isMethod) {
                    Object[] methodResult = parseAndInvokeMethod(currentObject, name, path, matcher.end(), params);
                    currentObject = methodResult[0];
                    lastMatchEnd = (int) methodResult[1];
                } else {
                    currentObject = accessField(currentObject, name);
                    lastMatchEnd = matcher.end();
                }
            } catch (Exception e) {
                if (e instanceof SafePathException) throw (SafePathException)e;
                throw new SafePathException("Failed to access '" + name + "' on object " + currentObject.getClass().getSimpleName(), e);
            }
        }

        return Optional.ofNullable((T) currentObject);
    }

    private static Object[] parseAndInvokeMethod(Object target, String methodName, String path, int startPos, Object[] params) throws ReflectiveOperationException {
        Matcher matcher = PATH_TOKENIZER.matcher(path);
        int currentPos = startPos;

        if (!matcher.find(currentPos) || matcher.group(5) == null) {
            throw new SafePathException("Expected '(' after method name '" + methodName + "'");
        }
        currentPos = matcher.end();

        List<Object> methodArgs = new ArrayList<>();
        while (true) {
            String nextTokenPeek = path.substring(currentPos).trim();
            if (nextTokenPeek.startsWith(")")) {
                if (!matcher.find(currentPos) || matcher.group(6) == null) {
                    throw new SafePathException("Mismatched parentheses for method '" + methodName + "'");
                }
                currentPos = matcher.end();
                break;
            }
            if (nextTokenPeek.isEmpty()) {
                throw new SafePathException("Unclosed method call for '" + methodName + "'");
            }
            if (!matcher.find(currentPos) || matcher.group(7) == null) {
                throw new SafePathException("Expected parameter like '#0' or ')' in arguments for method '" + methodName + "'");
            }
            int paramIndex = Integer.parseInt(matcher.group(8));
            if (paramIndex >= params.length) {
                throw new SafePathException("Parameter index #" + paramIndex + " is out of bounds for method '" + methodName + "'");
            }
            methodArgs.add(params[paramIndex]);
            currentPos = matcher.end();
        }

        Object result = invokeMethod(target, methodName, methodArgs);
        return new Object[]{result, currentPos};
    }

    private static Object invokeMethod(Object target, String methodName, List<Object> args) throws ReflectiveOperationException {
        Class<?>[] paramTypes = args.stream().map(SafePath::getclass).toArray(Class<?>[]::new);
        Method method = findMethod(target.getClass(), methodName, paramTypes);
        method.setAccessible(true);
        try {
            return method.invoke(target, args.toArray());
        } catch (InvocationTargetException e) {
            // Unwrap and rethrow the actual exception thrown by the invoked method.
            if (e.getTargetException() instanceof RuntimeException) {
                throw (RuntimeException) e.getTargetException();
            } else {
                // For checked exceptions, wrap them in our custom runtime exception.
                throw new SafePathException("Invoked method '" + methodName + "' threw a checked exception.", e.getTargetException());
            }
        }
    }

    private static Method findMethod(Class<?> targetClass, String methodName, Class<?>[] paramTypes) throws NoSuchMethodException {
        try {
            return targetClass.getMethod(methodName, paramTypes);
        } catch (NoSuchMethodException e) {
            for (Method method : targetClass.getMethods()) {
                if (method.getName().equals(methodName) && method.getParameterCount() == paramTypes.length) {
                    Class<?>[] methodParamTypes = method.getParameterTypes();
                    boolean isMatch = true;
                    for (int i = 0; i < paramTypes.length; i++) {
                        if (paramTypes[i] != null && !methodParamTypes[i].isAssignableFrom(paramTypes[i])) {
                            isMatch = false;
                            break;
                        }
                    }
                    if (isMatch) return method;
                }
            }
            throw e;
        }
    }

    private static Object accessField(Object target, String fieldName) throws ReflectiveOperationException {
        Field field = target.getClass().getField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }

    private static boolean isFollowedByParentheses(String path, int fromIndex) {
        if (fromIndex >= path.length()) return false;
        return path.substring(fromIndex).trim().startsWith("(");
    }

    private static boolean lookAheadForCoalesce(String path, int fromIndex) {
        Matcher lookAheadMatcher = PATH_TOKENIZER.matcher(path);
        if (lookAheadMatcher.find(fromIndex)) {
            do {
                if (lookAheadMatcher.group(9) != null) { // Found '??'
                    return true;
                }
            } while (lookAheadMatcher.find());
        }
        return false;
    }

    private static Class<?> getclass(Object obj) {
        if (obj == null) return Object.class;
        if (obj instanceof Integer) return int.class;
        if (obj instanceof Long) return long.class;
        if (obj instanceof Double) return double.class;
        if (obj instanceof Float) return float.class;
        if (obj instanceof Boolean) return boolean.class;
        if (obj instanceof Character) return char.class;
        if (obj instanceof Byte) return byte.class;
        if (obj instanceof Short) return short.class;
        return obj.getClass();
    }

    public static class SafePathException extends RuntimeException {
        public SafePathException(String message) {
            super(message);
        }
        public SafePathException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

