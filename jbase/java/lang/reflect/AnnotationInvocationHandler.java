package java.lang.reflect;

// Backs the java.lang.reflect.Proxy returned by Class.getAnnotation: element
// accessor methods look up their decoded value by name in the pairs array
// ([name0, value0, name1, value1, ...]) supplied by the VM.
class AnnotationInvocationHandler implements InvocationHandler {
    private Object[] pairs;

    public Object invoke(Object proxy, Method method, Object[] args) {
        String n = method.getName();
        for (int i = 0; i < pairs.length; i += 2) {
            if (pairs[i].equals(n)) {
                return pairs[i + 1];
            }
        }
        return null;
    }
}
