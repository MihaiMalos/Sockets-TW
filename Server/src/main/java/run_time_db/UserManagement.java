package run_time_db;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import packet.Command;
import packet.Packet;
import packet.User;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.lang.reflect.Type;
import java.sql.Array;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public enum UserManagement {
    INSTANCE;

    public List<User> users;
    private final String filePath;
    private final Gson gson;

    UserManagement() {
        this.filePath = "users.json";
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        this.users = loadUsersFromFile();
        if (this.users == null) users = new ArrayList<>();
    }

    public void register(User user) {
        users.add(user);
        saveUsersToFile();
    }

    private List<User> loadUsersFromFile() {
        users = new ArrayList<>();
        try (FileReader reader = new FileReader(filePath)) {
            Type userListType = new TypeToken<List<User>>() {}.getType();
            return gson.fromJson(reader, userListType);
        } catch (IOException e) {
            e.printStackTrace();
            return new ArrayList<>(); // Return an empty list if file doesn't exist or an error occurs
        }
    }

    private void saveUsersToFile() {
        try (FileWriter writer = new FileWriter(filePath)) {
            gson.toJson(users, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public Optional<User> login(User userToLogin) {
        return this.users
                .stream()
                .filter(user -> user.equals(userToLogin))
                .findFirst();
    }

    public void broadcastMessage(Packet packet) {
        for (User user : users) {
            /* Do not send the message back to the user who sent it */
            boolean isNotSameUser = !packet.getUser().getNickname().equals(user.getNickname());

            if (Objects.nonNull(user.getSocket()) && isNotSameUser) {
                User cleanUser = User.builder()
                        .nickname(packet.getUser().getNickname())
                        .build();

                Packet messagePacket = Packet.builder()
                        .user(cleanUser)
                        .message(packet.getMessage())
                        .command(Command.MESSAGE_ALL)
                        .build();

                try {
                    ObjectOutputStream userOutStream = user.getOutStream();
                    if (userOutStream != null) {
                        userOutStream.writeObject(messagePacket);  // Use the user's existing stream
                        userOutStream.flush();
                    }
                } catch (IOException e) {
                    throw new RuntimeException("Error sending message to user: " + user.getNickname(), e);
                }
            }
        }
    }
}