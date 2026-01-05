package org.example.deboardv2.system.monitor.query;

public class RequestContextHolder {
    private static final ThreadLocal<RequestContext> CONTEXT = new ThreadLocal<>();
    public static void init(RequestContext ctx) {
        CONTEXT.remove();
        CONTEXT.set(ctx);
    }
    public static RequestContext get() {
        return CONTEXT.get();
    }

    public static void clear() {
        CONTEXT.remove();
    }
}
