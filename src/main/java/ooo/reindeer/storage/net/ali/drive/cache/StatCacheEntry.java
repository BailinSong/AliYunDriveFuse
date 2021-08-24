package ooo.reindeer.storage.net.ali.drive.cache;

import jnr.ffi.Runtime;
import ru.serce.jnrfuse.struct.FileStat;

import java.util.HashMap;
import java.util.Map;

/**
 * @ClassName StatCacheEntry
 * @Author songbailin
 * @Date 2021/8/17 14:34
 * @Version 1.0
 * @Description TODO
 */
public class StatCacheEntry {
    FileStat stbuf;
    long hit_count;
    long cache_date;
    Map<String, String> meta = new HashMap<>();
    boolean isforce;
    boolean noobjcache;  // Flag: cache is no object for no listing.
    String fileId;
    String parentFileId;
    String driveId;

    public StatCacheEntry() {
        hit_count = 0;
        cache_date = 0;
        isforce = false;
        noobjcache = false;
        stbuf = new FileStat(Runtime.getSystemRuntime());
        meta.clear();
    }

    public String getDriveId() {
        return driveId;
    }

    public StatCacheEntry setDriveId(String driveId) {
        this.driveId = driveId;
        return this;
    }

    public String getFileId() {
        return fileId;
    }

    public StatCacheEntry setFileId(String fileId) {
        this.fileId = fileId;
        return this;
    }

    public Map<String, String> getMeta() {
        return meta;
    }

    public StatCacheEntry setMeta(Map<String, String> meta) {
        this.meta = meta;
        return this;
    }

    public String getParentFileId() {
        return parentFileId;
    }

    public StatCacheEntry setParentFileId(String parentFileId) {
        this.parentFileId = parentFileId;
        return this;
    }

    public FileStat getStbuf() {
        return stbuf;
    }

    public StatCacheEntry setStbuf(FileStat stbuf) {
        this.stbuf = stbuf;
        return this;
    }
}
