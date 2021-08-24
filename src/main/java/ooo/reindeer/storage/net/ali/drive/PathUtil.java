package ooo.reindeer.storage.net.ali.drive;

/**
 * @ClassName PathUtil
 * @Author songbailin
 * @Date 2021/8/18 09:48
 * @Version 1.0
 * @Description TODO
 */
public class PathUtil {

    static public String cleanPath(String spath) {
        String path = "";
        if (!(spath.indexOf('\0') < 0)) {
            path = spath.substring(0, spath.indexOf('\0'));
        } else {
            path = spath;
        }
        return path;
    }
}
