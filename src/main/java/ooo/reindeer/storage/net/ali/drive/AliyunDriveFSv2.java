package ooo.reindeer.storage.net.ali.drive;


import com.aliyun.pds.client.models.*;
import com.aliyun.tea.TeaException;
import jnr.ffi.Platform;
import jnr.ffi.Pointer;
import jnr.ffi.types.mode_t;
import jnr.ffi.types.off_t;
import jnr.ffi.types.size_t;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.serce.jnrfuse.ErrorCodes;
import ru.serce.jnrfuse.FuseFillDir;
import ru.serce.jnrfuse.FuseStubFS;
import ru.serce.jnrfuse.struct.FileStat;
import ru.serce.jnrfuse.struct.FuseFileInfo;
import ru.serce.jnrfuse.struct.Statvfs;
import ru.serce.jnrfuse.struct.Timespec;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import static java.time.format.DateTimeFormatter.ISO_DATE_TIME;

/**
 * @ClassName AliyunDriveFS
 * @Author songbailin
 * @Date 2021/6/28 16:15
 * @Version 1.0
 * @Description TODO
 */
public class AliyunDriveFSv2 extends FuseStubFS {


    public static final String S_AVAILABLE = "available";
    public static final String S_UPLOADING = "uploading*";
    public static final String T_SYMLINK = "symlink";
    public static final String T_FILE = "file";
    public static final String T_FOLDER = "folder";
    static List<String> exclude = Arrays.asList(new String[]{
//            "._"
//            ".DS_Store"
//            ,".hidden"
    });
    Logger logger = LoggerFactory.getLogger(AliyunDriveFSv2.class);
    Config config = new Config();
    Path tempDir = Files.createTempDirectory("AliyunDriverFS");
    int uploadPartSize = 5 * 1024 * 1024;
    int readPartSize = 5 * 1024 * 1024;
    Map<String, CacheItem<GetFileByPathResponse>> cache = new ConcurrentHashMap<>();
    Map<String, byte[]> frist4kCache = new ConcurrentHashMap<>();
    Map<Object, FixBuffer> writeCache = new ConcurrentHashMap<>();
    Map<Object, FixBuffer> readCache = new ConcurrentHashMap<>();
    GetFileByPathResponse rootFile = new GetFileByPathResponse();
    CacheItem<GetFileByPathResponse> DEFAULT_CACHE_ITEM = new CacheItem<GetFileByPathResponse>().setTimeout(-1);
    long lastUpdateStatFS = 0;
    long totalSize = 0;
    long freeSize = 0;
    int BLOCK_SIZE = 4 * 1024;
    AtomicLong fh = new AtomicLong(0);
    private final DriveClient driveClient;

    public AliyunDriveFSv2(String rt) throws Exception {

        config.protocol = "https";
        config.refreshToken = rt;
        driveClient = new DriveClient(config);
        AccountTokenRequest tokenRequest = new AccountTokenRequest();
        tokenRequest.setRefreshToken(config.refreshToken);
        tokenRequest.setGrantType("refresh_token");
        AccountTokenModel tokenResponse;
        tokenResponse = driveClient.accountToken(tokenRequest);

        GetUserRequest userRequest = new GetUserRequest();
        userRequest.setUserId(tokenResponse.getBody().getUserId());
        GetUserModel getUserModel = driveClient.getUser(userRequest);
        driveClient.setDriveId(tokenResponse.body.getDefaultDriveId());
        rootFile.setType("folder");
        rootFile.setName("root");
        rootFile.setFileId("root");
        rootFile.setStatus("available");
        rootFile.setDriveId(tokenResponse.body.getDefaultDriveId());
        rootFile.setCreatedAt(ISO_DATE_TIME.format(LocalDateTime.ofEpochSecond(getUserModel.body.getCreatedAt() / 1000, 0, ZoneOffset.UTC)));
        rootFile.setUpdatedAt(ISO_DATE_TIME.format(LocalDateTime.ofEpochSecond(getUserModel.body.getUpdatedAt() / 1000, 0, ZoneOffset.UTC)));
    }

