import java.util.Scanner;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.net.Socket;
import java.io.IOException;

public class FileClient {
    public static void main(String[] args) {
        String host = "127.0.0.1";
        int port = 5000;

        try (Socket socket = new Socket(host, port)) {
            System.out.println("Connected to the server!");
	    DataInputStream input = new DataInputStream(socket.getInputStream());

	    DataOutputStream output = new DataOutputStream(socket.getOutputStream());

	   Scanner keyboard = new Scanner(System.in);

System.out.print("Enter the full path of the file to upload: ");
String filePath = keyboard.nextLine().strip();

// Allow paths copied with surrounding double quotes.
if (filePath.startsWith("\"") && filePath.endsWith("\"")
        && filePath.length() >= 2) {
    filePath = filePath.substring(1, filePath.length() - 1);
}

Path source = Path.of(filePath);

if (!Files.isRegularFile(source)) {
    System.out.println("That path does not point to a regular file.");
    return;
}

try (InputStream file = Files.newInputStream(source)) {
    long fileSize = Files.size(source);

    output.writeUTF("UPLOAD");
    output.writeUTF(source.getFileName().toString());
    output.writeLong(fileSize);
    output.flush();

    String response = input.readUTF();
    System.out.println("Server response: " + response);

    if ("READY".equals(response)) {
        byte[] buffer = new byte[8192];
        long remaining = fileSize;

        while (remaining > 0) {
            int bytesToRead = (int) Math.min(buffer.length, remaining);
            int bytesRead = file.read(buffer, 0, bytesToRead);

            if (bytesRead == -1) {
                throw new IOException("Source file ended before expected");
            }

            output.write(buffer, 0, bytesRead);
            remaining -= bytesRead;
        }

        output.flush();
        System.out.println("Upload result: " + input.readUTF());
    }
}
        } catch (IOException e) {
            System.err.println("Client error: " + e.getMessage());
        }
    }
}