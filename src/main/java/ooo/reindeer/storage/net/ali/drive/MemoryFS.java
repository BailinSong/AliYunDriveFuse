package ooo.reindeer.storage.net.ali.drive;


import jnr.ffi.Platform;
import jnr.ffi.Pointer;
import jnr.ffi.types.mode_t;
import jnr.ffi.types.off_t;
import jnr.ffi.types.size_t;
import ru.serce.jnrfuse.ErrorCodes;
import ru.serce.jnrfuse.FuseFillDir;
import ru.serce.jnrfuse.FuseStubFS;
import ru.serce.jnrfuse.struct.FileStat;
import ru.serce.jnrfuse.struct.FuseFileInfo;
import ru.serce.jnrfuse.struct.Statvfs;
import ru.serce.jnrfuse.struct.Timespec;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class MemoryFS extends FuseStubFS {
    private final MemoryDirectory rootDirectory = new MemoryDirectory("");

    public MemoryFS() {
        // Sprinkle some files around
//        rootDirectory.add(new MemoryFile("Sample file.txt", "Hello there, feel free to look around.\n"));
//        rootDirectory.add(new MemoryDirectory("Sample directory"));
//        MemoryDirectory dirWithFiles = new MemoryDirectory("Directory with files");
//        rootDirectory.add(dirWithFiles);
//        dirWithFiles.add(new MemoryFile("hello.txt", "This is some sample text.\n"));
//        dirWithFiles.add(new MemoryFile("hello again.txt", "This another file with text in it! Oh my!\n"));
//        MemoryDirectory nestedDirectory = new MemoryDirectory("Sample nested directory");
//        dirWithFiles.add(nestedDirectory);
//        nestedDirectory.add(new MemoryFile("So deep.txt", "Man, I'm like, so deep in this here file structure.\n"));
    }

    @Override
    public int create(String path, @mode_t long mode, FuseFileInfo fi) {
        System.out.println("MemoryFS.create( " + "path = [" + path + "], mode = [" + mode + "], fi = [" + fi + "]" + " )");

        if (getPath(path) != null) {
            return -ErrorCodes.EEXIST();
        }
        MemoryPath parent = getParentPath(path);
        if (parent instanceof MemoryDirectory) {
            ((MemoryDirectory) parent).mkfile(getLastComponent(path));
            return 0;
        }
        return -ErrorCodes.ENOENT();
    }

    private String getLastComponent(String path) {
        while (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        if (path.isEmpty()) {
            return "";
        }
        return path.substring(path.lastIndexOf("/") + 1);
    }

    private MemoryPath getParentPath(String path) {
        return rootDirectory.find(path.substring(0, path.lastIndexOf("/")));
    }

    private MemoryPath getPath(String path) {
        return rootDirectory.find(path);
    }

    @Override
    public int getattr(String path, FileStat stat) {
        MemoryPath p = getPath(path);
        if (p != null) {
            p.getattr(stat);
            return 0;
        }
        System.out.println("MemoryFS.getattr( " + "path = [" + path + "], stat = [error]" + " )");

        return -ErrorCodes.ENOENT();
    }

    @Override
    public int getxattr(String path, String name, Pointer value, long size) {
        System.out.println("MemoryFS.getxattr( " + "path = [" + path + "], name = [" + name + "], value = [" + value + "], size = [" + size + "]" + " )");

        return 0;
    }

    @Override
    public int link(String oldpath, String newpath) {
        System.out.println("MemoryFS.link( " + "oldpath = [" + oldpath + "], newpath = [" + newpath + "]" + " )");

        return 0;
    }

    @Override
    public int mkdir(String path, @mode_t long mode) {
        System.out.println("MemoryFS.mkdir( " + "path = [" + path + "], mode = [" + mode + "]" + " )");

        if (getPath(path) != null) {
            return -ErrorCodes.EEXIST();
        }
        MemoryPath parent = getParentPath(path);
        if (parent instanceof MemoryDirectory) {
            ((MemoryDirectory) parent).mkdir(getLastComponent(path));
            return 0;
        }
        return -ErrorCodes.ENOENT();
    }

    @Override
    public int open(String path, FuseFileInfo fi) {
        return 0;
    }

    @Override
    public int read(String path, Pointer buf, @size_t long size, @off_t long offset, FuseFileInfo fi) {
        System.out.println("MemoryFS.read( " + "path = [" + path + "], buf = [" + buf + "], size = [" + size + "], offset = [" + offset + "], fi = [" + fi + "]" + " )");

        MemoryPath p = getPath(path);
        if (p == null) {
            return -ErrorCodes.ENOENT();
        }
        if (!(p instanceof MemoryFile)) {
            return -ErrorCodes.EISDIR();
        }
        return ((MemoryFile) p).read(buf, size, offset);
    }

    @Override
    public int readdir(String path, Pointer buf, FuseFillDir filter, @off_t long offset, FuseFileInfo fi) {
        MemoryPath p = getPath(path);
        if (p == null) {
            return -ErrorCodes.ENOENT();
        }
        if (!(p instanceof MemoryDirectory)) {
            return -ErrorCodes.ENOTDIR();
        }
        filter.apply(buf, ".", null, 0);
        filter.apply(buf, "..", null, 0);
        ((MemoryDirectory) p).read(buf, filter);
        return 0;
    }

    @Override
    public int readlink(String path, Pointer buf, long size) {
        System.out.println("MemoryFS.readlink( " + "path = [" + path + "], buf = [" + buf + "], size = [" + size + "]" + " )");
        MemoryFile file = (MemoryFile) getPath(path);
        byte[] bytes = file.contents.array();
        buf.put(0, bytes, 0, bytes.length);
        buf.put(bytes.length,
                new byte[]{0},
                0,
                1);
        return 0;
    }

    @Override
    public int rename(String path, String newName) {
        System.out.println("MemoryFS.rename( " + "path = [" + path + "], newName = [" + newName + "]" + " )");

        MemoryPath p = getPath(path);
        if (p == null) {
            return -ErrorCodes.ENOENT();
        }
        MemoryPath newParent = getParentPath(newName);
        if (newParent == null) {
            return -ErrorCodes.ENOENT();
        }
        if (!(newParent instanceof MemoryDirectory)) {
            return -ErrorCodes.ENOTDIR();
        }
        p.delete();
        p.rename(newName.substring(newName.lastIndexOf("/")));
        ((MemoryDirectory) newParent).add(p);
        return 0;
    }

    @Override
    public int rmdir(String path) {
        System.out.println("MemoryFS.rmdir( " + "path = [" + path + "]" + " )");

        MemoryPath p = getPath(path);
        if (p == null) {
            return -ErrorCodes.ENOENT();
        }
        if (!(p instanceof MemoryDirectory)) {
            return -ErrorCodes.ENOTDIR();
        }
        p.delete();
        return 0;
    }

    @Override
    public int setxattr(String path, String name, Pointer value, long size, int flags) {
//        System.out.println("MemoryFS.setxattr( "+"path = [" + path + "], name = [" + name + "], value = [" + value + "], size = [" + size + "], flags = [" + flags + "]"+" )");

        return 0;
    }

    @Override
    public int statfs(String path, Statvfs stbuf) {
        if ("/".equals(path)) {
            stbuf.f_blocks.set(1024 * 1024 * 1024); // total data blocks in file system

//                stbuf.f_frsize.set(blockSize);        // fs block size
            stbuf.f_bsize.set(4096);

            stbuf.f_bfree.set(1024 * 1024 * 1024);  // free blocks in fs
            stbuf.f_bavail.set(1024 * 1024 * 1024);
        }

        return super.statfs(path, stbuf);
    }

    @Override
    public int symlink(String oldpath, String newpath) {
        System.out.println("MemoryFS.symlink( " + "oldpath = [" + oldpath + "], newpath = [" + newpath + "]" + " )");
        if (getPath(newpath) != null) {
            return -ErrorCodes.EEXIST();
        }
        MemoryPath parent = getParentPath(newpath);
        if (parent instanceof MemoryDirectory) {
            ((MemoryDirectory) parent).mkfile(getLastComponent(newpath), oldpath.getBytes(StandardCharsets.UTF_8));
            return 0;
        }
        return -ErrorCodes.ENOENT();
    }

    @Override
    public int truncate(String path, long offset) {
        System.out.println("MemoryFS.truncate( " + "path = [" + path + "], offset = [" + offset + "]" + " )");

        MemoryPath p = getPath(path);
        if (p == null) {
            return -ErrorCodes.ENOENT();
        }
        if (!(p instanceof MemoryFile)) {
            return -ErrorCodes.EISDIR();
        }
        ((MemoryFile) p).truncate(offset);
        return 0;
    }

    @Override
    public int unlink(String path) {
        System.out.println("MemoryFS.unlink( " + "path = [" + path + "]" + " )");

        MemoryPath p = getPath(path);
        if (p == null) {
            return -ErrorCodes.ENOENT();
        }
        p.delete();
        return 0;
    }

    @Override
    public int utimens(String path, Timespec[] timespec) {
        System.out.println("MemoryFS.utimens( " + "path = [" + path + "], timespec = [" + timespec + "]" + " )");

        return 0;
    }

    @Override
    public int write(String path, Pointer buf, @size_t long size, @off_t long offset, FuseFileInfo fi) {

//        System.out.println("MemoryFS.write( "+"path = [" + path + "], buf = [" + buf + "], size = [" + size + "], offset = [" + offset + "], fi = [" + fi + "]"+" )");

        MemoryPath p = getPath(path);
        if (p == null) {
            return -ErrorCodes.ENOENT();
        }
        if (!(p instanceof MemoryFile)) {
            return -ErrorCodes.EISDIR();
        }
        return ((MemoryFile) p).write(buf, size, offset);
    }

    private class MemoryDirectory extends MemoryPath {
        private final List<MemoryPath> contents = new ArrayList<>();

        private MemoryDirectory(String name) {
            super(name);
        }

        private MemoryDirectory(String name, MemoryDirectory parent) {
            super(name, parent);
        }

        public synchronized void add(MemoryPath p) {
            contents.add(p);
            p.parent = this;
        }

        private synchronized void deleteChild(MemoryPath child) {
            contents.remove(child);
        }

        @Override
        protected MemoryPath find(String path) {
            if (super.find(path) != null) {
                return super.find(path);
            }
            while (path.startsWith("/")) {
                path = path.substring(1);
            }
            synchronized (this) {
                if (!path.contains("/")) {
                    for (MemoryPath p : contents) {
                        if (p.name.equals(path)) {
                            return p;
                        }
                    }

                    return null;
                }
                String nextName = path.substring(0, path.indexOf("/"));
                String rest = path.substring(path.indexOf("/"));
                for (MemoryPath p : contents) {
                    if (p.name.equals(nextName)) {
                        return p.find(rest);
                    }
                }
            }


            return null;
        }

        @Override
        protected void getattr(FileStat stat) {
            stat.st_mode.set(FileStat.S_IFDIR | 0777);
            stat.st_uid.set(getContext().uid.get());
            stat.st_gid.set(getContext().gid.get());
        }

        private synchronized void mkdir(String lastComponent) {
            contents.add(new MemoryDirectory(lastComponent, this));
        }

        public synchronized void mkfile(String lastComponent) {
            contents.add(new MemoryFile(lastComponent, this));
        }

        public synchronized void mkfile(String lastComponent, byte[] link) {
            contents.add(new MemoryFile(lastComponent, this, link));
        }

        public synchronized void read(Pointer buf, FuseFillDir filler) {
            for (MemoryPath p : contents) {
                filler.apply(buf, p.name, null, 0);
            }
        }
    }

    private class MemoryFile extends MemoryPath {
        boolean symlink;
        private ByteBuffer contents = ByteBuffer.allocate(0);

        private MemoryFile(String name) {
            super(name);
        }

        private MemoryFile(String name, MemoryDirectory parent) {
            super(name, parent);
        }

        public MemoryFile(String name, String text) {
            super(name);
            byte[] contentBytes = text.getBytes(StandardCharsets.UTF_8);
            contents = ByteBuffer.wrap(contentBytes);
        }

        public MemoryFile(String name, MemoryDirectory parent, byte[] link) {
            super(name, parent);

            contents = ByteBuffer.wrap(link);

            symlink = true;

        }

        @Override
        protected void getattr(FileStat stat) {


            if (symlink) {
                stat.st_mode.set(FileStat.S_IFLNK | 0777);
            } else {
                stat.st_mode.set(FileStat.S_IFREG | 0777);
            }
            stat.st_size.set(contents.capacity());
            stat.st_uid.set(getContext().uid.get());
            stat.st_gid.set(getContext().gid.get());
        }

        private int read(Pointer buffer, long size, long offset) {
            int bytesToRead = (int) Math.min(contents.capacity() - offset, size);
            byte[] bytesRead = new byte[bytesToRead];
            synchronized (this) {
                contents.position((int) offset);
                contents.get(bytesRead, 0, bytesToRead);
                buffer.put(0, bytesRead, 0, bytesToRead);
                contents.position(0); // Rewind
            }
            return bytesToRead;
        }

        private synchronized void truncate(long size) {
            if (size < contents.capacity()) {
                // Need to create a new, smaller buffer
                ByteBuffer newContents = ByteBuffer.allocate((int) size);
                byte[] bytesRead = new byte[(int) size];
                contents.get(bytesRead);
                newContents.put(bytesRead);
                contents = newContents;
            }
        }

        private int write(Pointer buffer, long bufSize, long writeOffset) {
            int maxWriteIndex = (int) (writeOffset + bufSize);
            byte[] bytesToWrite = new byte[(int) bufSize];
            synchronized (this) {
                if (maxWriteIndex > contents.capacity()) {
                    // Need to create a new, larger buffer
                    ByteBuffer newContents = ByteBuffer.allocate(maxWriteIndex);
                    newContents.put(contents);
                    contents = newContents;
                }
                buffer.get(0, bytesToWrite, 0, (int) bufSize);
                contents.position((int) writeOffset);
                contents.put(bytesToWrite);
                contents.position(0); // Rewind
            }
            return (int) bufSize;
        }
    }

    private abstract class MemoryPath {
        private String name;
        private MemoryDirectory parent;

        private MemoryPath(String name) {
            this(name, null);
        }

        private MemoryPath(String name, MemoryDirectory parent) {
            this.name = name;
            this.parent = parent;
        }

        private synchronized void delete() {
            if (parent != null) {
                parent.deleteChild(this);
                parent = null;
            }
        }

        protected MemoryPath find(String path) {
            while (path.startsWith("/")) {
                path = path.substring(1);
            }
            if (path.equals(name) || path.isEmpty()) {
                return this;
            }
            return null;
        }

        protected abstract void getattr(FileStat stat);

        private void rename(String newName) {
            while (newName.startsWith("/")) {
                newName = newName.substring(1);
            }
            name = newName;
        }
    }

    public static void main(String[] args) {
        MemoryFS memfs = new MemoryFS();
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
