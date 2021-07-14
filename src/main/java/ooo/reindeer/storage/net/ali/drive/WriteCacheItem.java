package ooo.reindeer.storage.net.ali.drive;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * @ClassName WriteCacheItem
 * @Author songbailin
 * @Date 2021/7/6 00:09
 * @Version 1.0
 * @Description TODO
 */
public class WriteCacheItem {
    byte[] data;
    AtomicInteger partNum = new AtomicInteger(0);

    public byte[] getData() {
        return data;
    }

    public WriteCacheItem setData(byte[] data) {
        this.data = data;
        return this;
    }

    public AtomicInteger getPartNum() {
        return partNum;
    }

    public WriteCacheItem setPartNum(AtomicInteger partNum) {
        this.partNum = partNum;
        return this;
    }
}
