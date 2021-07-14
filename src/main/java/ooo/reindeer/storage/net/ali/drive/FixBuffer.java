package ooo.reindeer.storage.net.ali.drive;

import java.io.ByteArrayOutputStream;
import java.io.Flushable;
import java.io.IOException;
import java.util.Arrays;

/**
 * @ClassName FixBuffer
 * @Author songbailin
 * @Date 2021/7/6 01:34
 * @Version 1.0
 * @Description TODO
 */
public class FixBuffer implements Flushable {
    volatile byte[] raw;
    volatile int writeIndex = 0;
    int maxSize;
    volatile int flushNum = 0;

    volatile int cacheOff = 0;

    public FixBuffer(int size) {
        this.raw = new byte[size];
        maxSize = size;
    }

    @Override
    public synchronized void flush() throws IOException {
        if (writeIndex != 0) {
            flushNum++;
            flush(Arrays.copyOf(raw, writeIndex), flushNum);
            writeIndex = 0;
        }
    }

    public void flush(byte[] data, int flushNum) {

    }

    public void load(ByteArrayOutputStream buff, long size, int offset, int len) {
    }

    public synchronized byte[] read(long size, int offset, int len) {

        //在缓存范围内
        if (!(cacheOff < offset && writeIndex + cacheOff > offset + len)) {
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream(maxSize);
            load(byteArrayOutputStream, size, offset, maxSize);
            raw = byteArrayOutputStream.toByteArray();
            writeIndex = raw.length - 1;
            cacheOff = offset;
        }

        byte[] part = new byte[len];
        System.arraycopy(raw, offset - cacheOff, part, 0, len);
        return part;

    }

    public synchronized void write(byte[] data) throws IOException {
        int writeable = maxSize - writeIndex;

        if (data.length > writeable) {
            flush();
        }
        System.arraycopy(data, 0, raw, writeIndex, data.length);
        writeIndex += data.length;

    }

    public static void main(String[] args) throws IOException {
        FixBuffer fixBuffer = new FixBuffer(25) {
            @Override
            public void flush(byte[] data, int flushNum) {
                System.out.println("FixBuffer.flush( " + "data = [" + Arrays.toString(data) + "] flushNum = [" + flushNum + "]" + " )");
            }


        };

        byte[] bytes = new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10};

        for (int i = 0; i < 5; i++) {
            fixBuffer.write(bytes);
        }
        fixBuffer.flush();

    }
}
