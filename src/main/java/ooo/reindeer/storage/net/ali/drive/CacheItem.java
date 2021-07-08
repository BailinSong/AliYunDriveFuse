package ooo.reindeer.storage.net.ali.drive;

/**
 * @ClassName CacheItem
 * @Author songbailin
 * @Date 2021/7/3 01:49
 * @Version 1.0
 * @Description TODO
 */
public class CacheItem<T> {
    T value;
    long timeout;

    public T getValue() {
        return value;
    }

    public CacheItem<T> setValue(T value) {
        this.value = value;
        return this;
    }

    public long getTimeout() {
        return timeout;
    }

    public CacheItem<T> setTimeout(long timeout) {
        this.timeout = timeout;
        return this;
    }
}
