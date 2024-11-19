package com.http;

import java.io.*;
import java.net.Socket;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

public class Client implements Runnable {
    public static final String HTTP_1_1 = "HTTP/1.1";
    public static final String CRLF = "\r\n";

    private static final byte[] HTTP_1_1_BYTES = HTTP_1_1.getBytes();
    private static final byte[] CRLF_BYTES = CRLF.getBytes();
    private static final byte SPACE_BYTE = ' ';
    private static final byte[] COLON_SPACE_BYTE = {':', ' '};

    private static final Pattern ECHO_PATTERN = Pattern.compile("/echo/(.*)");
    private static final Pattern FILE_PATTERN = Pattern.compile("/files/(.*)");

    private static final AtomicInteger ID_INCREMENT = new AtomicInteger();

    private final int id;
    private final Socket socket;

    public Client(Socket socket) {
        this.id = ID_INCREMENT.incrementAndGet();
        this.socket = socket;
    }

    @Override
    public void run() {
        System.out.printf("%d: connected%n", id);

        try (socket) {
            final var inputStream = new BufferedInputStream(socket.getInputStream());
            final var outputStream = new BufferedOutputStream(socket.getOutputStream());

            final var request = parse(inputStream);

            final var response = handle(request);
            send(response, outputStream);
        } catch (IOException ex) {
            System.err.printf("%d: returned an error: %s%n", id, ex.getMessage());
            ex.printStackTrace();
        }
        System.out.printf("%d: disconnected%n", id);
    }

    public Request parse(BufferedInputStream inputStream) throws IOException {
        var line = nextLine(inputStream);

        var scanner = new Scanner(line);

        final var method = Method.valueOf(scanner.next());
        final var path = scanner.next();

        if (!path.startsWith("/")) {
            throw new IllegalStateException("path does not start with a slash: " + path);
        }
        final var version = scanner.next();
        if (!HTTP_1_1.equals(version)) {
            throw new IllegalStateException("unsupported version " + version);
        }
        if (scanner.hasNext()) {
            throw new IllegalStateException("content after version: " + scanner.next());
        }

        final var headers = new Headers();

        while (!(line = nextLine(inputStream)).isEmpty()) {
            final var parts = line.split(":", 2);

            if (parts.length != 2) {
                throw new IllegalStateException("missing header values " + line);
            }

            final var key = parts[0];
            final var value = parts[1].stripLeading();
            headers.put(key, value);
        }

        if (Method.POST.equals(method)) {
            final var contentLength = headers.contentLength();
            final var body = inputStream.readNBytes(contentLength);

            return new Request(method, path, headers, body);
        }
        return new Request(method, path, headers, null);
    }

    public Response handle(Request request) throws IOException {
        return switch (request.method()) {
            case GET -> handleGet(request);
            case POST -> handlePost(request);
        };
    }

    private Response handlePost(Request request) throws IOException {
        {
            final var match = FILE_PATTERN.matcher(request.path());
            if (match.find()) {
                final var path = match.group(1);
                try(final var outputStream = new FileOutputStream(new File(Main.WORKING_DIRECTORY, path))) {
                    outputStream.write(request.body());
                }
                return Response.status(Status.CREATED);
            }
        }
        return Response.status(Status.NOT_FOUND);
    }

    private Response handleGet(Request request) throws IOException {
        if (request.path().equals("/")) {
            return Response.status(Status.OK);
        }

        if (request.path().equals("/user-agent")) {
            final var userAgent = request.headers().userAgent();
            return Response.plainText(userAgent);
        }

        {
            final var matcher = ECHO_PATTERN.matcher(request.path());
            if (matcher.find()) {
                final var message = matcher.group(1);
                return Response.plainText(message);
            }
        }

        {
            final var matcher = FILE_PATTERN.matcher(request.path());
            if (matcher.find()) {
                final var filename = matcher.group(1);
                final var file = new File(Main.WORKING_DIRECTORY, filename);
                return Response.file(file);
            }
        }

        return Response.status(Status.NOT_FOUND);
    }

    private void send(Response response, BufferedOutputStream outputStream) throws IOException {
        outputStream.write(HTTP_1_1_BYTES);
        outputStream.write(SPACE_BYTE);

        outputStream.write(response.status().line().getBytes());
        outputStream.write(CRLF_BYTES);

        for (final var entry : response.headers().entrySet()) {
            final var key = entry.getKey();
            if (Headers.CONTENT_LENGTH.equalsIgnoreCase(key)) {
                continue;
            }

            final var value = entry.getValue();

            outputStream.write(key.getBytes());
            outputStream.write(COLON_SPACE_BYTE);
            outputStream.write(value.getBytes());
            outputStream.write(CRLF_BYTES);
        }

        final var body = response.body();
        if (body != null) {
            outputStream.write(Headers.CONTENT_LENGTH.getBytes());
            outputStream.write(COLON_SPACE_BYTE);
            outputStream.write(String.valueOf(body.length).getBytes());
            outputStream.write(CRLF_BYTES);
        }

        outputStream.write(CRLF_BYTES);
        if (body != null) {
            outputStream.write(body);
        }

        outputStream.flush();
    }

    private String nextLine(BufferedInputStream inputStream) throws IOException {
        final var builder = new StringBuilder();
        var carriageReturn = false;

        int value;
        while ((value = inputStream.read()) != -1) {
            if ('\n' == value && carriageReturn) {
                break;
            } else if ('\r' == value) {
                carriageReturn = true;
            } else {
                if (carriageReturn) {
                    builder.append('\r');
                }
                builder.append((char) value);
                carriageReturn = false;
            }
        }
        return builder.toString();
    }
}
