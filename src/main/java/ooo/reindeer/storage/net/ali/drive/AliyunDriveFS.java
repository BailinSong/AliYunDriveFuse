package ooo.reindeer.storage.net.ali.drive;


import com.aliyun.pds.client.models.*;
import com.aliyun.tea.TeaException;
import com.aliyun.teautil.models.TeaUtilException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jnr.ffi.Platform;
import jnr.ffi.Pointer;
import jnr.ffi.Runtime;
import jnr.ffi.types.mode_t;
import jnr.ffi.types.off_t;
import jnr.ffi.types.size_t;
import okhttp3.Response;
import ru.serce.jnrfuse.ErrorCodes;
import ru.serce.jnrfuse.FuseFillDir;
import ru.serce.jnrfuse.FuseStubFS;
import ru.serce.jnrfuse.struct.FileStat;
import ru.serce.jnrfuse.struct.FuseFileInfo;
import ru.serce.jnrfuse.struct.Statvfs;
import ru.serce.jnrfuse.struct.Timespec;

import javax.swing.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
public class AliyunDriveFS extends FuseStubFS {


    public static final String S_AVAILABLE = "available";
    public static final String S_UPLOADING = "uploading*";
    public static final String T_SYMLINK = "symlink";
    public static final String T_FILE = "file";
    public static final String T_FOLDER = "folder";
    static List<String> exclude = Arrays.asList("._"
            , ".DS_Store"
            , ".hidden"
            , ".fuse_"
            , "~$", ".~WRL");
    private final DriveClient driveClient;
    //    Logger logger = LoggerFactory.getLogger(AliyunDriveFS.class);
    Config config = new Config();
    int uploadPartSize = 32 * 1024 * 1024;
    int readPartSize = 5 * 1024 * 1024;
    Map<String, byte[]> frist4kCache = new ConcurrentHashMap<>();
    Map<Object, FixBuffer> writeCache = new ConcurrentHashMap<>();
    Map<Object, FixBuffer> readCache = new ConcurrentHashMap<>();
    FileRef rootFile = new FileRef();
    long lastUpdateStatFS = 0;
    long totalSize = 0;
    long freeSize = 0;
    int BLOCK_SIZE = 4 * 1024;
    AtomicLong fh = new AtomicLong(0);
    ObjectMapper objectMapper = new ObjectMapper();

    public AliyunDriveFS(String rt) throws Exception {

        config.protocol = "https";
        config.refreshToken = rt;

        driveClient = new DriveClient(config);
        AccountTokenRequest tokenRequest = new AccountTokenRequest();
        tokenRequest.setRefreshToken(config.refreshToken);
        tokenRequest.setGrantType("refresh_token");
        AccountTokenModel tokenResponse;
        try {
            tokenResponse = driveClient.accountToken(tokenRequest);
        } catch (TeaUtilException teaUtilException) {
            if (teaUtilException.getMessage().contains("refresh_token is not valid")) {
                UserDataUtil.setConfig("refreshToken", AliyunDriveFS.getRefreshToken());
            }
            throw new RuntimeException(teaUtilException);
        }

        GetUserRequest userRequest = new GetUserRequest();
        userRequest.setUserId(tokenResponse.getBody().getUserId());
        GetUserModel getUserModel = driveClient.getUser(userRequest);
        driveClient.setDriveId(tokenResponse.body.getDefaultDriveId());

        rootFile.setSelf(new GetFileByPathResponse()).getSelf()
                .setType("folder")
                .setName("root")
                .setFileId("root")
                .setStatus("available")
                .setDriveId(tokenResponse.body.getDefaultDriveId())
                .setCreatedAt(ISO_DATE_TIME.format(LocalDateTime.ofEpochSecond(getUserModel.body.getCreatedAt() / 1000, 0, ZoneOffset.UTC)))
                .setUpdatedAt(ISO_DATE_TIME.format(LocalDateTime.ofEpochSecond(getUserModel.body.getUpdatedAt() / 1000, 0, ZoneOffset.UTC)));
    }

    public static String UserInput(String message, String title) {
        String value = null;
        try {
            while ((value = JOptionPane.showInputDialog(null, message, title, JOptionPane.PLAIN_MESSAGE)) == null || value.isEmpty()) {
                ;
            }
        } catch (Throwable t) {
            Scanner scanner = new Scanner(System.in);
            System.out.print(message + ":\t");
            while ((value = scanner.next()) == null || value.isEmpty()) {
                System.out.print(message + ":\t");
                ;
            }


        }
        return value;
    }

