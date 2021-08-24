package ooo.reindeer.concurrent;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * @ClassName Blocking
 * @Author songbailin
 * @Date 2019/12/30 13:43
 * @Version 1.0
 * @Description TODO
 */
public class RejectedStrategy {


    /**
     * 线程创建时的阻塞策略
     * @param r 被拒绝的执行接口
     * @param executor 线程池
     */
    public static void BLOCKING(Runnable r, ThreadPoolExecutor executor) {
        try {
            executor.getQueue().put(r);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }


}