    @Override
    public int create(String path, @mode_t long mode, FuseFileInfo fi) {
        fi.fh.set(fh.incrementAndGet() % 999999999999999999L);



        if (getPath(path) != null) {
            System.out.println("AliyunDriveFSv2.create[" + fi.fh.get() + "]( " + "path = [" + path + "], mode = [" + mode + "]" + " ) EEXIST");
            return -ErrorCodes.EEXIST();
        }


        GetFileByPathResponse parent = getParentPath(path, S_AVAILABLE, null);


        if (!parent.getType().equalsIgnoreCase("file")) {
            String lastComponent = getLastComponent(path);

            if (lastComponent.startsWith("._") || getLastComponent(path).startsWith(".fuse_hidden")) {

                GetFileByPathResponse file = new GetFileByPathResponse();

                file
                        .setName(getLastComponent(lastComponent))
                        .setFileId(UUID.randomUUID().toString())
                        .setSize(0L)
                        .setStatus(null)
                        .setCreatedAt(ISO_DATE_TIME.format(LocalDateTime.now()))
                        .setUpdatedAt(ISO_DATE_TIME.format(LocalDateTime.now()))
                        .setType("file");
                frist4kCache.getOrDefault(file.getFileId(), new byte[0]);
                putCache(path, file);
                return 0;
            }

            try {
                CreateFileRequest createFileRequest = new CreateFileRequest();
                createFileRequest.setType("file");
                createFileRequest.setDriveId(driveClient.driveId);
                createFileRequest.setName(lastComponent);
                createFileRequest.setParentFileId(parent.getFileId());
                CreateFileModel reponse = driveClient.createFile(createFileRequest);
                GetFileByPathResponse file = new GetFileByPathResponse();

                file
                        .setName(getLastComponent(lastComponent))
                        .setFileId(reponse.getBody().getFileId())
                        .setParentFileId(reponse.getBody().getParentFileId())
                        .setDriveId(reponse.getBody().getDriveId())
                        .setDomainId(reponse.getBody().getDomainId())
                        .setSize(0L)
                        .setStatus(reponse.getBody().getStatus())
                        .setCreatedAt(ISO_DATE_TIME.format(LocalDateTime.now()))
                        .setUpdatedAt(ISO_DATE_TIME.format(LocalDateTime.now()))
                        .setType(reponse.getBody().getType())
                        .setUploadId(reponse.getBody().uploadId);
                putCache(path, file);
//                writeCache.put(reponse.getBody().getFileId(), new byte[uploadPartSize]);

            } catch (Exception e) {
                e.printStackTrace();
            }
            return 0;
        }
        return -ErrorCodes.ENOENT();
    }

