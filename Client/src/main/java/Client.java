import packet.Command;
import packet.Packet;
import packet.User;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class Client {
    private static final int PORT = 6543;
    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private volatile boolean isRunning = true;
    private volatile boolean isAuthenticated = false;
    private User currentUser; // Principal-like variable to retain the user for future requests

    public void start() {
        try {
            this.socket = new Socket("localhost", PORT);
            this.out = new ObjectOutputStream(this.socket.getOutputStream());
            this.in = new ObjectInputStream(this.socket.getInputStream());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        /* Server Thread */
        Thread serverThread = new Thread(() -> {
            try {
                while (isRunning) {
                    Packet packet = (Packet) in.readObject();

                    synchronized (this) {
                        // Handle errors
                        if (packet.getCommand() == null) {
                            System.out.println(packet.getMessage());
                        } else {
                            // If we receive "Success" message, the user is authenticated
                            if ("Success".equals(packet.getMessage())) {
                                this.isAuthenticated = true;
                                this.currentUser = packet.getUser(); // Store the authenticated user
                                System.out.println("Logged in as: " + currentUser.getNickname());
                            }

                            if (packet.getCommand().equals(Command.MESSAGE_ALL)) {
                                String message = "%s: %s ".formatted(packet.getUser().getNickname(), packet.getMessage());
                                System.out.println(message);
                            }
                            if (packet.getCommand().equals(Command.MESSAGE_INDIVIDUAL)) {
                                String message = "Message from %s: %s ".formatted(packet.getUser().getNickname(), packet.getMessage());
                                System.out.println(message);
                            }
                            if (packet.getCommand().equals(Command.JOIN_ROOM)) {
                                String message = "Joined the room: %s ".formatted(packet.getMessage());
                                currentUser.setCurrentRoom(packet.getMessage());
                                System.out.println(message);
                            }

                            if (packet.getCommand().equals(Command.EXIT_ROOM)) {
                                String message = "Room left";
                                currentUser.setCurrentRoom(null);
                                System.out.println(message);
                            }
                        }
                        this.notify(); // Notify the waiting thread that a server response was received
                    }
                }
            } catch (IOException | ClassNotFoundException e) {
                System.out.println("Server connection lost.");
            } finally {
                closeConnection();
            }
        });
        serverThread.start();

        /* Client Input */
        new Thread(() -> {
            /* Main Menu Loop - Authentication Phase */
            Scanner scanner = new Scanner(System.in);
            while (isRunning && !isAuthenticated) {  // Loop until user is authenticated or exits
                showAuthMenu();
                String option = scanner.nextLine().toLowerCase();

                switch (option) {
                    case "1", "login" -> {
                        login(scanner);
                        waitForServerResponse();  // Wait for the server response after login
                    }
                    case "2", "register" -> {
                        register(scanner);
                        waitForServerResponse();  // Wait for the server response after register
                    }
                    case "exit" -> exit();
                    default -> System.out.println("Invalid option. Please try again.");
                }
            }

            /* Messaging Phase */
            while (isRunning && isAuthenticated) {
                if (currentUser.getCurrentRoom() == null) {
                    showMessagingMenu();
                    String option = scanner.nextLine().toLowerCase();

                    switch (option) {
                        case "1", "message all" -> messageAll(scanner);
                        case "2", "message individual" -> messageIndividual(scanner);
                        case "3", "join room" -> joinRoom(scanner);
                        case "exit" -> exit();
                        default -> System.out.println("Invalid option. Please try again.");
                    }
                }
                else {
                    showRoomMenu();
                    String option = scanner.nextLine().toLowerCase();

                    switch (option) {
                        case "1", "message room" -> messageRoom(scanner);
                        case "2", "exit room" -> exitRoom();
                        case "exit" -> exit();
                        default -> System.out.println("Invalid option. Please try again.");
                    }
                }
            }


            scanner.close();
        }).start();
    }

    private void showAuthMenu() {
        System.out.println("""
                Options:
                1. LOGIN
                2. REGISTER
                Type 'exit' to quit.
                Choose: """);
    }

    private void showMessagingMenu() {
        System.out.println("""
                Messaging Options:
                1. Message All (Public Chat)
                2. Message Individual
                3. Join Room
                Type 'exit' to quit.
                Choose: """);
    }

    private void showRoomMenu() {
        System.out.println("""
                Messaging Options:
                1. Message room
                2. Exit room
                Type 'exit' to quit.
                Choose: """);
    }

    private void login(Scanner scanner) {
        System.out.print("Enter username: ");
        String username = scanner.nextLine();
        System.out.print("Enter password: ");
        String password = scanner.nextLine();

        User loginUser = User.builder().nickname(username).password(password).build();

        try {
            out.writeObject(Packet
                    .builder()
                    .user(loginUser)
                    .command(Command.LOGIN)
                    .build());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void register(Scanner scanner) {
        System.out.print("Enter new username: ");
        String username = scanner.nextLine();
        System.out.print("Enter new password: ");
        String password = scanner.nextLine();

        User newUser = User.builder().nickname(username).password(password).build();

        try {
            out.writeObject(Packet
                    .builder()
                    .user(newUser)
                    .command(Command.REGISTER)
                    .build());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void messageAll(Scanner scanner) {
        System.out.print("Enter message to send to all: ");
        String message = scanner.nextLine();

        try {
            out.writeObject(Packet
                    .builder()
                    .message(message)
                    .user(currentUser)  // Use the retained User (principal)
                    .command(Command.MESSAGE_ALL)
                    .build());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void messageIndividual(Scanner scanner) {
        System.out.print("Enter recipient username: ");
        String recipient = scanner.nextLine();
        System.out.print("Enter message: ");
        String message = scanner.nextLine();

        try {
            List<String> usersToMessage = new ArrayList<String>();
            usersToMessage.add(recipient);

            out.writeObject(Packet
                    .builder()
                    .message(message)
                    .user(currentUser)  // Use the retained User (principal)
                    .recipientUsersName(usersToMessage)
                    .command(Command.MESSAGE_INDIVIDUAL)
                    .build());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void joinRoom(Scanner scanner) {
        System.out.print("Enter room name: ");
        String name = scanner.nextLine();

        try {
            out.writeObject(Packet
                    .builder()
                    .message(name)
                    .user(currentUser)  // Use the retained User (principal)
                    .command(Command.JOIN_ROOM)
                    .build());

            Thread.sleep(1000);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private void exitRoom() {
        try {
            out.writeObject(Packet
                    .builder()
                    .user(currentUser)  // Use the retained User (principal)
                    .command(Command.EXIT_ROOM)
                    .build());

            Thread.sleep(1000);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private void messageRoom(Scanner scanner) {
        System.out.print("Enter message: ");
        String message = scanner.nextLine();

        try {
            out.writeObject(Packet
                    .builder()
                    .message(message)
                    .user(currentUser)  // Use the retained User (principal)
                    .command(Command.MESSAGE_ROOM)
                    .build());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private synchronized void waitForServerResponse() {
        try {
            // Wait until the server responds (notify() will be called in the server thread)
            this.wait();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private void exit() {
        isRunning = false;
        System.out.println("Exiting...");
        closeConnection();
    }

    private void closeConnection() {
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            System.out.println("Error closing connection.");
        }
    }

    public static void main(String[] args) {
        new Client().start();
    }
}
