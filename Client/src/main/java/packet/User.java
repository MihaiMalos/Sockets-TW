package packet;

import com.google.gson.annotations.Expose;
import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.net.Socket;

import java.io.ObjectOutputStream;
import java.util.Objects;

@Data
@Builder
public class User implements Serializable, Cloneable {
    @Expose
    private String nickname;
    @Expose
    private String password;
    private String currentRoom;

    private transient Socket socket; // Marked transient because socket is not serializable
    private transient ObjectOutputStream outStream; // Marked transient because streams are not serializable

    // Clone method
    @Override
    public User clone() {
        try {
            return (User) super.clone(); // Shallow copy
        } catch (CloneNotSupportedException e) {
            // Fallback to manual cloning with the copy constructor
            return User.builder()
                    .nickname(nickname)
                    .currentRoom(currentRoom)
                    .password(password)
                    .build();
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        User user = (User) o;
        return Objects.equals(nickname, user.nickname) && Objects.equals(password, user.password);
    }

    @Override
    public int hashCode() {
        return Objects.hash(nickname, password);
    }
}