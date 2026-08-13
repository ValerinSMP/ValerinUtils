package me.mtynnn.valerinutils.network;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

final class RedisBus implements Closeable {
    private final CrossServerConfig config;
    private final String channel;
    private final Consumer<String> receiver;
    private volatile boolean running;
    private volatile Socket subscriber;
    private Thread thread;

    RedisBus(CrossServerConfig config, Consumer<String> receiver) {
        this.config = config;
        this.channel = config.namespace() + ":events";
        this.receiver = receiver;
    }

    void start() throws IOException {
        if (!ping()) throw new IOException("Redis PING failed");
        running = true;
        thread = Thread.ofPlatform().name("valerinutils-redis").daemon(true).start(this::subscribeLoop);
    }

    boolean ping() {
        try (Connection connection = connect()) {
            return "PONG".equals(connection.command("PING"));
        } catch (IOException error) {
            return false;
        }
    }

    boolean publish(String message) {
        try (Connection connection = connect()) {
            Object result = connection.command("PUBLISH", channel, message);
            return result instanceof Long;
        } catch (IOException error) {
            return false;
        }
    }

    private void subscribeLoop() {
        while (running) {
            try (Connection connection = connect()) {
                subscriber = connection.socket;
                connection.write("SUBSCRIBE", channel);
                while (running) {
                    Object response = connection.read();
                    if (response instanceof Object[] values && values.length == 3
                            && "message".equals(values[0]) && values[2] instanceof String message) {
                        receiver.accept(message);
                    }
                }
            } catch (IOException ignored) {
                if (running) {
                    try { Thread.sleep(1000); } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            } finally {
                subscriber = null;
            }
        }
    }

    private Connection connect() throws IOException {
        Connection connection = new Connection(config.redisHost(), config.redisPort(), config.redisTimeoutMs());
        if (!config.redisPassword().isEmpty()) connection.expectOk("AUTH", config.redisPassword());
        if (config.redisDatabase() != 0) connection.expectOk("SELECT", String.valueOf(config.redisDatabase()));
        return connection;
    }

    @Override
    public void close() {
        running = false;
        Socket socket = subscriber;
        if (socket != null) try { socket.close(); } catch (IOException ignored) { }
        if (thread != null) thread.interrupt();
    }

    private static final class Connection implements Closeable {
        private final Socket socket = new Socket();
        private final BufferedInputStream input;
        private final BufferedOutputStream output;

        Connection(String host, int port, int timeout) throws IOException {
            socket.connect(new InetSocketAddress(host, port), timeout);
            socket.setSoTimeout(0);
            input = new BufferedInputStream(socket.getInputStream());
            output = new BufferedOutputStream(socket.getOutputStream());
        }

        Object command(String... values) throws IOException {
            write(values);
            return read();
        }

        void expectOk(String... values) throws IOException {
            if (!"OK".equals(command(values))) throw new IOException("Redis rejected " + values[0]);
        }

        void write(String... values) throws IOException {
            output.write(("*" + values.length + "\r\n").getBytes(StandardCharsets.UTF_8));
            for (String value : values) {
                byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
                output.write(("$" + bytes.length + "\r\n").getBytes(StandardCharsets.UTF_8));
                output.write(bytes);
                output.write("\r\n".getBytes(StandardCharsets.UTF_8));
            }
            output.flush();
        }

        Object read() throws IOException {
            int prefix = input.read();
            if (prefix < 0) throw new EOFException();
            String line = line();
            return switch (prefix) {
                case '+' -> line;
                case ':' -> Long.parseLong(line);
                case '-' -> throw new IOException("Redis: " + line);
                case '$' -> bulk(Integer.parseInt(line));
                case '*' -> array(Integer.parseInt(line));
                default -> throw new IOException("Invalid Redis response");
            };
        }

        private String bulk(int length) throws IOException {
            if (length < 0) return null;
            byte[] bytes = input.readNBytes(length);
            if (bytes.length != length) throw new EOFException();
            input.readNBytes(2);
            return new String(bytes, StandardCharsets.UTF_8);
        }

        private Object[] array(int length) throws IOException {
            Object[] values = new Object[length];
            for (int index = 0; index < length; index++) values[index] = read();
            return values;
        }

        private String line() throws IOException {
            StringBuilder line = new StringBuilder();
            int previous = -1;
            for (int current; (current = input.read()) >= 0; previous = current) {
                if (previous == '\r' && current == '\n') {
                    line.setLength(line.length() - 1);
                    return line.toString();
                }
                line.append((char) current);
            }
            throw new EOFException();
        }

        @Override public void close() throws IOException { socket.close(); }
    }
}
