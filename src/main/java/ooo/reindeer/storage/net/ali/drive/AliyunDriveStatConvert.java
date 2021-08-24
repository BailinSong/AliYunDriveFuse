package ooo.reindeer.storage.net.ali.drive;

import ooo.reindeer.storage.net.ali.drive.cache.IStatConvert;
import ru.serce.jnrfuse.struct.FileStat;
import ru.serce.jnrfuse.struct.FuseContext;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;

import static java.time.format.DateTimeFormatter.ISO_DATE_TIME;
import static ooo.reindeer.storage.net.ali.drive.AliyunDriveConst.*;

/**
 * @ClassName AliyunDriveStatConvert
 * @Author songbailin
 * @Date 2021/8/17 16:18
 * @Version 1.0
 * @Description TODO
 */
public class AliyunDriveStatConvert implements IStatConvert {

    FuseContext context;

    public AliyunDriveStatConvert(FuseContext context) {
        this.context = context;
    }

    @Override
    public boolean convert_header_to_stat(String path, Map<String, String> meta, FileStat pst, boolean forcedir) {

        try {

            if (!forcedir) {
                switch (meta.get(MATE_TYPE)) {
                    case T_FOLDER:
                        pst.st_mode.set(FileStat.S_IFDIR | 0777);
                        break;
                    case T_FILE:
                    default:
                        String labels = meta.getOrDefault(MATE_LABELS, "");
                        if (labels.contains(T_SYMLINK)) {
                            pst.st_mode.set(FileStat.S_IFLNK | 0777);
                        } else {
                            pst.st_mode.set(FileStat.S_IFREG | 0777);
                        }
                        pst.st_size.set(Long.parseLong(meta.getOrDefault(MATE_SIZE, "0")));
                        pst.st_blocks.set(pst.st_size.longValue() / BLOCK_SIZE + 1);
//                        stat.st_blksize.set(64*1024);

                }
            } else {
                pst.st_mode.set(FileStat.S_IFDIR | 0777);
            }


            if (pst.st_birthtime != null) {
                pst.st_birthtime.tv_sec.set(LocalDateTime.parse(meta.get(MATE_CREATED_AT), ISO_DATE_TIME).toEpochSecond(ZoneOffset.of("+0")));
            }
            pst.st_mtim.tv_sec.set(LocalDateTime.parse(meta.get(MATE_UPDATED_AT), ISO_DATE_TIME).toEpochSecond(ZoneOffset.of("+0")));
            pst.st_uid.set(context.uid.get());
            pst.st_gid.set(context.gid.get());

            return true;
        } catch (Throwable e) {
            e.printStackTrace();
            return false;
        }
    }
}
