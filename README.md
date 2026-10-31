# Java File Upload Server

This project demonstrates TCP networking with a Java client running on Windows and a Java server running on Ubuntu Server inside a VirtualBox virtual machine. I built it to learn how to establish connections, exchange requests and responses, transfer file bytes, and accept another client after a disconnect.

The server supports `PING`, `LIST`, and `UPLOAD` requests. The current client prompts for a local file path and uploads that file to the server's `shared` directory. It transfers data in 8 KB chunks and waits for the server to confirm completion. The server validates filenames, rejects negative file sizes and duplicate filenames, and removes incomplete uploads when a transfer fails.

The application currently handles one client at a time. Downloads, authentication, and transfer encryption are not implemented. SSH is used for terminal access to Ubuntu; it does not encrypt the separate Java connection on port `5000`.

## Instructions for Build and Use

Steps to build and/or run the software:

1. Install a Java 25 JDK on Windows and Ubuntu. Confirm that `java --version` and `javac --version` work on both systems.
2. Configure the Ubuntu VM's network adapter to use NAT. In VirtualBox's port-forwarding settings, add a TCP rule with host IP `127.0.0.1`, host port `5000`, a blank guest IP, and guest port `5000`.
3. Place `FileServer.java` in `~/file-server` inside Ubuntu. The server source currently resides in the development VM and must be copied into this project before the project can be reproduced from this folder alone.
4. In an Ubuntu terminal, create the storage folder, compile the server, and start it:

   ```bash
   cd ~/file-server
   mkdir -p shared
   javac FileServer.java
   java FileServer
   ```

   Run the server from `~/file-server` because `shared` is a relative path. Wait for `Listening on port 5000`.

5. In Windows PowerShell, open the folder containing `FileClient.java` and compile it:

   ```powershell
   javac FileClient.java
   ```

Optional SSH access: install and enable OpenSSH Server in Ubuntu, then add a separate VirtualBox TCP forwarding rule from `127.0.0.1:2222` to guest port `22`. Connect from Windows with `ssh -p 2222 YOUR_UBUNTU_USERNAME@127.0.0.1`.

Instructions for using the software:

1. Leave the Linux server running. In the Windows project folder, run:

   ```powershell
   java FileClient
   ```

2. At the prompt, enter the full path of a file, such as `C:\Users\corbi\Downloads\report.pdf`. Paths containing spaces are supported, and surrounding double quotes are removed automatically. The client checks that the path points to a regular file.
3. For an accepted upload, the client displays `Server response: READY`, followed by `Upload result: OK`. The file is stored as `shared/report.pdf` on Linux; the Windows directory path is not sent to the server.
4. Run the client again to upload another file. If its filename already exists on the server, the server responds with `ERROR: File already exists` and preserves the existing file. Successful uploads and duplicate rejection were tested during development.
5. Inspect uploaded files from another Ubuntu terminal with `ls ~/file-server/shared`. Stop the server with **Ctrl+C** when finished.

The server's other supported requests are `PING` (returns `PONG`) and `LIST` (returns `OK` with a file count, followed by filename messages). These were tested while building the project; the current client interface sends only `UPLOAD`.

## Development Environment

To recreate the development environment, you need the following software and/or libraries with the specified versions:

* Windows host with PowerShell and Eclipse Temurin JDK **25.0.5**.
* Oracle VirtualBox with an Ubuntu Server **26.04 LTS** guest. The development VM was configured with 4 GB RAM, 2 virtual processors, and a 25 GB dynamically allocated disk. The exact VirtualBox version was not recorded.
* OpenJDK and `javac` **25.0.4.1** on Ubuntu.
* OpenSSH Server on Ubuntu for remote terminal access from Windows.
* Java standard-library classes, including `Socket`, `ServerSocket`, `DataInputStream`, `DataOutputStream`, `Files`, `Path`, and `Scanner`. No third-party Java libraries or build tools are required.
* Nano on Ubuntu and Notepad on Windows were used to edit the source files.

## Useful Websites to Learn More

I found these websites useful in developing this software:

* [CSE 310 Module Descriptions](https://macbeth-byui.github.io/cse310-course/module_descriptions.html) — networking requirements and stretch challenges.
* [Java Socket Documentation](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/net/Socket.html) — client connections and socket communication.
* [Java DataInputStream Documentation](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/io/DataInputStream.html) — reading messages, file sizes, and bytes sent by a data output stream.
* [VirtualBox Virtual Networking](https://docs.oracle.com/en/virtualization/virtualbox/7.2/user/networkingdetails.html) — NAT networking and port forwarding between Windows and the Linux VM.
* [Ubuntu Server Documentation](https://ubuntu.com/server/docs/) — installing and configuring the Linux server environment.

## Future Work

The following items I plan to fix, improve, and/or add to this project in the future:

* [ ] Copy `FileServer.java` from the Linux VM into this project so both programs are available together.
* [ ] Add file downloads and a client menu for selecting upload, list, and connection-test requests.
* [ ] Add authentication and TLS encryption for file transfers.
* [ ] Support multiple connected clients at the same time.
* [ ] Improve invalid-path handling and add connection/read timeouts and upload-size limits.
* [ ] Add automated tests for binary files, empty files, interrupted uploads, and duplicate filenames.
