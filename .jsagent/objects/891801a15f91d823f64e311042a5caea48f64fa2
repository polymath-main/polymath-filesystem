package com.polymath.fs.network;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;

public class FtpClient {

    public static String execute(String host, int port, String user, String pass, String command, String path) {
        StringBuilder result = new StringBuilder();
        try (Socket socket = new Socket(host, port);
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()))) {

            // Read initial connection response
            readResponse(reader);

            // Login sequence
            sendCommand(writer, "USER " + user);
            readResponse(reader);

            sendCommand(writer, "PASS " + pass);
            readResponse(reader);

            // Command execution
            if ("LIST".equalsIgnoreCase(command) || "RETR".equalsIgnoreCase(command) || "STOR".equalsIgnoreCase(command)) {
                // Enter passive mode for data transfer
                sendCommand(writer, "PASV");
                String pasvResponse = readResponse(reader);

                // Parse PASV response: 227 Entering Passive Mode (h1,h2,h3,h4,p1,p2).
                int start = pasvResponse.indexOf('(');
                int end = pasvResponse.indexOf(')');
                if (start != -1 && end != -1) {
                    String[] parts = pasvResponse.substring(start + 1, end).split(",");
                    String dataHost = parts[0] + "." + parts[1] + "." + parts[2] + "." + parts[3];
                    int dataPort = (Integer.parseInt(parts[4]) << 8) + Integer.parseInt(parts[5]);

                    // Connect to data port
                    try (Socket dataSocket = new Socket(dataHost, dataPort)) {
                        if ("LIST".equalsIgnoreCase(command)) {
                            sendCommand(writer, "LIST" + (path != null && !path.isEmpty() ? " " + path : ""));
                            readResponse(reader);
                            result.append(readData(dataSocket));
                            readResponse(reader);
                        } else if ("RETR".equalsIgnoreCase(command)) {
                            sendCommand(writer, "RETR " + path);
                            readResponse(reader);
                            result.append(readData(dataSocket));
                            readResponse(reader);
                        } else if ("STOR".equalsIgnoreCase(command)) {
                            sendCommand(writer, "STOR " + path);
                            readResponse(reader);
                            // Stub for file uploading, as content is not provided
                            result.append("STOR completed (stub)");
                            readResponse(reader);
                        }
                    }
                } else {
                    result.append("Error parsing PASV response: ").append(pasvResponse);
                }
            } else {
                // Execute basic simple command
                sendCommand(writer, command + (path != null && !path.isEmpty() ? " " + path : ""));
                result.append(readResponse(reader));
            }

            // Quit cleanly
            sendCommand(writer, "QUIT");
            readResponse(reader);

        } catch (Exception e) {
            result.append("Error: ").append(e.getMessage());
        }

        return result.toString();
    }

    private static void sendCommand(BufferedWriter writer, String command) throws Exception {
        writer.write(command + "\r\n");
        writer.flush();
    }

    private static String readResponse(BufferedReader reader) throws Exception {
        StringBuilder response = new StringBuilder();
        String line = reader.readLine();
        if (line != null) {
            response.append(line).append("\n");
            // Handle multiline responses
            if (line.length() >= 4 && line.charAt(3) == '-') {
                String endMarker = line.substring(0, 3) + " ";
                while ((line = reader.readLine()) != null) {
                    response.append(line).append("\n");
                    if (line.startsWith(endMarker)) {
                        break;
                    }
                }
            }
        }
        return response.toString();
    }

    private static String readData(Socket dataSocket) throws Exception {
        StringBuilder data = new StringBuilder();
        try (BufferedReader dataReader = new BufferedReader(new InputStreamReader(dataSocket.getInputStream()))) {
            String line;
            while ((line = dataReader.readLine()) != null) {
                data.append(line).append("\n");
            }
        }
        return data.toString();
    }
}
