package com.nicole.tfg;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class GestorTransacciones {
    private final Mongo mongo7;
    private final DatabaseClient mongo8;
    private final DatabaseClient redis;
    private final HistoricoCsv historicoCsv;
    private final ControlConcurrencia control = new ControlConcurrencia();

    public GestorTransacciones(Mongo mongo7, DatabaseClient mongo8, DatabaseClient redis) {
        this(mongo7, mongo8, redis,
                new HistoricoCsv(Path.of("resultados", "historico_transacciones_concurrentes.csv")));
    }

    GestorTransacciones(Mongo mongo7, DatabaseClient mongo8, DatabaseClient redis, HistoricoCsv historicoCsv) {
        this.mongo7 = mongo7;
        this.mongo8 = mongo8;
        this.redis = redis;
        this.historicoCsv = historicoCsv;
    }

    public void abrir(String uriMongo7, String uriMongo8, String uriRedis) throws Exception {
        mongo7.open(uriMongo7);
        mongo8.open(uriMongo8);
        redis.open(uriRedis);
    }

    public void cerrar() throws Exception {
        mongo7.close();
        mongo8.close();
        redis.close();
    }

    public void ejecutarEscritura(Transaccion tx, double porcentajeLectura, int bloque) throws Exception {
        if (!control.intentarIniciar(tx)) {
            registrarResultado(tx, porcentajeLectura, bloque, null);
            return;
        }
        Exception fallo = null;
        try {
            if (!"escritura".equalsIgnoreCase(tx.getOperacion())) {
                throw new IllegalArgumentException("La transaccion no es de escritura");
            }
            mongo7.write(tx.getClave(), tx.getValor());
            mongo8.write(tx.getClave(), tx.getValor());
            redis.write(tx.getClave(), tx.getValor());
            tx.setEstado(Transaccion.Estado.FINALIZADA);
        } catch (Exception e) {
            tx.setEstado(Transaccion.Estado.ABORTADA);
            tx.setMotivoAborto(Transaccion.MotivoAborto.ERROR_OPERACION);
            fallo = e;
        } finally {
            control.finalizar(tx);
        }
        registrarResultado(tx, porcentajeLectura, bloque, fallo);
    }

    public Map<String, String> ejecutarLectura(Transaccion tx, double porcentajeLectura, int bloque) throws Exception {
        if (!control.intentarIniciar(tx)) {
            registrarResultado(tx, porcentajeLectura, bloque, null);
            return Map.of();
        }
        Exception fallo = null;
        Map<String, String> resultado = new HashMap<>();
        try {
            if (!"lectura".equalsIgnoreCase(tx.getOperacion())) {
                throw new IllegalArgumentException("La transaccion no es de lectura");
            }
            resultado.put("mongo7", mongo7.read(tx.getClave()));
            resultado.put("mongo8", mongo8.read(tx.getClave()));
            resultado.put("redis", redis.read(tx.getClave()));
            tx.setEstado(Transaccion.Estado.FINALIZADA);
        } catch (Exception e) {
            tx.setEstado(Transaccion.Estado.ABORTADA);
            tx.setMotivoAborto(Transaccion.MotivoAborto.ERROR_OPERACION);
            fallo = e;
        } finally {
            control.finalizar(tx);
        }
        registrarResultado(tx, porcentajeLectura, bloque, fallo);
        return resultado;
    }

    private void registrarResultado(Transaccion tx, double porcentajeLectura,
                                    int bloque, Exception falloOperacion) throws Exception {
        long inicio = tx.getTInicial().getTime();
        long fin = tx.getTFinal().getTime();
        // El estado describe las operaciones; un fallo del historico no las deshace.
        try {
            guardarHistorico(tx, inicio, fin, porcentajeLectura, bloque);
        } catch (Exception falloHistorico) {
            if (falloOperacion == null) throw falloHistorico;
            falloOperacion.addSuppressed(falloHistorico);
        }
        if (falloOperacion != null) throw falloOperacion;
    }

    private void guardarHistorico(Transaccion tx, long inicio, long fin,
                                  double porcentajeLectura, int bloque) throws Exception {
        Exception fallo = null;
        try {
            mongo7.guardarHistorico(tx.getId(), inicio, fin, fin - inicio, porcentajeLectura, bloque, tx.getEstado(), tx.getMotivoAborto());
        } catch (Exception e) {
            fallo = e;
        }
        try {
            historicoCsv.guardar(tx.getId(), inicio, fin, fin - inicio, porcentajeLectura, bloque, tx.getEstado(), tx.getMotivoAborto());
        } catch (Exception e) {
            if (fallo == null) fallo = e;
            else fallo.addSuppressed(e);
        }
        if (fallo != null) throw fallo;
    }
}