    public GetFileByPathResponse find(String path) {
        String tempPath = path;
        while (tempPath.startsWith("/")) {
            tempPath = tempPath.substring(1);
        }
        if (tempPath.isEmpty()) {
            try {
                return rootFile;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        GetFileByPathRequest request = new GetFileByPathRequest();
        request.setDriveId(driveClient.driveId);
        request.setFilePath(path);
        try {
            GetFileByPathModel model = driveClient.getFileByPath(request);
            return model.getBody();
        } catch (TeaException ignored) {

        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public int flush(String path, FuseFileInfo fi) {
        System.out.println("AliyunDriveFSv2.flush[" + fi.fh.get() + "]( " + "path = [" + path + "]" + " )");
        readCache.remove(fi.fh.get());
        return super.flush(path, fi);
    }

    @Override
    public int fsync(String path, int isdatasync, FuseFileInfo fi) {
        System.out.println("AliyunDriveFSv2.fsync( "+"path = [" + path + "], isdatasync = [" + isdatasync + "], fi = [" + fi + "]"+" )");
        return super.fsync(path, isdatasync, fi);
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

    private GetFileByPathResponse getParentPath(String path) {
        return getPath(getParentComponent(path));
    }

    private GetFileByPathResponse getParentPath(String path, String... status) {
        return getPath(getParentComponent(path), status);
    }

    private GetFileByPathResponse getPath(String path, String... status) {

        if (isExclude(path)) {
            return null;
        }

        GetFileByPathResponse value;
        CacheItem<GetFileByPathResponse> item = cache.getOrDefault(path, DEFAULT_CACHE_ITEM);

        if (item.getTimeout() != -1 && Objects.nonNull(item.value)) {
            value = item.getValue();
        } else {
            value = find(path);
            if (Objects.nonNull(value)) {
                putCache(path, value);
            } else {
                value = item.getValue();
            }
        }


        if (Objects.nonNull(value)) {
            if (Objects.nonNull(status)) {
                for (String s : status) {
                    if (Objects.equals(s, value.getStatus())) {
//                        System.out.println("AliyunDriveFSv2.getPath( " + "path = [" + path + "], status = [" + Arrays.toString(status) + "]" + " ) return [" + value + "]");
                        return value;
                    }
                }
            } else if (Objects.equals(status, value.getStatus())) {
//                System.out.println("AliyunDriveFSv2.getPath( " + "path = [" + path + "], status = [" + Arrays.toString(status) + "]" + " ) return [" + value + "]");
                return value;
            }
            value = null;
        } else {
            value = null;
        }

        System.out.println("AliyunDriveFSv2.getPath( " + "path = [" + path + "], status = [" + Arrays.toString(status) + "]" + " ) return [" + value + "]");
        return value;
    }

    private GetFileByPathResponse getPath(String path) {


        return getPath(path, S_AVAILABLE);
    }

    @Override
    public int getattr(String path, FileStat stat) {

        try {

            GetFileByPathResponse file = getPath(path, S_AVAILABLE, null);
            if (file != null) {

                switch (file.getType()) {
                    case T_FOLDER:
                        stat.st_mode.set(FileStat.S_IFDIR | 0777);
                        break;
                    case T_FILE:
                    default:
                        if (!Objects.isNull(file.getLabels()) && file.getLabels().contains(T_SYMLINK)) {
                            stat.st_mode.set(FileStat.S_IFLNK | 0777);
                        } else {
                            stat.st_mode.set(FileStat.S_IFREG | 0777);
                        }
                        stat.st_size.set(file.getSize());
                }

                stat.st_birthtime.tv_sec.set(LocalDateTime.parse(file.getCreatedAt(), ISO_DATE_TIME).toEpochSecond(ZoneOffset.of("+0")));
                stat.st_mtim.tv_sec.set(LocalDateTime.parse(file.getUpdatedAt(), ISO_DATE_TIME).toEpochSecond(ZoneOffset.of("+0")));
                stat.st_uid.set(getContext().uid.get());
                stat.st_gid.set(getContext().gid.get());
                return 0;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.out.println("AliyunDriveFSv2.getattr( "+"path = [" + path + "], stat = [error]"+" )");

        return -ErrorCodes.ENOENT();
    }

    boolean isExclude(String path) {
        for (String s : exclude) {
            if (getLastComponent(path).startsWith(s)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int link(String oldpath, String newpath) {
        System.out.println("AliyunDriveFSv2.link( " + "oldpath = [" + oldpath + "], newpath = [" + newpath + "]" + " )");

        return 0;
    }

    @Override
    public int mkdir(String path, @mode_t long mode) {
        System.out.println("AliyunDriveFSv2.mkdir( " + "path = [" + path + "], mode = [" + mode + "]" + " )");

        try {
            if (getPath(path, S_AVAILABLE, null) != null) {
                return -ErrorCodes.EEXIST();
            }


            GetFileByPathResponse parent = getParentPath(path, S_AVAILABLE, null);


            CreateFileRequest request = new CreateFileRequest();
            request.setDriveId(driveClient.getDriveId());
            request.setName(getLastComponent(path));
            request.setParentFileId(parent.getFileId());
            request.setType("folder");
            CreateFileModel reponse = driveClient.createFile(request);
            GetFileByPathResponse file = new GetFileByPathResponse()
                    .setName(getLastComponent(path))
                    .setFileId(reponse.getBody().getFileId())
                    .setParentFileId(reponse.getBody().getParentFileId())
                    .setDriveId(reponse.getBody().getDriveId())
                    .setDomainId(reponse.getBody().getDomainId())
                    .setSize(0L)
                    .setStatus(null)
                    .setStatus(reponse.getBody().getStatus())
                    .setCreatedAt(ISO_DATE_TIME.format(LocalDateTime.now()))
                    .setUpdatedAt(ISO_DATE_TIME.format(LocalDateTime.now()))
                    .setType(reponse.getBody().getType());
            putCache(path, file);

            return 0;
        } catch (Exception e) {
            e.printStackTrace();
        }

        return -ErrorCodes.ENOENT();

    }

    @Override
    public int open(String path, FuseFileInfo fi) {

        if (fi.fh.get() == 0) {
            fi.fh.set(fh.incrementAndGet() % 999999999999999999L);
        }

        System.out.println("AliyunDriveFSv2.open[" + fi.fh.get() + "]( " + "path = [" + path + "]" + " )");

        return 0;
    }

    private void putCache(String path, GetFileByPathResponse file) {
        if (isExclude(path)) {
            return;
        }
//            System.out.println("AliyunDriveFSv2.putCache( " + "createDir = [" + createDir + "], file = [" + file + "]" + " )");
//            new Throwable().printStackTrace(System.out);

        cache.put(path, new CacheItem<GetFileByPathResponse>().setValue(file).setTimeout(System.currentTimeMillis()));
    }

    @Override
    public int read(String path, Pointer buf, @size_t long size, @off_t long offset, FuseFileInfo fi) {
//        System.out.println("AliyunDriveFS.read[" + fi.fh.get() + "]( " + "path = [" + path + "], buf = [" + buf + "], size = [" + size + "], offset = [" + offset + "]" + " )");
        int bytesToRead = 0;
        try {
            GetFileByPathResponse file = getPath(path, S_AVAILABLE);

            if (file == null) {
                return -ErrorCodes.ENOENT();
            }
            if (!(file.getType().equalsIgnoreCase("file"))) {
                return -ErrorCodes.EISDIR();
            }

            bytesToRead = (int) Math.min(file.size - offset, size);

            if (getLastComponent(path).startsWith("._") || getLastComponent(path).startsWith(".fuse_hidden")) {
                buf.put(0, frist4kCache.get(file.getFileId()), 0, bytesToRead);
                return bytesToRead;
            }


            byte[] bytes = null;


//            if (size <= 4 * 1024 && offset == 0) {
//                bytes = frist4kCache.get(file.getFileId());
//            }
//            if (bytes == null || offset==0 &&bytes.length < 4 * 1024) {
//                bytes = driveClient.downloadPart(file.driveId, file.fileId, file.getSize().intValue(), (int) offset, bytesToRead);
//                frist4kCache.put(file.getFileId(), bytes);
//            } else {
            FixBuffer fixBuffer = readCache.getOrDefault(fi.fh.get(), new FixBuffer(readPartSize) {
                @Override
                public void load(ByteArrayOutputStream buff, int size, int offset, int len) {
                    try {
                        buff.write(driveClient.downloadPart(file.driveId, file.fileId, size, offset, len));
                    } catch (Exception e) {
                        new RuntimeException(e);
                    }
                }
            });
            readCache.put(fi.fh.get(), fixBuffer);
            bytes = fixBuffer.read(file.getSize().intValue(), (int) offset, bytesToRead);
//            }


            buf.put(0, bytes, 0, bytesToRead);

        } catch (Exception e) {
            e.printStackTrace();
        }
        return bytesToRead;
    }

    @Override
    public int readdir(String path, Pointer buf, FuseFillDir filter, @off_t long offset, FuseFileInfo fi) {
//        System.out.println("AliyunDriveFSv2.readdir( " + "path = [" + path + "], buf = [" + buf + "], filter = [" + filter + "], offset = [" + offset + "]" + " )");

        try {
            GetFileByPathResponse file = getPath(path, S_AVAILABLE, null);
//            file =(file==null)?getPath(path,null):file;
            if (file == null) {
                return -ErrorCodes.ENOENT();
            }
            if (file.getType().equalsIgnoreCase("file")) {
                return -ErrorCodes.ENOTDIR();
            }

            filter.apply(buf, ".", null, 0);
            filter.apply(buf, "..", null, 0);

            String marker = "";
            do {
                ListFileRequest listFileRequest = new ListFileRequest();
                listFileRequest.setDriveId(file.getDriveId());
                listFileRequest.setMarker(marker);
                listFileRequest.setParentFileId(file.getFileId());

                ListFileModel listFileResponse = null;

                listFileResponse = driveClient.listFile(listFileRequest);
                marker = listFileResponse.getBody().getNextMarker();
                for (BaseCCPFileResponse item : listFileResponse.getBody().getItems()) {
//                    System.out.println("item.getName() = " + item.getName());
                    if (item.getType().equalsIgnoreCase("file")) {
                        filter.apply(buf, item.getName(), null, 0);
                    } else {
                        filter.apply(buf, item.getName(), null, 0);
                    }
                }
            } while (!marker.isEmpty());
        } catch (Exception e) {
            e.printStackTrace();
        }

        return 0;
    }

    @Override
    public int readlink(String path, Pointer buf, long size) {
//        System.out.println("AliyunDriveFSv2.readlink( " + "path = [" + path + "], buf = [" + buf + "], size = [" + size + "]" + " )");
        GetFileByPathResponse p = getPath(path);
        try {
            byte[] newPath = driveClient.downloadPart(p.getDriveId(), p.getFileId(), p.getSize().intValue(), 0, p.getSize().intValue());
            buf.put(0, newPath, 0, newPath.length);
            buf.put(newPath.length, new byte[]{0}, 0, 1);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0;
    }

    @Override
    public int release(String path, FuseFileInfo fi) {

        long begin = System.currentTimeMillis();
        if (getLastComponent(path).startsWith("._") || getLastComponent(path).startsWith(".fuse_hidden")) {
            return 0;
        }
        try {
            GetFileByPathResponse fileInfo = getPath(path, null, S_UPLOADING);
            if (fileInfo == null) {
                return 0;
            }
            FixBuffer buffer = writeCache.remove(fi.fh.get());


            if (buffer != null) {
                buffer.flush();

//            AtomicInteger partNum = new AtomicInteger();
//            if (fileInfo != null && tempDir.resolve(fileInfo.fileId).toFile().exists()) {
//                try (RandomAccessFile file = new RandomAccessFile(tempDir.resolve(fileInfo.fileId).toFile(), "r")) {
//
//                    byte[] tempDate = new byte[uploadPartSize];
//                    int len = -1;
//                    while ((len = file.read(tempDate)) != -1) {
//                        upload(fileInfo.getFileId(), fileInfo.getUploadId(), partNum.incrementAndGet(), tempDate, len);
//                    }
//                }

                CompleteFileRequest CompleteFileRequest = new CompleteFileRequest();
                CompleteFileRequest.driveId = fileInfo.getDriveId();
                CompleteFileRequest.fileId = fileInfo.fileId;
                CompleteFileRequest.uploadId = fileInfo.uploadId;
                CompleteFileModel CompleteFileResponse = driveClient.completeFile(CompleteFileRequest);
                setUpdateCache(path);

            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.out.println("AliyunDriveFSv2.release[" + fi.fh.get() + "]( " + "path = [" + path + "]" + " ) use=" + (System.currentTimeMillis() - begin));

        return 0;
    }

    @Override
    public int rename(String path, String newName) {
        try {
            System.out.println("AliyunDriveFS.rename( " + "path = [" + path + "], newName = [" + newName + "]" + " )");

            GetFileByPathResponse p = getPath(path, S_AVAILABLE, null);
            if (p == null) {
                return -ErrorCodes.ENOENT();
            }
            GetFileByPathResponse newParent = getParentPath(newName);
            newParent = (newParent == null) ? getParentPath(newName, null) : newParent;
            if (newParent == null) {
                System.out.println("newParent = " + newParent + ":" + getParentComponent(path));
                return -ErrorCodes.ENOENT();
            }

            if (getLastComponent(newName).startsWith(".fuse_hidden")) {
                cache.put(newName, cache.remove(path));

                return 0;
            }

            String name = getLastComponent(newName);
            MoveFileRequest request = new MoveFileRequest();
            request.setDriveId(driveClient.driveId);
            request.setToDriveId(driveClient.driveId);
            request.setNewName(name);
            request.setFileId(p.fileId);
            request.setToParentFileId(newParent.fileId);


            MoveFileModel fileResponse = driveClient.moveFile(request);
//            setUpdateCache(path);
            cache.remove(path);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return 0;
    }

    @Override
    public int rmdir(String path) {
        return unlink(path);
    }

    private void setUpdateCache(String path) {
        if (isExclude(path)) {
            return;
        }
//            System.out.println("AliyunDriveFSv2.putCache( " + "createDir = [" + createDir + "], file = [" + file + "]" + " )");
//            new Throwable().printStackTrace(System.out);
        CacheItem<GetFileByPathResponse> item = cache.get(path);
        if (item != null) {
            item.setTimeout(-1);
        }
    }

    @Override
    public int statfs(String path, Statvfs stbuf) {


        if ("/".equals(path)) {
            try {

                if (lastUpdateStatFS < System.currentTimeMillis() - 30000) {

                    lastUpdateStatFS = System.currentTimeMillis();

                    GetDriveRequest getDriveRequest = new GetDriveRequest();
                    getDriveRequest.setDriveId(driveClient.getDriveId());


                    GetDriveModel getDriveModel = driveClient.getDrive(getDriveRequest);
//                    stbuf.f_blocks.set(1024 * 1024); // total data blocks in file system
//                    stbuf.f_frsize.set(1024);        // fs block size
//                    stbuf.f_bfree.set(1024 * 1024);  // free blocks in fs


                    totalSize = getDriveModel.getBody().getTotalSize() / BLOCK_SIZE;
                    freeSize = (getDriveModel.getBody().getTotalSize() - getDriveModel.getBody().getUsedSize()) / BLOCK_SIZE;
                }

                stbuf.f_blocks.set(totalSize); // total data blocks in file system

//                stbuf.f_frsize.set(blockSize);        // fs block size
                stbuf.f_bsize.set(BLOCK_SIZE);

                stbuf.f_bfree.set(freeSize);  // free blocks in fs
                stbuf.f_bavail.set(freeSize);

            } catch (Exception e) {
                e.printStackTrace();
            }


        }
        return super.statfs(path, stbuf);
    }

    @Override
    public int symlink(String oldpath, String newpath) {
        System.out.println("AliyunDriveFSv2.symlink( " + "oldpath = [" + oldpath + "], newpath = [" + newpath + "]" + " )");
        if (getPath(newpath) != null) {
            return -ErrorCodes.EEXIST();
        }

        GetFileByPathResponse parent = getParentPath(newpath, S_AVAILABLE, null);

        String lastComponent = getLastComponent(newpath);
        try {
            CreateFileRequest createFileRequest = new CreateFileRequest();
            createFileRequest.setType("file");
            createFileRequest.setLabels(Collections.singletonList(T_SYMLINK));
            createFileRequest.setDriveId(driveClient.driveId);
            createFileRequest.setName(lastComponent);
            createFileRequest.setParentFileId(parent.getFileId());
            CreateFileModel reponse = driveClient.createFile(createFileRequest);

            GetFileByPathResponse file = new GetFileByPathResponse();
            byte[] data = (oldpath).getBytes(StandardCharsets.UTF_8);
            file
                    .setName(getLastComponent(lastComponent))
                    .setFileId(reponse.getBody().getFileId())
                    .setParentFileId(reponse.getBody().getParentFileId())
                    .setDriveId(reponse.getBody().getDriveId())
                    .setDomainId(reponse.getBody().getDomainId())
                    .setSize((long) data.length)
                    .setLabels(Collections.singletonList(T_SYMLINK))
                    .setStatus(reponse.getBody().getStatus())
                    .setCreatedAt(ISO_DATE_TIME.format(LocalDateTime.now()))
                    .setUpdatedAt(ISO_DATE_TIME.format(LocalDateTime.now()))
                    .setType(reponse.getBody().getType())
                    .setUploadId(reponse.getBody().uploadId);
            putCache(newpath, file);


            upload(reponse.getBody().getFileId(), reponse.getBody().getUploadId(), 1, data, data.length);

            CompleteFileRequest CompleteFileRequest = new CompleteFileRequest();
            CompleteFileRequest.driveId = reponse.getBody().getDriveId();
            CompleteFileRequest.fileId = reponse.getBody().getFileId();
            CompleteFileRequest.uploadId = reponse.getBody().getUploadId();
            CompleteFileModel CompleteFileResponse = driveClient.completeFile(CompleteFileRequest);
            setUpdateCache(newpath);
            return 0;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return -ErrorCodes.ENOENT();
    }

    @Override
    public int truncate(String path, long offset) {
        System.out.println("AliyunDriveFSv2.truncate( " + "path = [" + path + "], offset = [" + offset + "]" + " )");

        if (getPath(path, S_AVAILABLE, null) == null) {
            return -ErrorCodes.ENOENT();
        } else {
            unlink(path);
        }

        GetFileByPathResponse parent = getParentPath(path, S_AVAILABLE, null);


        if (!parent.getType().equalsIgnoreCase("file")) {
            String lastComponent = getLastComponent(path);

            if (lastComponent.startsWith("._") || getLastComponent(path).startsWith(".fuse_hidden")) {
                frist4kCache.getOrDefault(path, new byte[4096]);
                GetFileByPathResponse file = new GetFileByPathResponse();

                file
                        .setName(getLastComponent(lastComponent))
                        .setFileId(UUID.randomUUID().toString())
                        .setSize(4096L)
                        .setStatus(S_AVAILABLE)
                        .setCreatedAt(ISO_DATE_TIME.format(LocalDateTime.now()))
                        .setUpdatedAt(ISO_DATE_TIME.format(LocalDateTime.now()))
                        .setType("file");
                putCache(file.getFileId(), file);
                return 0;
            }


            try {
                CreateFileRequest createFileRequest = new CreateFileRequest();
                createFileRequest.setType("file");
                createFileRequest.setDriveId(driveClient.driveId);
                createFileRequest.setName(lastComponent);
                createFileRequest.setParentFileId(parent.getFileId());
                CreateFileModel reponse = driveClient.createFile(createFileRequest);
                GetFileByPathResponse file = new GetFileByPathResponse();

                file
                        .setName(getLastComponent(lastComponent))
                        .setFileId(reponse.getBody().getFileId())
                        .setParentFileId(reponse.getBody().getParentFileId())
                        .setDriveId(reponse.getBody().getDriveId())
                        .setDomainId(reponse.getBody().getDomainId())
                        .setSize(0L)
                        .setStatus(reponse.getBody().getStatus())
                        .setCreatedAt(ISO_DATE_TIME.format(LocalDateTime.now()))
                        .setUpdatedAt(ISO_DATE_TIME.format(LocalDateTime.now()))
                        .setType(reponse.getBody().getType())
                        .setUploadId(reponse.getBody().uploadId);
                putCache(path, file);

            } catch (Exception e) {
                e.printStackTrace();
            }
            return 0;
        }
        return -ErrorCodes.ENOENT();
    }

    @Override
    public int unlink(String path) {
        try {
            System.out.println("AliyunDriveFS.unlink( " + "path = [" + path + "]" + " )");

            GetFileByPathResponse file = getPath(path, S_AVAILABLE, null);

            if (getLastComponent(path).startsWith("._") || getLastComponent(path).startsWith(".fuse_hidden")) {
                cache.remove(path);
                frist4kCache.remove(file.getFileId());
                return 0;
            }


//            file = (file==null)?getPath(path,null):file;
            DeleteFileRequest deleteFileRequest = new DeleteFileRequest();
            deleteFileRequest.setDriveId(file.driveId);
            deleteFileRequest.setFileId(file.fileId);
            DeleteFileModel response = null;

            response = driveClient.deleteFile(deleteFileRequest);
            cache.remove(path);
            frist4kCache.remove(file.getFileId());
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0;
    }

    private void upload(String fileid, String uploadId, int partNum, byte[] bytesToWrite, int len) {

        try {
            GetUploadUrlRequest uploadUrlRequest = new GetUploadUrlRequest();
            uploadUrlRequest.setFileId(fileid);
            uploadUrlRequest.setDriveId(driveClient.driveId);
            uploadUrlRequest.setUploadId(uploadId);
            UploadPartInfo part = new UploadPartInfo()
                    .setPartNumber((long) partNum);
            uploadUrlRequest.setPartInfoList(Collections.singletonList(part));
            part = driveClient.getUploadUrl(uploadUrlRequest).getBody().getPartInfoList().get(0);

            Request.Builder requestBuilder = new Request.Builder();
            RequestBody body = RequestBody.create(MediaType.parse(""), bytesToWrite, 0, len);
            requestBuilder.url(part.getUploadUrl());
            requestBuilder.put(body);
            Request request = requestBuilder.build();
            OkHttpClient okHttpClient = new OkHttpClient.Builder().build();
            Response response = okHttpClient.newCall(request).execute();
//            System.out.println("AliyunDriveFSv2.upload( " + "fileid = [" + fileid + "], uploadId = [" + uploadId + "], partNum = [" + partNum + "], bytesToWrite = [" + bytesToWrite + "], len = [" + len + "], response = [" + response.message() + "]" + " )");

        } catch (IOException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public int utimens(String path, Timespec[] timespec) {
        System.out.println("AliyunDriveFSv2.utimens( " + "path = [" + path + "], timespec = [" + timespec + "]" + " )");

        return 0;
    }
/*

    @Override
    public int getxattr(String path, String name, Pointer value, long size) {
        System.out.println("AliyunDriveFSv2.getxattr( "+"path = [" + path + "], name = [" + name + "], value = [" + value + "], size = [" + size + "]"+" )");

        return (int) 0;
    }

    @Override
    public int setxattr(String path, String name, Pointer value, long size, int flags) {
//        System.out.println("MemoryFS.setxattr( "+"path = [" + path + "], name = [" + name + "], value = [" + value + "], size = [" + size + "], flags = [" + flags + "]"+" )");
        return (int) 0;
    }
*/

    @Override
    public int write(String path, Pointer buf, @size_t long size, @off_t long offset, FuseFileInfo fi) {
        try {
//            System.out.println("AliyunDriveFS.write[" + fi.fh.get() + "]( " + "path = [" + path + "], buf = [" + buf + "], size = [" + size + "], offset = [" + offset + "]" + " )");

            GetFileByPathResponse p = getPath(path, null);

            if (p == null) {
                return -ErrorCodes.ENOENT();
            }
            if (!(p.getType().equalsIgnoreCase("file"))) {
                return -ErrorCodes.EISDIR();
            }

            if (getLastComponent(path).startsWith("._") || getLastComponent(path).startsWith(".fuse_hidden")) {
                byte[] data = new byte[(int) size];
                buf.get(0, data, 0, (int) size);
                frist4kCache.put(p.getFileId(), data);
                p.setSize((long) data.length);
                return (int) size;
            }

            FixBuffer buff = writeCache.getOrDefault(fi.fh.get(), new FixBuffer(uploadPartSize) {
                @Override
                public void flush(byte[] data, int flushNum) {
                    upload(p.getFileId(), p.getUploadId(), flushNum, data, data.length);
                }
            });
            writeCache.put(fi.fh.get(), buff);
            byte[] data = new byte[(int) size];
            buf.get(0, data, 0, (int) size);
            buff.write(data);

            if (p.getSize() < (offset + size)) {
                p.setSize(offset + size);
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }

//        try (RandomAccessFile file = new RandomAccessFile(tempDir.resolve(p.fileId).toFile(), "rw")) {
//            file.seek(offset);
//            byte[] data = new byte[(int) size];
//            buf.get(0, data, 0, data.length);
//            file.write(data, 0, data.length);
//            file.close();
//            if (p.getSize() < (offset + size)) {
//                p.setSize(offset + size);
//            }
//            p.setUpdatedAt(ISO_DATE_TIME.format(LocalDateTime.now()));
//        } catch (FileNotFoundException e) {
//            e.printStackTrace();
//        } catch (IOException e) {
//            e.printStackTrace();
//        }

        return (int) size;
    }

    public static void main(String[] args) throws Exception {
        AliyunDriveFSv2 memfs = new AliyunDriveFSv2("");

        try {
            String path;
            switch (Platform.getNativePlatform().getOS()) {
                case WINDOWS:
                    path = "J:\\";
                    break;
                default:
                    path = "/tmp/mntm";
            }

            memfs.mount(Paths.get(path), true, false);
        } finally {
            memfs.umount();
        }
    }


}
