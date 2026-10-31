import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.FileAlreadyExistsException;

public class WebServer {
    private static final long MAX_UPLOAD_BYTES = 100L * 1024 * 1024;

    public static void main(String[] args) throws IOException {
        Files.createDirectories(Path.of("shared"));
        int port = args.length == 0 ? 8080 : Integer.parseInt(args[0]);
        // Bind on the VM's interfaces so VirtualBox port forwarding can reach it.
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/upload", WebServer::receiveUpload);
        server.createContext("/files", exchange -> {
            try (exchange) {
                if (!exchange.getRequestURI().getPath().equals("/files")) {
                    reply(exchange, 404, "Page not found.");
                    return;
                }
                if (!exchange.getRequestMethod().equals("GET")) {
                    exchange.getResponseHeaders().set("Allow", "GET");
                    reply(exchange, 405, "Use GET to list files.");
                    return;
                }
                exchange.getResponseHeaders().set("Cache-Control", "no-store");
                try (var files = Files.list(Path.of("shared"))) {
                    String names = files
                        .filter(path -> Files.isRegularFile(path, java.nio.file.LinkOption.NOFOLLOW_LINKS))
                        .map(path -> path.getFileName().toString())
                        .sorted()
                        .collect(java.util.stream.Collectors.joining("\n"));

                    reply(exchange, 200, names);
                } catch (IOException e) {
                    reply(exchange, 500, "Unable to read the shared folder.");
                }
            }
        });

        server.createContext("/", exchange -> {
            try (exchange) {
                if (!exchange.getRequestURI().getPath().equals("/")) {
                    exchange.sendResponseHeaders(404, -1);
                    return;
                }
                if (!exchange.getRequestMethod().equals("GET")) {
                    exchange.getResponseHeaders().set("Allow", "GET");
                    exchange.sendResponseHeaders(405, -1);
                    return;
                }
                String page = """
                    <!doctype html>
                    <html lang="en">
                    <head>
                      <meta charset="utf-8">
                      <meta name="viewport" content="width=device-width, initial-scale=1">
                      <title>My File Server</title>
                      <style>
                        body { margin: 0; padding: 24px; background: #f1f5f9;
                               color: #172337; font: 18px/1.6 system-ui, sans-serif; }
                        main { max-width: 640px; margin: 12vh auto; padding: 32px;
                               background: white; border-radius: 20px; }
                        h1 { margin-top: 0; }
                        p { color: #475569; }
                        .status { color: #166534; font-weight: 600; }
                        button { padding: 10px 20px; margin-top: 16px; cursor: pointer;
                                 background: #1d4ed8; color: white; border: 0; border-radius: 8px; }
                        button:disabled { opacity: .5; cursor: default; }
                        input { display: block; max-width: 100%; margin-top: 8px; }
                        #drop-zone { border: 2px dashed #94a3b8; padding: 24px; border-radius: 12px; }
                        #drop-zone.dragging { border-color: #1d4ed8; background: #eff6ff; }
                        li { overflow-wrap: anywhere; padding: 6px 0; }
                      </style>
                    </head>
                    <body>
                      <main>
                        <h1>My File Server</h1>
                        <p class="status">Connected to the web server</p>
                        <p>Choose a file to save on your server. Maximum size: 100 MB.</p>
                        <form id="upload-form">
                          <div id="drop-zone">
                          <p>Drop files here, or choose files below.</p>
                          <label for="file">File to upload</label>
                          <input id="file" type="file" multiple required>
                          </div>
                          <button id="upload" type="submit">Upload file</button>
                        </form>
                        <p id="result" role="status" aria-live="polite"></p>
                        <h2>Files on your server</h2>
                        <button id="refresh" type="button">Refresh files</button>
                        <p id="files-status" role="status" aria-live="polite"></p>
                        <ul id="file-list"></ul>
                      </main>
                      <script>
                        const form = document.querySelector('#upload-form');
                        const picker = document.querySelector('#file');
                        const button = document.querySelector('#upload');
                        const result = document.querySelector('#result');
                        const zone = document.querySelector('#drop-zone');
                        const refresh = document.querySelector('#refresh');
                        const list = document.querySelector('#file-list');
                        const filesStatus = document.querySelector('#files-status');
                        let uploading = false;
                        async function loadFiles() {
                          refresh.disabled = true;
                          filesStatus.textContent = 'Loading files…';
                          list.replaceChildren();
                          try {
                            const response = await fetch('/files', {cache: 'no-store'});
                            if (!response.ok) throw new Error('Listing failed');
                            const text = await response.text();
                            const names = text === '' ? [] : text.split('\\n');
                            for (const name of names) {
                              const item = document.createElement('li');
                              item.textContent = name;
                              list.append(item);
                            }
                            filesStatus.textContent = names.length ? names.length + ' file(s)' : 'No files uploaded yet.';
                          } catch (error) {
                            filesStatus.textContent = 'Unable to load files. Try refreshing.';
                          } finally {
                            refresh.disabled = false;
                          }
                        }
                        refresh.addEventListener('click', loadFiles);
                        loadFiles();
                        form.addEventListener('submit', async (event) => {
                          event.preventDefault();
                          await uploadFiles(Array.from(picker.files));
                        });
                        for (const eventName of ['dragover', 'drop']) {
                          window.addEventListener(eventName, event => event.preventDefault());
                        }
                        zone.addEventListener('dragover', () => zone.classList.add('dragging'));
                        zone.addEventListener('dragleave', () => zone.classList.remove('dragging'));
                        zone.addEventListener('drop', async event => {
                          zone.classList.remove('dragging');
                          await uploadFiles(Array.from(event.dataTransfer.files));
                        });
                        async function uploadFiles(files) {
                          if (uploading || files.length === 0) return;
                          uploading = true;
                          button.disabled = true;
                          picker.disabled = true;
                          const messages = [];
                          try {
                          for (const file of files) {
                          if (file.size > 100 * 1024 * 1024) {
                            messages.push(file.name + ': exceeds the 100 MB limit.');
                            continue;
                          }
                          button.disabled = true;
                          result.textContent = 'Uploading ' + file.name + '…';
                          try {
                            const response = await fetch('/upload?name=' + encodeURIComponent(file.name), {
                              method: 'POST',
                              headers: {'Content-Type': 'application/octet-stream'},
                              body: file
                            });
                            const message = await response.text();
                            messages.push(message);
                          } catch (error) {
                            messages.push(file.name + ': connection lost. Refresh the list before retrying.');
                          }
                          }
                          form.reset();
                          await loadFiles();
                          } finally {
                            result.textContent = messages.join(' ');
                            button.disabled = false;
                            picker.disabled = false;
                            uploading = false;
                          }
                        }
                      </script>
                    </body>
                    </html>
                    """;
                byte[] response = page.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
                exchange.sendResponseHeaders(200, response.length);
                exchange.getResponseBody().write(response);
            }
        });
        server.start();
        System.out.println("Website listening on port " + port + ". Stop with Ctrl+C.");
    }

