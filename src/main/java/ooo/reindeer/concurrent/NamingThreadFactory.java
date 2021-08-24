package ooo.reindeer.concurrent;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 命名线程工厂
 *
 * @ClassName NamingThreadFactory
 * @Author songbailin
 * @Date 2019/12/30 13:58
 * @Version 1.0
 * @Description TODO
 */
public class NamingThreadFactory implements ThreadFactory {
    /**
     * 池数量
     */
    private static final AtomicInteger poolNumber = new AtomicInteger(1);
    /**
     * 集团
     */
    private final ThreadGroup group;
    /**
     * 线程数
     */
    private final AtomicInteger threadNumber = new AtomicInteger(1);
    /**
     * 名称前缀
     */
    private final String namePrefix;

    /**
     * 命名线程工厂
     * 构造函数
     *
     * @param name
     *         创建出来线程名字标示
     */
    NamingThreadFactory(String name) {
        SecurityManager s = System.getSecurityManager();
        group = (s != null) ? s.getThreadGroup() :
                Thread.currentThread().getThreadGroup();
        namePrefix = "pool-" +
                name + "-" + poolNumber.getAndIncrement() +
                "-thread-";
    }

    /**
     * 新线程
     *
     * @param r
     *         r
     *
     * @return {@link Thread}
     */
    @Override
    public Thread newThread(Runnable r) {
        Thread t = new Thread(group, r,
                namePrefix + threadNumber.getAndIncrement(),
                0);
        if (t.isDaemon()) {
            t.setDaemon(false);
        }
        if (t.getPriority() != Thread.NORM_PRIORITY) {
            t.setPriority(Thread.NORM_PRIORITY);
        }
        return t;
    }

    /**
     * 构建
     * 创建一个县城工厂
     *
     * @param name
     *         创建出来线程名字标示
     *
     * @return 县城工厂
     */
    public static ThreadFactory  build(String name){
      return new  NamingThreadFactory(name);
    }
}