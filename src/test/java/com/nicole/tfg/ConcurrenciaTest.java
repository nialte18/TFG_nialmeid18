package com.nicole.tfg;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import junit.framework.TestCase;

public class ConcurrenciaTest extends TestCase {
    private Path dir;
    private Path archivo;
    protected void setUp() throws Exception {
        dir = Files.createTempDirectory("concurrencia-test");
        archivo = dir.resolve("historico.csv");
    }
    protected void tearDown() throws Exception {
        try (var files = Files.list(dir)) {
            for (Path p : files.toList()) Files.delete(p);
        }
        Files.delete(dir);
    }

    private static class Base extends Mongo {
        final CountDownLatch dentro = new CountDownLatch(1);
        final CountDownLatch continuar = new CountDownLatch(1);
        final AtomicInteger operaciones = new AtomicInteger();
        final AtomicInteger historicos = new AtomicInteger();
        boolean fallo;
        @Override public void write(String k, String v) throws Exception {
            operaciones.incrementAndGet();
            dentro.countDown();
            if (!continuar.await(5, TimeUnit.SECONDS)) throw new IOException("La prueba no libero la operacion");
            if (fallo) throw new IOException("Fallo simulado");
        }
        @Override public String read(String k) throws Exception { write(k, null); return "v"; }
        @Override public void guardarHistorico(String id, long inicio, long fin, long duracion,
                double p, int bloque, Transaccion.Estado estado, Transaccion.MotivoAborto motivo) {
            historicos.incrementAndGet();
        }
    }

    public void testSoloUnaAdmitidaAunqueLleguenALaVez() throws Exception {
        ControlConcurrencia control = new ControlConcurrencia();
        CountDownLatch salida = new CountDownLatch(1);
        List<Future<Transaccion>> tareas = new ArrayList<>();
        try (ExecutorService hilos = Executors.newFixedThreadPool(8)) {
            for (int i = 0; i < 32; i++) {
                tareas.add(hilos.submit(() -> {
                    salida.await();
                    Transaccion tx = new Transaccion("lectura", "c", null);
                    control.intentarIniciar(tx);
                    return tx;
                }));
            }
            salida.countDown();
            Transaccion admitida = null;
            int abortadas = 0;
            for (Future<Transaccion> f : tareas) {
                Transaccion tx = f.get(5, TimeUnit.SECONDS);
                if (tx.getEstado() == Transaccion.Estado.INICIADA) {
                    assertNull(admitida);
                    admitida = tx;
                } else {
                    abortadas++;
                    assertEquals(Transaccion.MotivoAborto.COLISION_TEMPORAL, tx.getMotivoAborto());
                    assertNotNull(tx.getTFinal());
                }
            }
            assertNotNull(admitida);
            assertEquals(31, abortadas);
            control.finalizar(admitida);
            Transaccion siguiente = new Transaccion("lectura", "c", null);
            assertTrue(control.intentarIniciar(siguiente));
            control.finalizar(siguiente);
        }
    }

    public void testColisionPorSocketLecturasYEscriturasDistintasClaves() throws Exception {
        Base base = new Base();
        GestorTransacciones gestor = new GestorTransacciones(base, base, base, new HistoricoCsv(archivo));
        try (ServerSocket servidor = new ServerSocket(0);
             ExecutorService conexiones = Executors.newFixedThreadPool(4);
             ExecutorService clientes = Executors.newFixedThreadPool(2)) {
            servidor.setSoTimeout(5000);
            Future<?> aceptar = conexiones.submit(() -> {
                try {
                    for (int i = 0; i < 4; i++) {
                        Socket socket = servidor.accept();
                        conexiones.submit(() -> ServidorTransaccionesSimple.atenderCliente(socket, gestor));
                    }
                } catch (IOException e) { throw new UncheckedIOException(e); }
            });
            Future<String> primera = clientes.submit(() -> ClienteTransaccionesSimple.enviarPeticion(
                    "localhost", servidor.getLocalPort(), "READ 1 4 cuenta1"));
            try {
                assertTrue(base.dentro.await(5, TimeUnit.SECONDS));
                for (String comando : new String[]{"READ 1 4 cuenta2", "WRITE 0 4 cuenta3 v"}) {
                    assertEquals("ABORTADA COLISION_TEMPORAL", ClienteTransaccionesSimple.enviarPeticion(
                            "localhost", servidor.getLocalPort(), comando));
                }
                // Las dos rechazadas no han llegado a leer ni escribir datos.
                assertEquals(1, base.operaciones.get());
            } finally {
                base.continuar.countDown();
            }
            assertTrue(primera.get(5, TimeUnit.SECONDS).startsWith("OK READ "));
            assertTrue(ClienteTransaccionesSimple.enviarPeticion("localhost", servidor.getLocalPort(),
                    "WRITE 0 4 cuenta4 v").startsWith("OK WRITE "));
            aceptar.get(5, TimeUnit.SECONDS);
            assertEquals(6, base.operaciones.get());
            assertEquals(4, base.historicos.get());
        }
        List<String> filas = Files.readAllLines(archivo);
        assertEquals(5, filas.size());
        assertEquals(2, filas.stream().filter(f -> f.endsWith(",ABORTADA,COLISION_TEMPORAL")).count());
    }

