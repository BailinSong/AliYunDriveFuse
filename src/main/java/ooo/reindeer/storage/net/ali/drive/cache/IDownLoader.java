package ooo.reindeer.storage.net.ali.drive.cache;

/**
 * @ClassName ILoader
 * @Author songbailin
 * @Date 2021/8/18 14:24
 * @Version 1.0
 * @Description TODO
 */
public interface IDownLoader {
    byte[] load(String path, long offset, long bytes);
}
