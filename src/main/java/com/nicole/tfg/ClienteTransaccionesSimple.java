package com.nicole.tfg;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;

public class ClienteTransaccionesSimple {
    private static final double[] WORKLOADS = {0.0, 0.2, 0.5, 0.8, 1.0};
    private static final int[] BLOQUES = {2, 4, 8, 16, 32, 64, 128, 256, 512};
    private static final String[] CLAVES = {"cuenta1", "cuenta2", "cuenta3", "cuenta4"};

    public static void main(String[] args) throws IOException {
        int concurrencia = args.length == 0 ? 4 : Integer.parseInt(args[0]);
        if (concurrencia < 1 || concurrencia > 64) throw new IllegalArgumentException("Concurrencia entre 1 y 64");
        try (ResumenExperimentosCsv resumen = new ResumenExperimentosCsv(Path.of("resultados"))) {
            System.out.println("CSV de esta ejecucion: " + resumen.getRuta().toAbsolutePath());
            ejecutarExperimentos(comando -> enviarPeticion("localhost", 5000, comando), System.out, resumen, concurrencia);
        }
    }

    // Permite comprobar los experimentos sin utilizar bases de datos reales.
    interface Envio {
        String enviar(String comando) throws IOException;
    }

    static List<String> prepararOperaciones(double porcentajeLectura, int bloque, Random random) {
        int lecturas = (int) Math.round(porcentajeLectura * bloque);
        List<String> operaciones = new ArrayList<>();
        for (int i = 0; i < bloque; i++) {
            operaciones.add(i < lecturas ? "READ" : "WRITE");
        }
        Collections.shuffle(operaciones, random);
        return operaciones;
    }

    static void ejecutarExperimentos(Envio envio, PrintStream salida) {
        try {
            ejecutarExperimentos(envio, salida, null, 1);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static void ejecutarExperimentos(Envio envio, PrintStream salida, ResumenExperimentosCsv resumen) throws IOException {
        ejecutarExperimentos(envio, salida, resumen, 1);
    }

    static void ejecutarExperimentos(Envio envio, PrintStream salida, ResumenExperimentosCsv resumen,
                                     int concurrencia) throws IOException {
        if (concurrencia < 1 || concurrencia > 64) {
            throw new IllegalArgumentException("La concurrencia debe estar entre 1 y 64");
        }
        Random random = new Random();
        try (ExecutorService hilos = Executors.newFixedThreadPool(concurrencia)) {
            for (double porcentajeLectura : WORKLOADS) {
                for (int bloque : BLOQUES) {
                    List<String> operaciones = prepararOperaciones(porcentajeLectura, bloque, random);
                    List<String> comandos = new ArrayList<>();
                    for (int i = 0; i < bloque; i++) {
                        String tipo = operaciones.get(i);
                        String clave = CLAVES[random.nextInt(CLAVES.length)];
                        String comando = tipo + " " + porcentajeLectura + " " + bloque + " " + clave;
                        if (tipo.equals("WRITE")) comando += " valor_" + i;
                        comandos.add(comando);
                    }
                    int lecturas = Collections.frequency(operaciones, "READ");
                    int correctas = 0, abortadas = 0, errores = 0;
                    long tiempoPeticiones = 0;
                    List<Future<ResultadoPeticion>> tareas = new ArrayList<>();
                    CountDownLatch salidaBloque = new CountDownLatch(1);
                    long inicioBloque = System.nanoTime();
                    for (String comando : comandos) {
                        tareas.add(hilos.submit(() -> {
                            salidaBloque.await();
                            return ejecutarPeticion(envio, comando);
                        }));
                    }
                    salidaBloque.countDown();
                    for (Future<ResultadoPeticion> tarea : tareas) {
                        try {
                            ResultadoPeticion resultado = tarea.get();
                            tiempoPeticiones += resultado.duracionNs;
                            if (resultado.correcta) correctas++;
                            else if (resultado.colision) abortadas++;
                            else {
                                errores++;
                                salida.println("Peticion fallida: " + resultado.respuesta);
                            }
                        } catch (InterruptedException e) {
                            hilos.shutdownNow();
                            Thread.currentThread().interrupt();
                            throw new IOException("Experimento interrumpido; el CSV puede estar incompleto", e);
                        } catch (ExecutionException e) {
                            hilos.shutdownNow();
                            throw new IOException("Fallo inesperado en una tarea del cliente", e.getCause());
                        }
                    }
                    long duracionBloque = System.nanoTime() - inicioBloque;
                    int fallidas = abortadas + errores;
                    if (resumen != null) {
                        resumen.guardar(porcentajeLectura, bloque, lecturas, correctas, fallidas,
                                duracionBloque, tiempoPeticiones, concurrencia, abortadas, errores);
                    }
                    salida.printf(Locale.ROOT,
                            "%nRESULTADOS: lecturas %.0f%% / escrituras %.0f%% | bloque %d | concurrencia %d%n",
                            porcentajeLectura * 100, (1 - porcentajeLectura) * 100, bloque, concurrencia);
                    salida.println("Lecturas enviadas: " + lecturas + " | Escrituras enviadas: " + (bloque - lecturas));
                    salida.println("Correctas: " + correctas + " | Fallidas: " + fallidas);
                    salida.println("Abortadas por colision: " + abortadas + " | Otros errores: " + errores);
                    salida.printf(Locale.ROOT, "Tiempo total del bloque: %.3f ms%n", duracionBloque / 1e6);
                    salida.printf(Locale.ROOT, "Latencia media por intento: %.3f ms%n", tiempoPeticiones / 1e6 / bloque);
                    salida.printf(Locale.ROOT, "Throughput: %.3f peticiones correctas/segundo%n",
                            duracionBloque > 0 ? correctas * 1e9 / duracionBloque : 0);
                }
            }
        }
    }

    // Cada hilo devuelve su resultado; los contadores se actualizan en el hilo principal.
    private static class ResultadoPeticion {
        long duracionNs;
        String respuesta;
        boolean correcta;
        boolean colision;
    }

    private static ResultadoPeticion ejecutarPeticion(Envio envio, String comando) {
        ResultadoPeticion resultado = new ResultadoPeticion();
        String tipo = comando.startsWith("READ ") ? "READ" : "WRITE";
        long inicio = System.nanoTime();
        try {
            resultado.respuesta = envio.enviar(comando);
            resultado.correcta = resultado.respuesta != null && resultado.respuesta.startsWith("OK " + tipo + " ");
            resultado.colision = "ABORTADA COLISION_TEMPORAL".equals(resultado.respuesta);
        } catch (IOException e) {
            resultado.respuesta = "Error de comunicacion: " + e.getMessage();
        }
        resultado.duracionNs = System.nanoTime() - inicio;
        return resultado;
    }

    static String enviarPeticion(String host, int puerto, String comando) throws IOException {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, puerto), 5000);
            socket.setSoTimeout(120000);
            try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                 BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))) {
                out.write(comando);
                out.newLine();
                out.flush();
                String respuesta = in.readLine();
                if (respuesta == null) throw new EOFException("El servidor cerro la conexion sin responder");
                return respuesta;
            }
        }
    }
}
