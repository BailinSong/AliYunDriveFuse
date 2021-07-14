package ooo.reindeer.storage.net.ali.drive;

import com.aliyun.pds.client.models.GetFileByPathResponse;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @ClassName FileRef
 * @Author songbailin
 * @Date 2021/7/13 09:01
 * @Version 1.0
 * @Description TODO
 */
public class FileRef {
    GetFileByPathResponse self;
    Map<String, FileRef> children = new ConcurrentHashMap<>(0);

    public FileRef find(String path) {

        if (path == null || path.isEmpty() || path.equals("/")) {
            return this;
        }

        if (path.startsWith("/")) {
            path = path.substring(1);
        }


        FileRef temp = this;
        for (String s : path.split("/")) {
            temp = temp.getChildren().get(s);
            if (temp == null) {
                break;
            }
        }
        return temp;
    }

    public Map<String, FileRef> getChildren() {
        return children;
    }

    public void setChildren(Map<String, FileRef> children) {
        this.children = children;
    }

    private String getLastComponent(String path) {
//        System.out.println("AliyunDriveFSv2.getLastComponent( "+"path = [" + path + "]"+" )");

        if (path.isEmpty() || path.equals("/")) {
            return "";
        }
        while (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        if (path.isEmpty()) {
            return "";
        }
        return path.substring(path.lastIndexOf("/") + 1);
    }

    private String getParentComponent(String path) {
        return path.substring(0, path.lastIndexOf("/"));
    }

    public GetFileByPathResponse getSelf() {
        return self;
    }

    public FileRef setSelf(GetFileByPathResponse self) {
        this.self = self;
        return this;
    }

    public void put(String path, GetFileByPathResponse file) {
        find(getParentComponent(path)).getChildren().put(file.getName(), new FileRef().setSelf(file));
    }

    public FileRef remove(String path) {
        return find(getParentComponent(path)).getChildren().remove(getLastComponent(path));
    }
}
