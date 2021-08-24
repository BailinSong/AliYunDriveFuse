package ooo.reindeer.concurrent;

import java.util.concurrent.*;

/**
 * 线程池服务
 *
 * @ClassName Executors
 * @Author songbailin
 * @Date 2019/12/30 13:58
 * @Version 1.0
 * @Description TODO
 */
public class ExecutorServices {


    /**
     * 线程池执行人建设者
     * 线程池创建者用于创建线程池
     *
     * @return 线程池构造器
     */
    public static ThreadPoolExecutorBuilder threadPoolExecutorBuilder(){
        return new ThreadPoolExecutorBuilder();
    }


    public static class ThreadPoolExecutorBuilder {

        private int corePoolSize=0;
        private int maximumPoolSize=Integer.MAX_VALUE;
        private long keepAliveTime=60L;
        private TimeUnit timeUnit=TimeUnit.SECONDS;
        private BlockingQueue<Runnable> workQueue=new SynchronousQueue<>();
        private ThreadFactory threadFactory=Executors.defaultThreadFactory();
        private RejectedExecutionHandler rejectedHandler= new ThreadPoolExecutor.AbortPolicy();

        public int getCorePoolSize() {
            return corePoolSize;
        }

        private void setCorePoolSize(int corePoolSize) {
            this.corePoolSize = corePoolSize;
        }

        public int getMaximumPoolSize() {
            return maximumPoolSize;
        }

        private void setMaximumPoolSize(int maximumPoolSize) {
            this.maximumPoolSize = maximumPoolSize;
        }

        public long getKeepAliveTime() {
            return keepAliveTime;
        }

        private void setKeepAliveTime(long keepAliveTime) {
            this.keepAliveTime = keepAliveTime;
        }

        public TimeUnit getTimeUnit() {
            return timeUnit;
        }

        private void setTimeUnit(TimeUnit timeUnit) {
            this.timeUnit = timeUnit;
        }

        public BlockingQueue<Runnable> getWorkQueue() {
            return workQueue;
        }

        private void setWorkQueue(BlockingQueue<Runnable> workQueue) {
            this.workQueue = workQueue;
        }

        public ThreadFactory getThreadFactory() {
            return threadFactory;
        }

        private void setThreadFactory(ThreadFactory threadFactory) {
            this.threadFactory = threadFactory;
        }

        public RejectedExecutionHandler getRejectedHandler() {
            return rejectedHandler;
        }

        private void setRejectedHandler(RejectedExecutionHandler rejectedHandler) {
            this.rejectedHandler = rejectedHandler;
        }



        /**
         *  the number of threads to keep in the pool, even
         *    if they are idle, unless {@code allowCoreThreadTimeOut} is set
         * @param size corePoolSize
         * @return
         */
        public ThreadPoolExecutorBuilder corePoolSize(int size){
            corePoolSize=size;
            return this;
        }

        public ThreadPoolExecutorBuilder maximumPoolSize(int size){
            maximumPoolSize=size;
            return this;
        }

        public ThreadPoolExecutorBuilder keepAliveTime(long time,TimeUnit unit){
            keepAliveTime=time;
            timeUnit=unit;
            return this;
        }

        public ThreadPoolExecutorBuilder workQueue(BlockingQueue<Runnable> queue){
            workQueue = queue;
            return this;
        }

        public ThreadPoolExecutorBuilder threadFactory(ThreadFactory factory){
            threadFactory = factory;
            return this;
        }

        public ThreadPoolExecutorBuilder namingThreadFactory(String name){
            threadFactory = new NamingThreadFactory(name);
            return this;
        }

        public ThreadPoolExecutorBuilder rejectedHandler(RejectedExecutionHandler handler){
            rejectedHandler = handler;
            return this;
        }

        public ThreadPoolExecutorBuilder blocking(){
            setRejectedHandler(RejectedStrategy::BLOCKING);
            return this;
        }

        public ExecutorService build(){
            return new ThreadPoolExecutor(
                    corePoolSize,
                    maximumPoolSize,
                    keepAliveTime,
                    timeUnit,
                    workQueue,
                    threadFactory,
                    rejectedHandler);
        }





    }

    /**
     * 主要
     *
     * @param args
     *         arg游戏
     */
    public static void main(String[] args) {
        ExecutorService executor = ExecutorServices.buildNamingFixedBlockingExecutor("test", 5);

    }

    /**
     * 构建命名固定阻塞执行人
     * 创建一个带有名字的阻塞试线程池
     *
     * @param name
     *         线程池的名字
     * @param size
     *         线程池的大小
     *
     * @return 线程池实例
     */
    public static ExecutorService buildNamingFixedBlockingExecutor(String name, int size){
        return ExecutorServices.threadPoolExecutorBuilder()
                .corePoolSize(size)
                .maximumPoolSize(size)
                .keepAliveTime(0L, TimeUnit.MILLISECONDS)
                .workQueue(new LinkedBlockingQueue<>(size))
                .namingThreadFactory(name)
                .blocking()
                .build();
    }

    public static ExecutorService buildNamingCachedThreadPool(String name){
        return ExecutorServices.threadPoolExecutorBuilder()
                .corePoolSize(0)
                .maximumPoolSize(Integer.MAX_VALUE)
                .keepAliveTime(60L, TimeUnit.SECONDS)
                .workQueue(new SynchronousQueue<Runnable>())
                .namingThreadFactory(name)
                .build();
    }
}