    public static String getRefreshToken() {
        return UserInput("Refresh Token", "配置");
    }

    @Override
    public synchronized int create(String path, @mode_t long mode, FuseFileInfo fi) {

        path = PathUtil.cleanPath(path);

        fi.fh.set(fh.incrementAndGet() % 999999999999999999L);


        if (getPath(path, true) != null) {
            System.out.println("AliyunDriveFS.create[" + fi.fh.get() + "]( " + "path = [" + path + "], mode = [" + mode + "]" + " ) EEXIST");
            return -ErrorCodes.EEXIST();
        } else {
            System.out.println("AliyunDriveFS.create[" + fi.fh.get() + "]( " + "path = [" + path + "], mode = [" + mode + "]" + " )");
        }


        GetFileByPathResponse parent = getParentPath(path, S_AVAILABLE, null);


        if (!parent.getType().equalsIgnoreCase("file")) {
            String lastComponent = getLastComponent(path);

            if (isExclude(lastComponent)) {

                GetFileByPathResponse file = new GetFileByPathResponse();

                file
                        .setName(getLastComponent(lastComponent))
                        .setFileId(UUID.randomUUID().toString())
                        .setSize(0L)
                        .setStatus(null)
                        .setCreatedAt(ISO_DATE_TIME.format(LocalDateTime.now()))
                        .setUpdatedAt(ISO_DATE_TIME.format(LocalDateTime.now()))
                        .setType("file")
                        .setCategory("L");
                frist4kCache.getOrDefault(path, new byte[0]);
                rootFile.put(path, file);
                return 0;
            }

            try {
                CreateFileRequest createFileRequest = new CreateFileRequest();
                createFileRequest.setType("file");
                createFileRequest.setDriveId(driveClient.driveId);
                createFileRequest.setName(lastComponent);
                createFileRequest.setParentFileId(parent.getFileId());
//                createFileRequest.setParallelUpload(true);
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
                        .setUploadId(reponse.getBody().uploadId)
                        .setCategory("L");
                rootFile.put(path, file);
//                writeCache.put(reponse.getBody().getFileId(), new byte[uploadPartSize]);

            } catch (Exception e) {
                e.printStackTrace();
            }
            return 0;
        }
        return -ErrorCodes.ENOENT();
    }

