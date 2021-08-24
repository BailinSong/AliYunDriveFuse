package ooo.reindeer.storage.net.ali.drive;

import ooo.reindeer.concurrent.ExecutorServices;

import java.io.*;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;

/**
 * @ClassName FixBuffer
 * @Author songbailin
 * @Date 2021/7/6 01:34
 * @Version 1.0
 * @Description TODO
 */
public class FixBuffer implements Flushable {

//    BlockingQueue<Thread> updaters=new LinkedBlockingQueue<>(1);
//
    volatile byte[] raw;
//    volatile int writeIndex = 0;
//    int maxSize;
    volatile int flushNum = 0;

//    volatile int cacheOff = 0;
    String feature;
    public FixBuffer(String feature,int size) {
        this.raw = new byte[size];
//        maxSize = size;
        this.feature=feature;
    }

    @Override
    public void flush() throws IOException {
//        if (writeIndex != 0) {
//
//            flushNum++;
//            byte [] updateData=Arrays.copyOf(raw, writeIndex);
//            writeIndex = 0;
//            final Thread updater=new Thread(){
//                @Override
//                public void run() {
//
//                        flush(updateData, flushNum);
//                        updaters.remove(this);
//
//                }
//            };
//            try {
//                updaters.put(updater);
//                updater.start();
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }
//        }
    }

    public synchronized boolean isUploadCompleted(){
        if (randomAccessFile!=null) {
            try {
                randomAccessFile.seek(0);
                int updateSize=-1;
                while ((updateSize = randomAccessFile.read(raw))!=-1) {
                    flush((updateSize!=raw.length)?Arrays.copyOfRange(raw,0,updateSize):raw, ++flushNum);
                    System.out.println("FixBuffer.isUploadCompleted(): "+updateSize);

                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }


        return true;
    }

    public void flush(byte[] data, int flushNum) {

    }

    public void load(ByteArrayOutputStream buff, long size, int offset, int len) {
    }

    public byte[] read(long size, int offset, int len) {


        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream(len);
        load(byteArrayOutputStream, size, offset, len);
        return byteArrayOutputStream.toByteArray();

//        //在缓存范围内
//        if (!(cacheOff < offset && writeIndex + cacheOff > offset + len)) {
//            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream(maxSize);
//            load(byteArrayOutputStream, size, offset, maxSize);
//            raw = byteArrayOutputStream.toByteArray();
//            writeIndex = raw.length - 1;
//            cacheOff = offset;
//        }
//
//        byte[] part = new byte[len];
//        System.arraycopy(raw, offset - cacheOff, part, 0, len);
//        return part;

    }

    File tempFile;
    Object lock=new Object();
    RandomAccessFile randomAccessFile;
    public synchronized void write(byte[] data,long offset) throws IOException {

        if (tempFile==null) {
            synchronized (lock){
                if (tempFile==null) {
                    tempFile= Files.createTempDirectory("ooo.reindeer.storage.net.ali.drive").resolve(feature).toFile();
                    tempFile.deleteOnExit();
                    randomAccessFile=new RandomAccessFile(tempFile,"rw");
                }

            }
        }

//        int writeable = maxSize - writeIndex;
//
//        if (data.length > writeable) {
//            flush();
//        }
//        System.arraycopy(data, 0, raw, writeIndex, data.length);
//        writeIndex += data.length;
        randomAccessFile.seek(offset);
        randomAccessFile.write(data, 0,data.length);
        System.out.println("FixBuffer.write( "+"data = [" + data.length + "], offset = [" + offset + "]"+" )");


    }

//    public static void main(String[] args) throws IOException {
//        FixBuffer fixBuffer = new FixBuffer(25) {
//            @Override
//            public void flush(byte[] data, int flushNum) {
//                System.out.println("FixBuffer.flush( " + "data = [" + Arrays.toString(data) + "] flushNum = [" + flushNum + "]" + " )");
//            }
//
//
//        };
//
//        byte[] bytes = new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
//
//        for (int i = 0; i < 5; i++) {
//            fixBuffer.write(bytes);
//        }
//        fixBuffer.flush();
//
//    }
}
