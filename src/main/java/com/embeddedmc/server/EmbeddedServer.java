package com.embeddedmc.server;

import com.embeddedmc.EmbeddedMC;
import com.embeddedmc.config.ServerInstance;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class EmbeddedServer {
    private static final int MAX_CONSOLE_LINES = 500;

    private final ServerInstance instance;
    private Process process;
    private Thread outputThread;
    private volatile boolean running = false;

    // Console log buffer
    private final LinkedList<String> consoleBuffer = new LinkedList<>();
    private final List<Consumer<String>> consoleListeners = new ArrayList<>();

    public EmbeddedServer(ServerInstance instance) {
        this.instance = instance;
    }

    public void addConsoleListener(Consumer<String> listener) {
        synchronized (consoleListeners) {
            consoleListeners.add(listener);
        }
    }

    public void removeConsoleListener(Consumer<String> listener) {
        synchronized (consoleListeners) {
            consoleListeners.remove(listener);
        }
    }

    public List<String> getConsoleBuffer() {
        synchronized (consoleBuffer) {
            return new ArrayList<>(consoleBuffer);
        }
    }

    private void addConsoleLine(String line) {
        synchronized (consoleBuffer) {
            consoleBuffer.addLast(line);
            while (consoleBuffer.size() > MAX_CONSOLE_LINES) {
                consoleBuffer.removeFirst();
            }
        }
        synchronized (consoleListeners) {
            for (Consumer<String> listener : consoleListeners) {
                try {
                    listener.accept(line);
                } catch (Exception e) {
                    EmbeddedMC.LOGGER.error("Console listener error", e);
                }
            }
        }
    }

    public void sendCommand(String command) {
        if (!running || process == null) {
            return;
        }
        try {
            OutputStream os = process.getOutputStream();
            os.write((command + "\n").getBytes());
            os.flush();
            addConsoleLine("> " + command);
        } catch (IOException e) {
            EmbeddedMC.LOGGER.error("Failed to send command", e);
        }
    }

    public boolean start(Runnable onReady) {
        if (running) {
            EmbeddedMC.LOGGER.warn("Server already running");
            return false;
        }

        if (!Files.exists(instance.getServerJar())) {
            EmbeddedMC.LOGGER.error("Server JAR not found: {}", instance.getServerJar());
            instance.setStatus(ServerInstance.ServerStatus.ERROR);
            return false;
        }

        // Check if port is available before starting
        if (isPortInUse(instance.getPort())) {
            EmbeddedMC.LOGGER.error("Port {} is already in use! Cannot start server.", instance.getPort());
            instance.setStatus(ServerInstance.ServerStatus.ERROR);
            return false;
        }

        instance.setStatus(ServerInstance.ServerStatus.STARTING);

        CompletableFuture.runAsync(() -> {
            try {
                // Accept EULA if configured
                if (EmbeddedMC.getInstance().getConfig().isAutoAcceptEula()) {
                    instance.acceptEula();
                }

                // Configure server for embedded mode (offline mode, correct port)
                instance.configureForEmbeddedMode();

                // Build command
                List<String> command = buildCommand();
                EmbeddedMC.LOGGER.info("Starting server with command: {}", String.join(" ", command));

                // Start process
                ProcessBuilder pb = new ProcessBuilder(command);
                pb.directory(instance.getInstancePath().toFile());
                pb.redirectErrorStream(true);

                process = pb.start();
                running = true;

                // Monitor output
                outputThread = new Thread(() -> monitorOutput(onReady), "EmbeddedServer-" + instance.getId());
                outputThread.start();

            } catch (IOException e) {
                EmbeddedMC.LOGGER.error("Failed to start server", e);
                instance.setStatus(ServerInstance.ServerStatus.ERROR);
                running = false;
            }
        });

        return true;
    }

    private List<String> buildCommand() {
        List<String> command = new ArrayList<>();

        // Java executable
        command.add(EmbeddedMC.getInstance().getConfig().getJavaPath());

        // Memory settings
        command.add("-Xms" + instance.getRamMB() + "M");
        command.add("-Xmx" + instance.getRamMB() + "M");

        // JVM arguments
        command.addAll(instance.getJvmArgs());

        // Disable GUI
        command.add("-Dcom.mojang.eula.agree=true");

        // Server JAR
        command.add("-jar");
        command.add("server.jar");

        // No GUI
        command.add("nogui");

        // Port
        command.add("--port");
        command.add(String.valueOf(instance.getPort()));

        return command;
    }

    private void monitorOutput(Runnable onReady) {
        boolean serverReady = false;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null && running) {
                EmbeddedMC.LOGGER.info("[{}] {}", instance.getName(), line);
                addConsoleLine(line);

                // Detect server ready - look for the specific "Done" message with timing info
                if (!serverReady && line.contains("Done (") && line.contains("s)!")) {
                    // Wait for port to actually be accepting connections
                    if (waitForPort(instance.getPort(), 30)) {
                        serverReady = true;
                        instance.setStatus(ServerInstance.ServerStatus.RUNNING);
                        EmbeddedMC.LOGGER.info("Server {} is ready and accepting connections!", instance.getName());
                        if (onReady != null) {
                            onReady.run();
                        }
                    } else {
                        EmbeddedMC.LOGGER.error("Server {} failed to bind to port {}", instance.getName(), instance.getPort());
                        instance.setStatus(ServerInstance.ServerStatus.ERROR);
                    }
                }
            }
        } catch (IOException e) {
            if (running) {
                EmbeddedMC.LOGGER.error("Error reading server output", e);
            }
        }

        running = false;
        instance.setStatus(ServerInstance.ServerStatus.STOPPED);
        EmbeddedMC.LOGGER.info("Server {} stopped", instance.getName());
    }

    private boolean waitForPort(int port, int timeoutSeconds) {
        long start = System.currentTimeMillis();
        long timeout = timeoutSeconds * 1000L;

        while (System.currentTimeMillis() - start < timeout) {
            try (Socket socket = new Socket("localhost", port)) {
                return true;
            } catch (IOException e) {
                // Port not ready yet, wait and retry
                try {
                    Thread.sleep(250);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
        return false;
    }

    public void stop() {
        if (!running || process == null) {
            return;
        }

        instance.setStatus(ServerInstance.ServerStatus.STOPPING);
        EmbeddedMC.LOGGER.info("Stopping server {}...", instance.getName());

        try {
            // Send stop command
            OutputStream os = process.getOutputStream();
            os.write("stop\n".getBytes());
            os.flush();

            // Wait for graceful shutdown
            boolean exited = process.waitFor(30, TimeUnit.SECONDS);

            if (!exited) {
                EmbeddedMC.LOGGER.warn("Server didn't stop gracefully, forcing...");
                process.destroyForcibly();
            }

        } catch (IOException | InterruptedException e) {
            EmbeddedMC.LOGGER.error("Error stopping server", e);
            process.destroyForcibly();
        }

        running = false;
        instance.setStatus(ServerInstance.ServerStatus.STOPPED);
    }

    public boolean isRunning() {
        return running && process != null && process.isAlive();
    }

    public boolean waitForReady(int timeoutSeconds) {
        long start = System.currentTimeMillis();
        long timeout = timeoutSeconds * 1000L;

        while (System.currentTimeMillis() - start < timeout) {
            if (instance.getStatus() == ServerInstance.ServerStatus.RUNNING) {
                return true;
            }
            if (instance.getStatus() == ServerInstance.ServerStatus.ERROR) {
                return false;
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    public boolean isPortReady() {
        try (Socket socket = new Socket("localhost", instance.getPort())) {
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Check if a port is already in use by trying to bind to it.
     */
    private boolean isPortInUse(int port) {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            // Port is available, we could bind to it
            return false;
        } catch (IOException e) {
            // Port is in use
            return true;
        }
    }
}
