package ooo.reindeer.storage.net.ali.drive;

import java.io.Flushable;
import java.io.IOException;
import java.util.Arrays;
import java.util.Spliterator;

/**
 * @ClassName FixBuffer
 * @Author songbailin
 * @Date 2021/7/6 01:34
 * @Version 1.0
 * @Description TODO
 */
public abstract class FixBuffer implements Flushable {
    byte[] raw;
    int writeIndex=0;
    int maxSize;
    int flushNum =0;
    public FixBuffer(int size) {
        this.raw = new byte[size];
        maxSize=size;
    }

    @Override
    public synchronized void flush() throws IOException {
        if(writeIndex!=0){
            flushNum++;
            flush(Arrays.copyOf(raw,writeIndex),flushNum);
            writeIndex=0;
        }
    }

    public synchronized void write(byte[] data) throws IOException {
        int writeable=maxSize-writeIndex;

        if (data.length>writeable) {
            flush();

        }
        System.arraycopy(data,0,raw,writeIndex,data.length);
        writeIndex+=data.length;

    }

    public abstract void flush(byte[] data,int flushNum);


    public static void main(String[] args) throws IOException {
        FixBuffer fixBuffer=new FixBuffer(25) {
            @Override
            public void flush(byte[] data, int flushNum) {
                System.out.println("FixBuffer.flush( "+"data = [" + Arrays.toString(data) + "] flushNum = [" + flushNum + "]"+" )");
            }
        };

        byte[] bytes =new byte[]{1,2,3,4,5,6,7,8,9,10};

        for(int i=0;i<5;i++){
            fixBuffer.write(bytes);
        }
        fixBuffer.flush();

    }
}
