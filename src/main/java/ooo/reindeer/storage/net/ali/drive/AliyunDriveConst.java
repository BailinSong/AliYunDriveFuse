package ooo.reindeer.storage.net.ali.drive;

/**
 * @ClassName AliyunDriveConst
 * @Author songbailin
 * @Date 2021/8/18 11:17
 * @Version 1.0
 * @Description TODO
 */
public interface AliyunDriveConst {
    String MATE_DOMAIN_ID = "DomainId";
    String MATE_DRIVE_ID = "DriveId";
    String MATE_PARENT_FILE_ID = "ParentFileId";
    String MATE_FILE_ID = "FileId";
    String MATE_UPLOAD_ID="UploadId";
    String MATE_TYPE = "Type";
    String MATE_NAME = "Name";
    String MATE_STATUS = "Status";
    String MATE_SIZE = "Size";
    String MATE_CATEGORY = "Category";
    String MATE_LABELS = "Labels";
    String MATE_UPDATED_AT = "UpdatedAt";
    String MATE_CREATED_AT = "CreatedAt";

    String S_AVAILABLE = "available";
    String S_UPLOADING = "uploading*";

    String T_SYMLINK = ".$symlink$";
    String T_FILE = "file";
    String T_FOLDER = "folder";

    int BLOCK_SIZE = 4 * 1024;
}
