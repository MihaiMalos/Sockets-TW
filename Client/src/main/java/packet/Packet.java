package packet;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
@Builder /* https://refactoring.guru/design-patterns/builder */
public class Packet implements Serializable {
    private String message;
    private Command command;
    private User user;
    private List<String> recipientUsersName;
}
