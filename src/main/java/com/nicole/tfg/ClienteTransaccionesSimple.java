package com.nicole.tfg;

import java.io.*;
import java.net.Socket;

public class ClienteTransaccionesSimple {

    public static void main(String[] args) {

        String host = "localhost";
        int puerto = 5000;

        String comando = "WRITE test HolaDesdeCliente";

        try (Socket socket = new Socket(host, puerto);
             BufferedReader in = new BufferedReader(
                     new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

            System.out.println("Conectado a " + host + ":" + puerto);

            // Enviamos el comando
            System.out.println("Enviando comando: " + comando);
            out.println(comando);

            // Leemos UNA respuesta
            String respuesta = in.readLine();
            System.out.println("Respuesta del servidor: " + respuesta);

        } catch (Exception e) {
            System.err.println(" Error en el cliente:");
            e.printStackTrace();
        }
    }
}
