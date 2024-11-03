package packet;

import com.google.gson.Gson;
import lombok.Builder;
import lombok.Data;

import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.Socket;

@Data
@Builder /* https://refactoring.guru/design-patterns/builder */
public class User implements Serializable {
    private String nickname;
    private String password;
    private transient Socket socket; // Marked transient because socket is not serializable
    private transient ObjectOutputStream outStream; // Marked transient because streams are not serializable

}