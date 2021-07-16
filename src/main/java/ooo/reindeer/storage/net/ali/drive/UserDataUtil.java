package ooo.reindeer.storage.net.ali.drive;

import jnr.ffi.Platform;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.Properties;
import java.util.function.Supplier;

/**
 * @ClassName UserDataUtil
 * @Author songbailin
 * @Date 2021/7/14 17:04
 * @Version 1.0
 * @Description 用于保存用户数据
 */
public class UserDataUtil {

    public static final Path userDateDir;
    public static final Properties config;

    static {
        try {
            switch (Platform.getNativePlatform().getOS()) {
                case WINDOWS:
                    userDateDir = Paths.get("/Users/" + System.getenv("USER") + "/Library/Application Support/" + AliyunDriveFS.class.getName());
                    break;
                default:
                    userDateDir = Paths.get("/Users/" + System.getenv("USER") + "/Library/Application Support/" + AliyunDriveFS.class.getName());
            }

            Files.createDirectories(userDateDir);
            config = new Properties();

            if (!Files.exists(userDateDir.resolve("config.properties"))) {
                Files.createFile(userDateDir.resolve("config.properties"));
            }

            config.load(Files.newBufferedReader(userDateDir.resolve("config.properties")));
            config.store(Files.newBufferedWriter(userDateDir.resolve("config.properties")), "auto store");

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static String getConfig(String name, String defaultValue) {
        String value = config.getProperty(name, defaultValue);
        if (Objects.equals(value, defaultValue)) {
            setConfig(name, defaultValue);
        }
        return value;
    }

    public static String getConfig(String name, Supplier<String> getter) {
        String value = config.getProperty(name);
        String defaultValue=null;
        if(Objects.isNull(value)||value.isEmpty()){
            defaultValue=getter.get();
        }

        if (defaultValue!=null && !defaultValue.isEmpty()&&!Objects.equals(value,defaultValue)) {
            setConfig(name, defaultValue);
            value=defaultValue;
        }
        return value;
    }

    public static String getConfig(String name) {
        return config.getProperty(name);
    }

    public static Path getUserDateDir() {
        return userDateDir;
    }

    public static void setConfig(String name, String value) {
        config.setProperty(name, value);
        try {
            config.store(Files.newBufferedWriter(userDateDir.resolve("config.properties")), "auto store");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