    public void testErrorLiberaControl() throws Exception {
        Base base = new Base();
        base.continuar.countDown();
        base.fallo = true;
        GestorTransacciones gestor = new GestorTransacciones(base, base, base, new HistoricoCsv(archivo));
        Transaccion tx = new Transaccion("escritura", "c", "v");
        try { gestor.ejecutarEscritura(tx, 0, 2); fail(); }
        catch (IOException esperado) { }
        assertEquals(Transaccion.MotivoAborto.ERROR_OPERACION, tx.getMotivoAborto());
        base.fallo = false;
        Transaccion siguiente = new Transaccion("lectura", "c", null);
        gestor.ejecutarLectura(siguiente, 1, 2);
        assertEquals(Transaccion.Estado.FINALIZADA, siguiente.getEstado());
    }

    public void testCsvNoMezclaFilasConcurrentes() throws Exception {
        HistoricoCsv csv = new HistoricoCsv(archivo);
        try (ExecutorService hilos = Executors.newFixedThreadPool(8)) {
            List<Future<?>> tareas = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                String id = "tx" + i;
                tareas.add(hilos.submit(() -> {
                    csv.guardar(id, 0, 1, 1, 0.5, 100, Transaccion.Estado.ABORTADA,
                            Transaccion.MotivoAborto.COLISION_TEMPORAL);
                    return null;
                }));
            }
            for (Future<?> f : tareas) f.get(5, TimeUnit.SECONDS);
        }
        List<String> filas = Files.readAllLines(archivo);
        assertEquals(101, filas.size());
        Set<String> ids = new HashSet<>();
        for (String fila : filas.subList(1, filas.size())) {
            String[] columnas = fila.split(",");
            assertEquals(8, columnas.length);
            assertTrue(ids.add(columnas[0]));
        }
    }

    public void testClienteConcurrenteYRecuentosCsv() throws Exception {
        AtomicInteger llamadas = new AtomicInteger();
        Set<String> hilosUsados = ConcurrentHashMap.newKeySet();
        CountDownLatch dosEnCurso = new CountDownLatch(2);
        Path resumen;
        try (ResumenExperimentosCsv csv = new ResumenExperimentosCsv(dir)) {
            resumen = csv.getRuta();
            ClienteTransaccionesSimple.ejecutarExperimentos(c -> {
                hilosUsados.add(Thread.currentThread().getName());
                int n = llamadas.incrementAndGet();
                if (n <= 2) {
                    dosEnCurso.countDown();
                    try {
                        if (!dosEnCurso.await(5, TimeUnit.SECONDS)) throw new IOException("No hay concurrencia");
                    } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException(e); }
                }
                if (n % 3 == 0) return "ABORTADA COLISION_TEMPORAL";
                if (n % 3 == 1) throw new IOException("Conexion perdida");
                return "OK " + c.split(" ")[0] + " valor";
            }, new PrintStream(OutputStream.nullOutputStream()), csv, 4);
        }
        assertEquals(5110, llamadas.get());
        assertTrue(hilosUsados.size() > 1);
        List<String> filas = Files.readAllLines(resumen);
        assertEquals(46, filas.size());
        int correctas = 0, abortadas = 0, errores = 0;
        for (String fila : filas.subList(1, filas.size())) {
            String[] c = fila.split(",");
            int bien = Integer.parseInt(c[5]), colision = Integer.parseInt(c[13]), error = Integer.parseInt(c[14]);
            assertEquals(Integer.parseInt(c[2]), bien + colision + error);
            assertEquals(Integer.parseInt(c[6]), colision + error);
            assertEquals("4", c[12]);
            correctas += bien; abortadas += colision; errores += error;
        }
        assertEquals(1703, correctas);
        assertEquals(1703, abortadas);
        assertEquals(1704, errores);
    }
}
