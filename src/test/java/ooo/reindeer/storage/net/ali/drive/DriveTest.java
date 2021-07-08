package ooo.reindeer.storage.net.ali.drive;


import com.aliyun.pds.client.models.*;
import com.aliyun.tea.TeaException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;


public class DriveTest {
    ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void testDrive() {
        try {
            Config config = new Config();

            config.protocol = "https";
            config.refreshToken = "7e0e98aea20a4339b9c2d018efc2475a";

            DriveClient client = new DriveClient(config);
            AccountTokenRequest tokenRequest = new AccountTokenRequest();
            tokenRequest.setRefreshToken(config.refreshToken);
            tokenRequest.setGrantType("refresh_token");
            AccountTokenModel tokenResponse;
            tokenResponse = client.accountToken(tokenRequest);

            System.out.println(objectMapper.writeValueAsString(tokenResponse));


            GetUserRequest userRequest = new GetUserRequest();
            userRequest.setUserId("a7e88f230d9e49c9be6a261e88a2e1df");
            GetUserModel getUserModel = client.getUser(userRequest);

            System.out.println(objectMapper.writeValueAsString(getUserModel));


            ListFileRequest listFileRequest=new ListFileRequest();
            listFileRequest.setDriveId(tokenResponse.body.getDefaultDriveId());
            listFileRequest.setMarker("");
            listFileRequest.setParentFileId("root");

            ListFileModel listFileResponse=client.listFile(listFileRequest);
            System.out.println(objectMapper.writeValueAsString(listFileResponse));



            GetDriveRequest getDriveRequest=new GetDriveRequest();
            getDriveRequest.setDriveId(tokenResponse.body.getDefaultDriveId());

            GetDriveModel getDriveModel=client.getDrive(getDriveRequest);
            System.out.println(objectMapper.writeValueAsString(getDriveModel));

        } catch (TeaException e) {
            System.out.println(e.getCode());
            System.out.println(e.getMessage());
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}