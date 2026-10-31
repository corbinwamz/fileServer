import java.io.OutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.LinkOption;

public class FileServer {
    public static void main(String[] args) {
        int port = 5000;
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Listening on port " + port);
            while (true) {
                try (Socket clientSocket = serverSocket.accept()) {
                    System.out.println("Client connected: " + clientSocket.getInetAddress());
                    DataInputStream input = new DataInputStream(clientSocket.getInputStream());
                    DataOutputStream output = new DataOutputStream(clientSocket.getOutputStream());
                    String request = input.readUTF();
                    System.out.println("Request received: " + request);
                    if ("PING".equals(request)) {
                        output.writeUTF("PONG");
                    } else if ("UPLOAD".equals(request)) {
                        receiveUpload(input, output);
                    } else if ("LIST".equals(request)) {
                        try (var files = Files.list(Path.of("shared"))) {
                            String[] names = files.filter(Files::isRegularFile)
                                .map(path -> path.getFileName().toString())
                                .sorted().toArray(String[]::new);
                            output.writeUTF("OK " + names.length);
                            for (String name : names) output.writeUTF(name);
                        }
                    } else {
                        output.writeUTF("ERROR: Unknown request");
                    }
                    output.flush();
                } catch (IOException e) {
                    System.err.println("Client error: " + e.getMessage());
                }
                System.out.println("Waiting for another client...");
            }
        } catch (IOException e) {
            System.err.println("Server error: " + e.getMessage());
        }
    }

    private static void receiveUpload(DataInputStream input, DataOutputStream output)
            throws IOException {
        String fileName = input.readUTF();
        long fileSize = input.readLong();
        System.out.println("Upload requested: " + fileName);
        System.out.println("File size: " + fileSize + " bytes");
        if (fileName.isBlank() || fileName.equals(".") || fileName.equals("..")
                || fileName.contains("/") || fileName.contains("\\")
                || fileName.indexOf('\0') >= 0) {
            output.writeUTF("ERROR: Invalid filename");
            return;
        }
        if (fileSize < 0) {
            output.writeUTF("ERROR: Invalid file size");
            return;
        }
        Path destination = Path.of("shared").resolve(fileName);
        if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
            output.writeUTF("ERROR: File already exists");
            return;
        }
        boolean created = false;
        boolean complete = false;
        try {
            try (OutputStream file = Files.newOutputStream(destination,
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                created = true;
                output.writeUTF("READY");
                output.flush();
                byte[] buffer = new byte[8192];
                long remaining = fileSize;
                while (remaining > 0) {
                    int bytesRead = input.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                    if (bytesRead == -1) throw new IOException("Client disconnected during upload");
                    file.write(buffer, 0, bytesRead);
                    remaining -= bytesRead;
                }
            }
            complete = true;
        } finally {
            if (created && !complete) Files.deleteIfExists(destination);
        }
        output.writeUTF("OK");
        System.out.println("Upload saved: " + destination);
    }
}