    private static void receiveUpload(HttpExchange exchange) throws IOException {
        try (exchange) {
            if (!exchange.getRequestURI().getPath().equals("/upload")) {
                reply(exchange, 404, "Page not found.");
                return;
            }
            if (!exchange.getRequestMethod().equals("POST")) {
                exchange.getResponseHeaders().set("Allow", "POST");
                reply(exchange, 405, "Use POST to upload a file.");
                return;
            }
            String query = exchange.getRequestURI().getRawQuery();
            String name;
            try {
                if (query == null || !query.startsWith("name=")) {
                    reply(exchange, 400, "A filename is required.");
                    return;
                }
                name = URLDecoder.decode(query.substring(5), StandardCharsets.UTF_8);
                if (name.isBlank() || name.equals(".") || name.equals("..")
                        || name.contains("/") || name.contains("\\")
                        || name.chars().anyMatch(Character::isISOControl)) {
                    reply(exchange, 400, "Invalid filename.");
                    return;
                }
                Path.of(name);
            } catch (IllegalArgumentException e) {
                reply(exchange, 400, "Invalid filename.");
                return;
            }

            Path destination = Path.of("shared").resolve(name);
            boolean created = false;
            boolean complete = false;
            try {
                try (var file = Files.newOutputStream(destination,
                        StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                    created = true;
                    byte[] buffer = new byte[8192];
                    long total = 0;
                    int count;
                    while ((count = exchange.getRequestBody().read(buffer)) != -1) {
                        total += count;
                        if (total > MAX_UPLOAD_BYTES) {
                            reply(exchange, 413, "File exceeds the 100 MB limit.");
                            return;
                        }
                        file.write(buffer, 0, count);
                    }
                }
                complete = true;
            } catch (FileAlreadyExistsException e) {
                reply(exchange, 409, "A file with that name already exists.");
                return;
            } catch (IOException e) {
                System.err.println("Upload failed: " + e.getMessage());
                reply(exchange, 500, "Upload failed. Please try again.");
                return;
            } finally {
                if (created && !complete) Files.deleteIfExists(destination);
            }
            reply(exchange, 201, "Uploaded " + name + " successfully.");
        }
    }

    private static void reply(HttpExchange exchange, int status, String message) throws IOException {
        byte[] bytes = message.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }
}
