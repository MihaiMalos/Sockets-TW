package run_time_db;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import jdk.jfr.Frequency;
import packet.Command;
import packet.Packet;
import packet.User;

import java.io.*;
import java.lang.reflect.Type;
import java.sql.Array;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public enum UserManagement {
    INSTANCE;

    public List<User> users;
    private final List<String> rooms;
    private final String filePath;
    private final Gson gson;

    UserManagement() {
        this.filePath = "users.json";
        this.gson = new GsonBuilder()
                .setPrettyPrinting()
                .excludeFieldsWithoutExposeAnnotation()
                .create();
        this.users = loadUsersFromFile();
        if (this.users == null) users = new ArrayList<>();
        this.rooms = List.of(
                "Room1",
                "Room2"
        );
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

                sendPacket(messagePacket, user.getOutStream());
            }
        }
    }

    public void individualMessage(Packet receivedPacket) {
        boolean success = false;
        List<User> users = this.users
                .stream()
                .filter(user -> receivedPacket.getRecipientUsersName().contains(user.getNickname()))
                .collect(Collectors.toList());

        User requesterUser = getUserFromList(receivedPacket.getUser());

        if (users.size() == 1) {
            for (User user : users) {
                if (user.getOutStream() == null) continue;

                Packet packet = Packet.builder()
                        .user(receivedPacket.getUser())
                        .message(receivedPacket.getMessage())
                        .command(Command.MESSAGE_INDIVIDUAL)
                        .build();

                sendPacket(packet, user.getOutStream());
                success = true;
            }
        }
        if (!success && requesterUser != null) {
            ObjectOutputStream outputStream = requesterUser.getOutStream();
            Packet packet = Packet.builder().message("User not found").build();
            sendPacket(packet, outputStream);
        }
    }

    public void joinRoom(Packet receivedPacket) {
        User userInList = getUserFromList(receivedPacket.getUser());
        String room = receivedPacket.getMessage();
        boolean roomExists = rooms.contains(room);
        if (roomExists && userInList != null) {
            userInList.setCurrentRoom(room);

            Packet packet = Packet.builder()
                    .user(userInList.clone())
                    .command(Command.JOIN_ROOM)
                    .build();

            sendPacket(packet, userInList.getOutStream());
        } else {
            Packet packet = Packet.builder().message("Room not found").build();
            sendPacket(packet, userInList.getOutStream());
        }
    }

    public void messageRoom(Packet receivedPacket) {
        String room = receivedPacket.getUser().getCurrentRoom();

        List<User> users = this.users
                .stream()
                .filter(user -> user.getCurrentRoom() != null && user.getCurrentRoom().contains(room))
                .collect(Collectors.toList());

        for (User user : users) {
            Packet packet = Packet.builder()
                    .user(receivedPacket.getUser())
                    .message(receivedPacket.getMessage())
                    .command(Command.MESSAGE_ROOM)
                    .build();

            sendPacket(packet, user.getOutStream());
        }
    }

    private User getUserFromList(User user) {
        Optional<User> requesterUser = this.users
                .stream()
                .filter(currentUser -> currentUser.equals(user))
                .findFirst();
        return requesterUser.orElse(null);
    }

    public void exitRoom(Packet receivedPacket) {
        User userInList = getUserFromList(receivedPacket.getUser());
        userInList.setCurrentRoom(null);
        Packet packet = Packet.builder()
                .user(userInList.clone())
                .command(Command.EXIT_ROOM)
                .build();

        sendPacket(packet, userInList.getOutStream());
    }

    private void sendPacket(Packet packet, ObjectOutputStream output) {
        try {
            output.writeObject(packet);
            output.flush();
        } catch (IOException e) {
            throw new RuntimeException("Error sending packet", e);
        }
    }

}