package com.nicole.tfg;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import junit.framework.TestCase;

public class ExperimentosTest extends TestCase {
    private static class GestorSimulado extends GestorTransacciones {
        double proporcion;
        int bloque;
        int llamadas;
        String valor;
        boolean fallar;
        GestorSimulado() { super(null, null, null); }
        @Override public void ejecutarEscritura(Transaccion tx, double p, int b) throws Exception {
            registrar(p, b);
            valor = tx.getValor();
        }
        @Override public Map<String, String> ejecutarLectura(Transaccion tx, double p, int b) throws Exception {
            registrar(p, b);
            return Map.of("mongo7", "v", "mongo8", "v", "redis", "v");
        }
        void registrar(double p, int b) throws Exception {
            llamadas++;
            proporcion = p;
            bloque = b;
            if (fallar) throw new Exception("Fallo simulado");
        }
    }

    public void testBateriaCompletaYMetadatos() {
        GestorSimulado gestor = new GestorSimulado();
        Map<String, int[]> escenarios = new HashMap<>();
        ByteArrayOutputStream texto = new ByteArrayOutputStream();
        ClienteTransaccionesSimple.ejecutarExperimentos(comando -> {
            String[] partes = comando.split(" ", 5);
            int[] cuenta = escenarios.computeIfAbsent(partes[1] + " " + partes[2], k -> new int[2]);
            cuenta[partes[0].equals("READ") ? 0 : 1]++;
            String respuesta = ServidorTransaccionesSimple.procesarPeticion(comando, gestor);
            assertTrue(respuesta.startsWith("OK "));
            assertEquals(Double.parseDouble(partes[1]), gestor.proporcion, 0.0);
            assertEquals(Integer.parseInt(partes[2]), gestor.bloque);
            return respuesta;
        }, new PrintStream(texto));
        assertEquals(45, escenarios.size());
        assertEquals(5110, gestor.llamadas);
        for (Map.Entry<String, int[]> escenario : escenarios.entrySet()) {
            String[] datos = escenario.getKey().split(" ");
            int bloque = Integer.parseInt(datos[1]);
            int lecturas = (int) Math.round(Double.parseDouble(datos[0]) * bloque);
            assertEquals(lecturas, escenario.getValue()[0]);
            assertEquals(bloque - lecturas, escenario.getValue()[1]);
        }
        assertEquals(45, texto.toString().split("RESULTADOS:", -1).length - 1);
    }

    public void testValidacionYRecuperacionTrasError() {
        GestorSimulado gestor = new GestorSimulado();
        for (String peticion : new String[]{"READ cuenta1", "READ NaN 8 c", "WRITE 1.1 8 c v",
                "READ 0.5 0 c", "READ 0.5 8 c extra", "WRITE 0.5 8 c"}) {
            assertTrue(ServidorTransaccionesSimple.procesarPeticion(peticion, gestor).startsWith("ERROR "));
        }
        assertEquals(0, gestor.llamadas);
        gestor.fallar = true;
        assertTrue(ServidorTransaccionesSimple.procesarPeticion("READ 0.2 8 c", gestor).startsWith("ERROR "));
        gestor.fallar = false;
        assertTrue(ServidorTransaccionesSimple.procesarPeticion("WRITE 0.8 16 c valor con espacios", gestor).startsWith("OK WRITE "));
        assertEquals("valor con espacios", gestor.valor);
        assertEquals(0.8, gestor.proporcion, 0.0);
    }

    public void testErroresNoCuentanComoExitos() {
        ByteArrayOutputStream texto = new ByteArrayOutputStream();
        ClienteTransaccionesSimple.ejecutarExperimentos(c -> "ERROR fallo simulado", new PrintStream(texto));
        assertEquals(45, texto.toString().split("Correctas: 0", -1).length - 1);
        assertTrue(texto.toString().contains("Fallidas: 512"));
    }

    public void testIntercambioRealPorSocket() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (ServerSocket servidor = new ServerSocket(0)) {
            servidor.setSoTimeout(5000);
            GestorSimulado gestor = new GestorSimulado();
            Future<?> tarea = executor.submit(() -> {
                try {
                    for (int i = 0; i < 2; i++) {
                        try (Socket socket = servidor.accept();
                             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                             PrintWriter out = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8)) {
                            socket.setSoTimeout(5000);
                            out.println(ServidorTransaccionesSimple.procesarPeticion(in.readLine(), gestor));
                        }
                    }
                } catch (IOException e) { throw new UncheckedIOException(e); }
            });
            assertTrue(ClienteTransaccionesSimple.enviarPeticion("localhost", servidor.getLocalPort(), "WRITE 0.2 8 c v").startsWith("OK WRITE "));
            assertTrue(ClienteTransaccionesSimple.enviarPeticion("localhost", servidor.getLocalPort(), "READ 0.2 8 c").startsWith("OK READ "));
            tarea.get(10, TimeUnit.SECONDS);
            assertEquals(2, gestor.llamadas);
        } finally {
            executor.shutdownNow();
        }
    }
}
