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
                             new InputStreamReader(socket.getInputStream(), java.nio.charset.StandardCharsets.UTF_8));
                     PrintWriter out = new PrintWriter(socket.getOutputStream(), true, java.nio.charset.StandardCharsets.UTF_8)) {

                    System.out.println("Cliente conectado: " + socket.getInetAddress());

                    // Esperamos write o read

                    String linea = in.readLine();
                    if (linea == null) {
                        System.out.println("Cliente cerró la conexión sin enviar nada.");
                        continue;
                    }

                    out.println(procesarPeticion(linea, gestor));

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
    // Una peticion lleva los datos del escenario, no solo la operacion.
    static String procesarPeticion(String linea, GestorTransacciones gestor) {
        if (linea == null) return "ERROR Peticion vacia";
        String[] partes = linea.trim().split("\\s+", 5);
        if (partes.length < 4) return "ERROR Formato: READ|WRITE porcentajeLectura bloque clave [valor]";
        String comando = partes[0].toUpperCase(java.util.Locale.ROOT);
        if (!comando.equals("READ") && !comando.equals("WRITE")) return "ERROR Comando desconocido";
        if ((comando.equals("READ") && partes.length != 4)
                || (comando.equals("WRITE") && partes.length != 5)) return "ERROR Numero de argumentos incorrecto";
        double porcentajeLectura;
        int bloque;
        try {
            porcentajeLectura = Double.parseDouble(partes[1]);
            bloque = Integer.parseInt(partes[2]);
        } catch (NumberFormatException e) {
            return "ERROR Porcentaje o bloque no numerico";
        }
        if (!Double.isFinite(porcentajeLectura) || porcentajeLectura < 0 || porcentajeLectura > 1 || bloque <= 0) {
            return "ERROR Porcentaje fuera de [0,1] o bloque no positivo";
        }
        String clave = partes[3];
        try {
            if (comando.equals("WRITE")) {
                gestor.ejecutarEscritura(new Transaccion("escritura", clave, partes[4]), porcentajeLectura, bloque);
                return "OK WRITE clave=" + clave;
            }
            Map<String, String> res = gestor.ejecutarLectura(new Transaccion("lectura", clave, null), porcentajeLectura, bloque);
            // Una respuesta ocupa siempre una sola linea del protocolo.
            return ("OK READ mongo7=" + res.get("mongo7") + " | mongo8=" + res.get("mongo8")
                    + " | redis=" + res.get("redis")).replace('\r', ' ').replace('\n', ' ');
        } catch (Exception e) {
            System.err.println("Error al ejecutar " + comando + ": " + e.getMessage());
            return "ERROR No se pudo completar la operacion " + comando;
        }
    }
}