    public GetFileByPathResponse find(String path) {
        String lastComponent = getLastComponent(path);
        if (isExclude(lastComponent)) {
            return null;
        }

        String tempPath = path;
        while (tempPath.startsWith("/")) {
            tempPath = tempPath.substring(1);
        }
        if (tempPath.isEmpty()) {
            try {
                return rootFile.getSelf();
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
        path = PathUtil.cleanPath(path);
        System.out.println("AliyunDriveFS.flush[" + fi.fh.get() + "]( " + "path = [" + path + "]" + " )");
        readCache.remove(fi.fh.get());
        return super.flush(path, fi);
    }

    @Override
    public int fsync(String path, int isdatasync, FuseFileInfo fi) {
        path = PathUtil.cleanPath(path);
        System.out.println("AliyunDriveFS.fsync( " + "path = [" + path + "], isdatasync = [" + isdatasync + "], fi = [" + fi + "]" + " )");
        return super.fsync(path, isdatasync, fi);
    }

    private String getLastComponent(String path) {
//        System.out.println("AliyunDriveFS.getLastComponent( "+"path = [" + path + "]"+" )");

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
        return getPath(getParentComponent(path), false, status);
    }

    private GetFileByPathResponse getPath(String path, String... status) {
        return getPath(path, false, status);
    }

    private GetFileByPathResponse getPath(String path, boolean remote, String... status) {

//        //?
//        if (isExclude(path)) {
//            return null;
//        }

        GetFileByPathResponse value;
        FileRef file = rootFile.find(path);
        GetFileByPathResponse item = (file == null) ? null : file.getSelf();


        if (Objects.nonNull(file)) {
            value = item;
        } else {
            if (!remote) {
                return null;
            }
            value = find(path);
            if (Objects.nonNull(value)) {
                rootFile.put(path, value);
            } else {
                value = item;
            }
        }


        if (Objects.nonNull(value)) {
            if (Objects.nonNull(status)) {
                for (String s : status) {
                    if (Objects.equals(s, value.getStatus())) {
//                        System.out.println("AliyunDriveFS.getPath( " + "path = [" + path + "], status = [" + Arrays.toString(status) + "]" + " ) return [" + value + "]");
                        return value;
                    }
                }
            } else if (Objects.equals(status, value.getStatus())) {
//                System.out.println("AliyunDriveFS.getPath( " + "path = [" + path + "], status = [" + Arrays.toString(status) + "]" + " ) return [" + value + "]");
                return value;
            }
            value = null;
        } else {
            value = null;
        }

        System.out.println("AliyunDriveFS.getPath( " + "path = [" + path + "], status = [" + Arrays.toString(status) + "]" + " ) return [" + value + "]");
        return value;
    }

    private GetFileByPathResponse getPath(String path) {


        return getPath(path, S_AVAILABLE);
    }

    private GetFileByPathResponse getPath(String path, boolean remote) {


        return getPath(path, remote, S_AVAILABLE);
    }

    @Override
    public synchronized int getattr(String path, FileStat stat) {
        path = PathUtil.cleanPath(path);

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
                        stat.st_blocks.set(file.getSize() / BLOCK_SIZE + 1);
//                        stat.st_blksize.set(64*1024);

                }
                if (stat.st_birthtime != null) {
                    stat.st_birthtime.tv_sec.set(LocalDateTime.parse(file.getCreatedAt(), ISO_DATE_TIME).toEpochSecond(ZoneOffset.of("+0")));
                }
                stat.st_mtim.tv_sec.set(LocalDateTime.parse(file.getUpdatedAt(), ISO_DATE_TIME).toEpochSecond(ZoneOffset.of("+0")));
                stat.st_uid.set(getContext().uid.get());
                stat.st_gid.set(getContext().gid.get());
                System.out.println("AliyunDriveFS.getattr( " + "path = [" + path + "], stat = [ok]" + " )");
                return 0;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.out.println("AliyunDriveFS.getattr( " + "path = [" + path + "], stat = [ENOENT]" + " )");

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
        oldpath = PathUtil.cleanPath(oldpath);
        newpath = PathUtil.cleanPath(newpath);
        System.out.println("AliyunDriveFS.link( " + "oldpath = [" + oldpath + "], newpath = [" + newpath + "]" + " )");

        return 0;
    }

    @Override
    public int mkdir(String path, @mode_t long mode) {
        path = PathUtil.cleanPath(path);
        System.out.println("AliyunDriveFS.mkdir( " + "path = [" + path + "], mode = [" + mode + "]" + " )");

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
                    .setStatus(reponse.getBody().getStatus())
                    .setCreatedAt(ISO_DATE_TIME.format(LocalDateTime.now()))
                    .setUpdatedAt(ISO_DATE_TIME.format(LocalDateTime.now()))
                    .setType(reponse.getBody().getType())
                    .setCategory("L");
            rootFile.put(path, file);

            return 0;
        } catch (Exception e) {
            e.printStackTrace();
        }

        return -ErrorCodes.ENOENT();

    }

    @Override
    public int open(String path, FuseFileInfo fi) {
        path = PathUtil.cleanPath(path);
        if (fi.fh.get() == 0) {
            fi.fh.set(fh.incrementAndGet() % 999999999999999999L);
        }

        System.out.println("AliyunDriveFS.open[" + fi.fh.get() + "]( " + "path = [" + path + "]" + " )");

        return 0;
    }

