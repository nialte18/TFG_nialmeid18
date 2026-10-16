package com.nicole.tfg;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ServidorTransaccionesSimple {
    public static void main(String[] args) {
        GestorTransacciones gestor = new GestorTransacciones(new Mongo(), new Mongo(), new Redis());
        Thread principal = Thread.currentThread();
        Thread cierre = null;
        try (ServerSocket servidor = new ServerSocket(5000);
             ExecutorService hilos = Executors.newFixedThreadPool(16)) {
            gestor.abrir("mongodb://localhost:27017/tfg_mongo7",
                    "mongodb://localhost:27018/tfg_mongo8", "redis://localhost:6379");
            cierre = new Thread(() -> {
                try {
                    servidor.close();
                    principal.join(150000);
                } catch (IOException e) {
                    System.err.println("Error al cerrar el servidor: " + e.getMessage());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            Runtime.getRuntime().addShutdownHook(cierre);
            System.out.println("Servidor concurrente escuchando en el puerto 5000 (16 hilos)");
            while (!servidor.isClosed()) {
                try {
                    Socket cliente = servidor.accept();
                    hilos.submit(() -> atenderCliente(cliente, gestor));
                } catch (SocketException e) {
                    if (!servidor.isClosed()) throw e;
                }
            }
        } catch (Exception e) {
            System.err.println("Error en el servidor: " + e.getMessage());
        } finally {
            try {
                gestor.cerrar();
            } catch (Exception e) {
                System.err.println("Error al cerrar las bases: " + e.getMessage());
            }
            if (cierre != null) {
                try { Runtime.getRuntime().removeShutdownHook(cierre); }
                catch (IllegalStateException e) { /* La JVM ya esta cerrando. */ }
            }
        }
    }

    static void atenderCliente(Socket cliente, GestorTransacciones gestor) {
        try (Socket socket = cliente) {
            socket.setSoTimeout(5000);
            try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                 PrintWriter out = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8)) {
                String linea = in.readLine();
                if (linea != null) out.println(procesarPeticion(linea, gestor));
            }
        } catch (IOException e) {
            System.err.println("Error de conexion: " + e.getMessage());
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
        Transaccion tx = new Transaccion(comando.equals("WRITE") ? "escritura" : "lectura",
                clave, comando.equals("WRITE") ? partes[4] : null);
        try {
            if (comando.equals("WRITE")) {
                gestor.ejecutarEscritura(tx, porcentajeLectura, bloque);
                if (tx.getEstado() == Transaccion.Estado.ABORTADA) return "ABORTADA " + tx.getMotivoAborto();
                return "OK WRITE clave=" + clave;
            }
            Map<String, String> res = gestor.ejecutarLectura(tx, porcentajeLectura, bloque);
            if (tx.getEstado() == Transaccion.Estado.ABORTADA) return "ABORTADA " + tx.getMotivoAborto();
            // Una respuesta ocupa siempre una sola linea del protocolo.
            return ("OK READ mongo7=" + res.get("mongo7") + " | mongo8=" + res.get("mongo8")
                    + " | redis=" + res.get("redis")).replace('\r', ' ').replace('\n', ' ');
        } catch (Exception e) {
            System.err.println("Error al ejecutar " + comando + ": " + e.getMessage());
            return "ERROR No se pudo completar la operacion " + comando;
        }
    }
}
