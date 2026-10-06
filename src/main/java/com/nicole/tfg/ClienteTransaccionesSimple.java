package com.nicole.tfg;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;

public class ClienteTransaccionesSimple {
    private static final double[] WORKLOADS = {0.0, 0.2, 0.5, 0.8, 1.0};
    private static final int[] BLOQUES = {2, 4, 8, 16, 32, 64, 128, 256, 512};
    private static final String[] CLAVES = {"cuenta1", "cuenta2", "cuenta3", "cuenta4"};

    public static void main(String[] args) throws IOException {
        try (ResumenExperimentosCsv resumen = new ResumenExperimentosCsv(Path.of("resultados"))) {
            System.out.println("CSV de esta ejecucion: " + resumen.getRuta().toAbsolutePath());
            ejecutarExperimentos(comando -> enviarPeticion("localhost", 5000, comando), System.out, resumen);
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
            ejecutarExperimentos(envio, salida, null);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static void ejecutarExperimentos(Envio envio, PrintStream salida, ResumenExperimentosCsv resumen) throws IOException {
        Random random = new Random();
        for (double porcentajeLectura : WORKLOADS) {
            for (int bloque : BLOQUES) {
                List<String> operaciones = prepararOperaciones(porcentajeLectura, bloque, random);
                int lecturas = Collections.frequency(operaciones, "READ");
                int correctas = 0;
                int fallidas = 0;
                long tiempoPeticiones = 0;
                long inicioBloque = System.nanoTime();
                for (int i = 0; i < operaciones.size(); i++) {
                    String tipo = operaciones.get(i);
                    String clave = CLAVES[random.nextInt(CLAVES.length)];
                    // Formato: operacion proporcionLecturas bloque clave [valor].
                    String comando = tipo + " " + porcentajeLectura + " " + bloque + " " + clave;
                    if (tipo.equals("WRITE")) comando += " valor_" + i;
                    long inicio = System.nanoTime();
                    try {
                        String respuesta = envio.enviar(comando);
                        if (respuesta != null && respuesta.startsWith("OK " + tipo + " ")) {
                            correctas++;
                        } else {
                            fallidas++;
                            salida.println("Peticion fallida: " + respuesta);
                        }
                    } catch (IOException e) {
                        fallidas++;
                        salida.println("Fallo de comunicacion: " + e.getMessage());
                    }
                    tiempoPeticiones += System.nanoTime() - inicio;
                }
                long duracionBloque = System.nanoTime() - inicioBloque;
                // Guardar fuera del intervalo medido para no incluir la escritura de este CSV.
                if (resumen != null) {
                    resumen.guardar(porcentajeLectura, bloque, lecturas, correctas, fallidas,
                            duracionBloque, tiempoPeticiones);
                }
                salida.printf(Locale.ROOT,
                        "%nRESULTADOS: lecturas %.0f%% / escrituras %.0f%% | bloque %d%n",
                        porcentajeLectura * 100, (1 - porcentajeLectura) * 100, bloque);
                salida.println("Lecturas enviadas: " + lecturas + " | Escrituras enviadas: " + (bloque - lecturas));
                salida.println("Correctas: " + correctas + " | Fallidas: " + fallidas);
                salida.printf(Locale.ROOT, "Tiempo total del bloque: %.3f ms%n", duracionBloque / 1_000_000.0);
                salida.printf(Locale.ROOT, "Latencia media por intento (conexion y respuesta incluidas): %.3f ms%n",
                        tiempoPeticiones / 1_000_000.0 / bloque);
                salida.printf(Locale.ROOT, "Throughput: %.3f peticiones correctas/segundo%n",
                        duracionBloque > 0 ? correctas * 1_000_000_000.0 / duracionBloque : 0);
            }
        }
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
