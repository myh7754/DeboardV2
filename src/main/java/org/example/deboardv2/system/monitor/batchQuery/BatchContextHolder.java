package org.example.deboardv2.system.monitor.batchQuery;

public class BatchContextHolder {
    private static final ThreadLocal<BatchContext> CONTEXT = new ThreadLocal<>();

    public static void init(BatchContext ctx) {
        CONTEXT.remove();
        CONTEXT.set(ctx);
    }

    public static BatchContext get() {
        return CONTEXT.get();
    }

    public static void clear() {
        CONTEXT.remove();
    }
}
