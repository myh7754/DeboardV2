package org.example.deboardv2.system.monitor.schedulermonitor;

import org.example.deboardv2.system.monitor.query.RequestContext;
import org.example.deboardv2.system.monitor.query.RequestContextHolder;
import org.springframework.core.task.TaskDecorator;

public class ContextCopyTaskDecorator implements TaskDecorator {
    @Override
    public Runnable decorate(Runnable runnable) {

        RequestContext context = RequestContextHolder.get();

        return() -> {
            try {
                // 비동기(자식) 스레드에 컨텍스트 설정
                if (context != null) {
                    RequestContextHolder.init(context);
                }
                runnable.run();
            } finally {
                // 작업 완료 후 클리어
                RequestContextHolder.clear();
            }
        };
    }
}
