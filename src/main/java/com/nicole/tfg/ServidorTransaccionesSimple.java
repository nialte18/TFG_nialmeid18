package com.nicole.tfg;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;

public class ServidorTransaccionesSimple {

    public static void main(String[] args) {

        int puerto = 5000;

        GestorTransacciones gestor = new GestorTransacciones(
                new Mongo7(),
                new Mongo8(),
                new Redis()
        );

        try (ServerSocket serverSocket = new ServerSocket(puerto)) {
            System.out.println("Servidor escuchando en el puerto " + puerto);

            gestor.abrir(); // abrimos conexiones a las 3 BDD

            // Aceptamos un cliente 
            try (Socket socket = serverSocket.accept();
                 BufferedReader in = new BufferedReader(
                         new InputStreamReader(socket.getInputStream()));
                 PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

                System.out.println("Cliente conectado: " + socket.getInetAddress());

                // Esperamos write o read 
            
                String linea = in.readLine();
                if (linea == null) {
                    System.out.println("Cliente cerró la conexión sin enviar nada.");
                    return;
                }

                System.out.println("Comando recibido: " + linea);
                String[] partes = linea.split(" ", 3);
                String comando = partes[0].toUpperCase();

                if ("WRITE".equals(comando)) {
                    if (partes.length < 3) {
                        out.println("ERROR Formato: WRITE clave valor");
                        return;
                    }
                    String clave = partes[1];
                    String valor = partes[2];

                    Transaccion txW = new Transaccion("escritura", clave, valor);
                    gestor.ejecutarEscritura(txW);

                    out.println("OK WRITE clave=" + clave);

                } else if ("READ".equals(comando)) {
                    if (partes.length < 2) {
                        out.println("ERROR Formato: READ clave");
                        return;
                    }
                    String clave = partes[1];

                    Transaccion txR = new Transaccion("lectura", clave, null);
                    Map<String,String> res = gestor.ejecutarLectura(txR);

                    String v7 = res.get("mongo7");
                    String v8 = res.get("mongo8");
                    String vr = res.get("redis");

                    out.println("OK READ mongo7=" + v7 + " | mongo8=" + v8 + " | redis=" + vr);

                } else {
                    out.println("ERROR Comando no reconocido. Usa WRITE o READ.");
                }

            }

        } catch (Exception e) {
            System.err.println(" Error en el servidor:");
            e.printStackTrace();
        } finally {
            try {
                gestor.cerrar();
            } catch (Exception e) {
                System.err.println(" Error al cerrar el gestor:");
                e.printStackTrace();
            }
        }
    }
}
