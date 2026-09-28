package com.nicole.tfg;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;

public class ServidorTransaccionesSimple {

    public static void main(String[] args) {

        int puerto = 5000;

        GestorTransacciones gestor = new GestorTransacciones(
                new Mongo(),
                new Mongo(),
                new Redis()
        );

        try (ServerSocket serverSocket = new ServerSocket(puerto)) {
            System.out.println("Servidor escuchando en el puerto " + puerto);

            gestor.abrir(
                    "mongodb://localhost:27017/tfg_mongo7",
                    "mongodb://localhost:27018/tfg_mongo8",
                    "redis://localhost:6379"
            ); // abrimos conexiones a las 3 BDD

            // Atendemos una conexion cada vez, de forma secuencial.
            while (true) {
                // accept() espera hasta que se conecte el siguiente cliente.
                Socket cliente = serverSocket.accept();
                try (Socket socket = cliente;
                     BufferedReader in = new BufferedReader(
                             new InputStreamReader(socket.getInputStream()));
                     PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

                    System.out.println("Cliente conectado: " + socket.getInetAddress());

                    // Esperamos write o read

                    String linea = in.readLine();
                    if (linea == null) {
                        System.out.println("Cliente cerró la conexión sin enviar nada.");
                        continue;
                    }

                    System.out.println("Comando recibido: " + linea);
                    String[] partes = linea.split(" ", 3);
                    String comando = partes[0].toUpperCase();

                    if ("WRITE".equals(comando)) {
                        if (partes.length < 3) {
                            out.println("ERROR Formato: WRITE clave valor");
                            continue;
                        }
                        String clave = partes[1];
                        String valor = partes[2];

                        Transaccion txW = new Transaccion("escritura", clave, valor);
                        gestor.ejecutarEscritura(txW,0,0);

                        out.println("OK WRITE clave=" + clave);

                    } else if ("READ".equals(comando)) {
                        if (partes.length < 2) {
                            out.println("ERROR Formato: READ clave");
                            continue;
                        }
                        String clave = partes[1];

                        Transaccion txR = new Transaccion("lectura", clave, null);
                        Map<String,String> res = gestor.ejecutarLectura(txR,0,0);

                        String v7 = res.get("mongo7");
                        String v8 = res.get("mongo8");
                        String vr = res.get("redis");

                        out.println("OK READ mongo7=" + v7 + " | mongo8=" + v8 + " | redis=" + vr);

                    } else {
                        out.println("ERROR Comando no reconocido. Usa WRITE o READ.");
                    }

                } catch (Exception e) {
                    // Un fallo de este cliente no termina el servidor.
                    System.err.println("Error al atender al cliente:");
                    e.printStackTrace();
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