    @Override
    public int read(String path, Pointer buf, @size_t long size, @off_t long offset, FuseFileInfo fi) {
        path = PathUtil.cleanPath(path);
        System.out.println("AliyunDriveFS.read[" + fi.fh.get() + "]( " + "path = [" + path + "], buf = [" + buf + "], size = [" + size + "], offset = [" + offset + "]" + " )");
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
            String lastComponent = getLastComponent(path);
            if (isExclude(lastComponent)) {


                if (frist4kCache.get(path) == null) {
                    buf.put(0, new byte[bytesToRead], 0, bytesToRead);
                } else {
                    buf.put(0, frist4kCache.get(path), 0, bytesToRead);
                }
                return bytesToRead;
            }


            byte[] bytes = null;


            if (bytesToRead <= 4 * 1024 && offset == 0 && readCache.get(fi.fh.get()) == null) {
                bytes = driveClient.downloadPart(file.driveId, file.fileId, file.getSize().longValue(), (int) offset, bytesToRead);
                buf.put(0, bytes, 0, bytesToRead);
                return bytesToRead;
            }
//            if (size <= 4 * 1024 && offset == 0) {
//                bytes = frist4kCache.get(file.getFileId());
//            }
//            if (bytes == null || offset==0 &&bytes.length < 4 * 1024) {
//                bytes = driveClient.downloadPart(file.driveId, file.fileId, file.getSize().intValue(), (int) offset, bytesToRead);
//                frist4kCache.put(file.getFileId(), bytes);
//            } else {
            FixBuffer fixBuffer = readCache.getOrDefault(fi.fh.get(), new FixBuffer(file.fileId,readPartSize) {
                @Override
                public void load(ByteArrayOutputStream buff, long size, int offset, int len) {
                    try {
                        buff.write(driveClient.downloadPart(file.driveId, file.fileId, size, offset, len));
                    } catch (Exception e) {
                        new RuntimeException(e);
                    }
                }
            });
            readCache.put(fi.fh.get(), fixBuffer);
            bytes = fixBuffer.read(file.getSize().longValue(), (int) offset, bytesToRead);
//            }


            buf.put(0, bytes, 0, bytesToRead);

        } catch (Exception e) {
            e.printStackTrace();
        }
        return bytesToRead;
    }

    @Override
    public int readdir(String path, Pointer buf, FuseFillDir filter, @off_t long offset, FuseFileInfo fi) {
        path = PathUtil.cleanPath(path);
//        System.out.println("AliyunDriveFS.readdir( " + "path = [" + path + "], buf = [" + buf + "], filter = [" + filter + "], offset = [" + offset + "]" + " )");


        try {
            FileRef file = rootFile.find(path);
//            file =(file==null)?getPath(path,null):file;
            if (file == null || file.getSelf() == null) {
                return -ErrorCodes.ENOENT();
            }
            if (file.getSelf().getType().equalsIgnoreCase("file")) {
                return -ErrorCodes.ENOTDIR();
            }

            filter.apply(buf, ".", null, 0);
            filter.apply(buf, "..", null, 0);

            file.getChildren().forEach((s, fileRef) -> {
                if (fileRef.getSelf().getCategory() == null && fileRef.getSelf().getType().equals(T_FILE)) {
                    file.getChildren().remove(fileRef.getSelf().getName());
                }
            });

            String marker = "";
            List<String> remoteNames = new ArrayList<>();
            do {
                ListFileRequest listFileRequest = new ListFileRequest();
                listFileRequest.setDriveId(file.getSelf().getDriveId());
                listFileRequest.setMarker(marker);
                listFileRequest.setParentFileId(file.getSelf().getFileId());

                ListFileModel listFileResponse = null;

                listFileResponse = driveClient.listFile(listFileRequest);
                marker = listFileResponse.getBody().getNextMarker();
                for (BaseCCPFileResponse item : listFileResponse.getBody().getItems()) {

//                    System.out.println("objectMapper.writeValueAsString(item) = " + objectMapper.writeValueAsString(item));
                    remoteNames.add(item.getName());

                    FileRef newFile = new FileRef();
                    FileRef original = file.getChildren().get(item.getName());
                    newFile.setSelf(new GetFileByPathResponse()).getSelf()
                            .setStatus(item.getStatus())
                            .setType(item.getType())
                            .setName(item.getName())
                            .setFileId(item.getFileId())
                            .setParentFileId(item.getParentFileId())
                            .setCreatedAt(item.getCreatedAt())
                            .setUpdatedAt(item.getUpdatedAt())
                            .setLabels(item.getLabels())
                            .setSize(item.size)
                            .setDriveId(item.getDriveId())
                            .setDomainId(item.getDomainId())
                            .setCategory(null);


                    if (original != null) {
                        original.setSelf(newFile.getSelf());
                    } else {
                        file.getChildren().put(item.getName(), newFile);
                    }
                }


            } while (!marker.isEmpty());

            file.getChildren().forEach((s, fileRef) -> {

                if ((!remoteNames.contains(s)) && fileRef.getSelf().getCategory() == null) {
                    file.getChildren().remove(s);
                }

            });

            String fpath = path;
            file.getChildren().forEach((s, fileRef) -> {
                System.out.println("item.getName() = " + s + " stat = " + fileRef.getSelf().getStatus() + " category = " + fileRef.getSelf().getCategory());
                FileStat fileStat = new FileStat(Runtime.getSystemRuntime());
                getattr(fpath, fileStat);
                if (fileRef.self.getType().equalsIgnoreCase(T_FILE)) {
                    filter.apply(buf, s, fileStat, 0);
                } else {
                    filter.apply(buf, s, fileStat, 0);
                }
            });


        } catch (Exception e) {
            e.printStackTrace();
        }

        return 0;
    }

    @Override
    public int readlink(String path, Pointer buf, long size) {
        path = PathUtil.cleanPath(path);
//        System.out.println("AliyunDriveFS.readlink( " + "path = [" + path + "], buf = [" + buf + "], size = [" + size + "]" + " )");
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
        path = PathUtil.cleanPath(path);
        long begin = System.currentTimeMillis();

        try {
            GetFileByPathResponse fileInfo = getPath(path, null, S_UPLOADING);
            if (fileInfo == null) {
                return 0;
            }

            String lastComponent = getLastComponent(path);
            if (isExclude(lastComponent)) {

                fileInfo.setStatus(S_AVAILABLE);
                return 0;
            }

            FixBuffer buffer = writeCache.remove(path);


            if (buffer != null) {
                buffer.flush();

            }
            /*else{
                upload(fileInfo.getFileId(), fileInfo.getUploadId(), 1, new byte[0], 0);
            }*/
            if (buffer == null||buffer.isUploadCompleted()) {
                CompleteFileRequest CompleteFileRequest = new CompleteFileRequest();
                CompleteFileRequest.driveId = fileInfo.getDriveId();
                CompleteFileRequest.fileId = fileInfo.fileId;
                CompleteFileRequest.uploadId = fileInfo.uploadId;

                CompleteFileModel CompleteFileResponse = driveClient.completeFile(CompleteFileRequest);
            }

            fileInfo.setStatus(S_AVAILABLE);


        } catch (Exception e) {
            e.printStackTrace();
        }
        System.out.println("AliyunDriveFS.release[" + fi.fh.get() + "]( " + "path = [" + path + "]" + " ) use=" + (System.currentTimeMillis() - begin));

        return 0;
    }

    @Override
    public int rename(String path, String newName) {
        path = PathUtil.cleanPath(path);
        newName = PathUtil.cleanPath(newName);
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

            String lastComponent = getLastComponent(path);
            if (p.getStatus() == null || isExclude(lastComponent)) {

                rootFile.put(newName, rootFile.remove(path).self.setName(getLastComponent(newName)));
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
            rootFile.remove(path);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return 0;
    }

    @Override
    public int rmdir(String path) {
        path = PathUtil.cleanPath(path);
        return unlink(path);
    }


    @Override
    public int statfs(String path, Statvfs stbuf) {
        path = PathUtil.cleanPath(path);
//       System.out.println("AliyunDriveFS.statfs( "+"path = [" + path + "] )");

//        if ("/".equals(path) || path.endsWith(":/")) {

        try {

            if (lastUpdateStatFS < System.currentTimeMillis() - 30000) {

                lastUpdateStatFS = System.currentTimeMillis();

                GetDriveRequest getDriveRequest = new GetDriveRequest();
                getDriveRequest.setDriveId(driveClient.getDriveId());


                GetDriveModel getDriveModel = driveClient.getDrive(getDriveRequest);


                totalSize = getDriveModel.getBody().getTotalSize() / BLOCK_SIZE;
                freeSize = (getDriveModel.getBody().getTotalSize() - getDriveModel.getBody().getUsedSize()) / BLOCK_SIZE;
            }

            stbuf.f_blocks.set(totalSize); // total data blocks in file system

            stbuf.f_bsize.set(BLOCK_SIZE);

            stbuf.f_bfree.set(freeSize);  // free blocks in fs
            stbuf.f_bavail.set(freeSize);

        } catch (Exception e) {
            e.printStackTrace();
        }


//        }
        return super.statfs(path, stbuf);
    }

    @Override
    public int symlink(String oldpath, String newpath) {
        oldpath = PathUtil.cleanPath(oldpath);
        newpath = PathUtil.cleanPath(newpath);
        System.out.println("AliyunDriveFS.symlink( " + "oldpath = [" + oldpath + "], newpath = [" + newpath + "]" + " )");
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
                    .setUploadId(reponse.getBody().uploadId)
                    .setCategory("L");
            rootFile.put(newpath, file);


            upload(reponse.getBody().getFileId(), reponse.getBody().getUploadId(), 1, data, data.length);

            CompleteFileRequest CompleteFileRequest = new CompleteFileRequest();
            CompleteFileRequest.driveId = reponse.getBody().getDriveId();
            CompleteFileRequest.fileId = reponse.getBody().getFileId();
            CompleteFileRequest.uploadId = reponse.getBody().getUploadId();
            CompleteFileModel CompleteFileResponse = driveClient.completeFile(CompleteFileRequest);
            file.setStatus(S_AVAILABLE);
            return 0;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return -ErrorCodes.ENOENT();
    }

    @Override
    public synchronized int truncate(String path, long offset) {
        path = PathUtil.cleanPath(path);
        try {
            System.out.println("AliyunDriveFS.truncate( " + "path = [" + path + "], offset = [" + offset + "]" + " )");
            GetFileByPathResponse target = getPath(path, S_AVAILABLE, null);
            if (target == null) {
                return -ErrorCodes.ENOENT();
            } else {
                if (S_AVAILABLE.equals(target.getStatus())) {
                    String newName = path + ".bak-" + System.currentTimeMillis();
                    rename(path, path + ".bak-" + System.currentTimeMillis());
                    unlink(newName);
                }
            }

            GetFileByPathResponse parent = getParentPath(path, S_AVAILABLE, null);


            if (!parent.getType().equalsIgnoreCase("file")) {
                String lastComponent = getLastComponent(path);

                if (isExclude(lastComponent)) {
                    frist4kCache.getOrDefault(path, new byte[4096]);
                    GetFileByPathResponse file = new GetFileByPathResponse();

                    file
                            .setName(lastComponent)
                            .setFileId(UUID.randomUUID().toString())
                            .setSize(4096L)
                            .setStatus(S_AVAILABLE)
                            .setCreatedAt(ISO_DATE_TIME.format(LocalDateTime.now()))
                            .setUpdatedAt(ISO_DATE_TIME.format(LocalDateTime.now()))
                            .setType("file")
                            .setCategory("L");
                    rootFile.put(path, file);
                } else {
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
                                .setUploadId(reponse.getBody().uploadId)
                                .setCategory("L");
                        rootFile.put(path, file);

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                return 0;
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
        return -ErrorCodes.ENOENT();
    }

    @Override
    public synchronized int unlink(String path) {
        path = PathUtil.cleanPath(path);
        try {
            System.out.println("AliyunDriveFS.unlink( " + "path = [" + path + "]" + " )");

            GetFileByPathResponse file = getPath(path, true, S_AVAILABLE, null);

            if (file != null) {
                String lastComponent = getLastComponent(path);
                if (isExclude(lastComponent)) {
                    rootFile.remove(path);
                    frist4kCache.remove(path);
                    return 0;
                }


                DeleteFileRequest deleteFileRequest = new DeleteFileRequest();
                deleteFileRequest.setDriveId(file.driveId);
                deleteFileRequest.setFileId(file.fileId);
                DeleteFileModel response = null;

                response = driveClient.deleteFile(deleteFileRequest);
                rootFile.remove(path);
                frist4kCache.remove(path);
            }
        } catch (Exception e) {
            new RuntimeException(path, e).printStackTrace();
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
            String uploadUrl = part.getUploadUrl();

            try (Response response = driveClient.uploadFilePart(uploadUrl, bytesToWrite, 0, len)) {
                System.out.println("AliyunDriveFS.upload( " + "fileid = [" + fileid + "], uploadId = [" + uploadId + "], partNum = [" + partNum + "], bytesToWrite = [" + bytesToWrite + "], len = [" + len + "], response = [" + response.message() + "]" + " )");

            }

        } catch (IOException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public int utimens(String path, Timespec[] timespec) {
        path = PathUtil.cleanPath(path);
        System.out.println("AliyunDriveFS.utimens( " + "path = [" + path + "], timespec = [" + timespec + "]" + " )");
        rootFile.find(path).self.setUpdatedAt(ISO_DATE_TIME.format(LocalDateTime.ofEpochSecond(timespec[0].tv_sec.get(),
                timespec[0].tv_nsec.intValue(), ZoneOffset.UTC
        )));
        return 0;
    }
/*

    @Override
    public int getxattr(String path, String name, Pointer value, long size) {
        System.out.println("AliyunDriveFS.getxattr( "+"path = [" + path + "], name = [" + name + "], value = [" + value + "], size = [" + size + "]"+" )");

        return (int) 0;
    }

    @Override
    public int setxattr(String path, String name, Pointer value, long size, int flags) {
//        System.out.println("MemoryFS.setxattr( "+"path = [" + path + "], name = [" + name + "], value = [" + value + "], size = [" + size + "], flags = [" + flags + "]"+" )");
        return (int) 0;
    }
*/

    @Override
    public synchronized int write(String path, Pointer buf, @size_t long size, @off_t long offset, FuseFileInfo fi) {
        path = PathUtil.cleanPath(path);
        try {
//            System.out.println("AliyunDriveFS.write( "+"path = [" + path + "], buf = [" + buf + "], size = [" + size + "], offset = [" + offset + "])");

            GetFileByPathResponse p = getPath(path, null);


            if (p == null) {
                if ((p = getPath(path, S_AVAILABLE)) != null) {

                    if (!isExclude(getLastComponent(path))) {
                        truncate(path, offset);
                        p = getPath(path, null);
//                        System.out.println("AliyunDriveFS.write[" + fi.fh.get() + "]( " + "path = [" + path + "], buf = [" + buf + "], size = [" + size + "], offset = [" + offset + "]" + " ) TRUNCATE");
                    }
                } else {
//                    System.out.println("AliyunDriveFS.write[" + fi.fh.get() + "]( " + "path = [" + path + "], buf = [" + buf + "], size = [" + size + "], offset = [" + offset + "]" + " ) ENOENT");

                    return -ErrorCodes.ENOENT();
                }
            }
//            System.out.println("AliyunDriveFS.write[" + fi.fh.get() + "]( " + "path = [" + path + "], buf = [" + buf + "], size = [" + size + "], offset = [" + offset + "]" + " )");
            if (!(p.getType().equalsIgnoreCase(T_FILE))) {
                return -ErrorCodes.EISDIR();
            }


            String lastComponent = getLastComponent(path);
            if (isExclude(lastComponent)) {
                byte[] sdata = frist4kCache.getOrDefault(path, new byte[(int) (offset + size)]);
                if (offset + size > sdata.length) {
                    byte[] ddata = new byte[(int) (offset + size)];
                    System.arraycopy(sdata, 0, ddata, 0, sdata.length);
                    sdata = ddata;
                }
                buf.get(0, sdata, (int) offset, (int) size);
                frist4kCache.put(path, sdata);
                p.setSize((long) sdata.length);
                return (int) size;
            }

            GetFileByPathResponse finalFile = p;

            FixBuffer buff = writeCache.getOrDefault(path, new FixBuffer(finalFile.fileId,uploadPartSize) {
                @Override
                public void flush(byte[] data, int flushNum) {

                    upload(finalFile.getFileId(), finalFile.getUploadId(), flushNum, data, data.length);
                }
            });
            writeCache.put(path, buff);
            byte[] data = new byte[(int) size];
            buf.get(0, data, 0, (int) size);
            buff.write(data,offset);

            if (p.getSize() < (offset + size)) {
                p.setSize(offset + size);

            }
        } catch (Throwable t) {
            t.printStackTrace();
        }

        return (int) (size);
    }

    public static void main(String[] args) throws Exception {
//        Logger.getLogger(OkHttpClient.class.getName()).setLevel(Level.FINE);


        AliyunDriveFS drive = null;

        do {
            try {
                drive = new AliyunDriveFS(UserDataUtil.getConfig("refreshToken", AliyunDriveFS::getRefreshToken));
            } catch (Exception e) {
                e.printStackTrace();
            }
        } while (drive == null);

        try {
            String path;
            switch (Platform.getNativePlatform().getOS()) {
                case WINDOWS:
                    path = "J:\\";
                    break;
                case LINUX:
                    path = "/mnt/aliyun";
                    break;
                default:
                    path = "/Volumes/AliyunDrive";
            }
            System.out.println("mount on:\t" + path);
            drive.mount(Paths.get(path), true, false);

        } finally {
            drive.umount();
        }
    }


}
