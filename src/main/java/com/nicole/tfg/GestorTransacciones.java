package com.nicole.tfg;

import java.util.HashMap;
import java.util.Map;
import java.nio.file.Path;

// capa intermedia que coordina 
public class GestorTransacciones {

    private final Mongo mongo7;  // necesitamos el metodo de guardarHistórico
    private final DatabaseClient mongo8;
    private final DatabaseClient redis;
    private final HistoricoCsv historicoCsv;
   

    

    public GestorTransacciones(Mongo mongo7,
                               DatabaseClient mongo8,
                               DatabaseClient redis) {
        this(mongo7, mongo8, redis,
                new HistoricoCsv(Path.of("resultados", "historico_transacciones.csv")));
    }

    GestorTransacciones(Mongo mongo7, DatabaseClient mongo8, DatabaseClient redis,
                        HistoricoCsv historicoCsv) {
        this.mongo7 = mongo7;
        this.mongo8 = mongo8;
        this.redis  = redis;
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

    // Ejecuta una escritura en las 3 BDs
    // Ejecuta una escritura en las 3 BDs
public void ejecutarEscritura(Transaccion tx,
                              double porcentajeLectura,
                              int totalTransacciones) throws Exception {

    if (!tx.getOperacion().equalsIgnoreCase("escritura")) {
        throw new IllegalArgumentException("La transacción no es de escritura");
    }

    // Tiempo inicial real de ejecución
    long inicio = System.currentTimeMillis();

    mongo7.write(tx.getClave(), tx.getValor());
    mongo8.write(tx.getClave(), tx.getValor());
    redis.write(tx.getClave(), tx.getValor());

    // Tiempo final real
    long fin = System.currentTimeMillis();
    // Calcular duración
    long duracion = fin - inicio;
    // Guardar información en el histórico
    guardarHistorico(
            tx.getId(),
            inicio,
            fin,
            duracion,
            porcentajeLectura,
            totalTransacciones      
    );
}

    // Ejecuta una lectura en las 3 BDs
    public Map<String,String> ejecutarLectura(Transaccion tx,double porcentajeLectura,int totalTransacciones) throws Exception {
        if (!tx.getOperacion().equalsIgnoreCase("lectura")) {
            throw new IllegalArgumentException("La transacción no es de lectura");
        }

        Map<String,String> resultado = new HashMap<>();
        long inicio = System.currentTimeMillis();
        resultado.put("mongo7", mongo7.read(tx.getClave()));
        resultado.put("mongo8", mongo8.read(tx.getClave()));
        resultado.put("redis",  redis.read(tx.getClave()));
         // Tiempo final real
        long fin = System.currentTimeMillis();
        // Calcular duración
        long duracion = fin - inicio;
        // Guardar información en el histórico
        
            guardarHistorico(
            tx.getId(),
            inicio,
            fin,
            duracion,
            porcentajeLectura,
            totalTransacciones      
    );


        return resultado;
    }
    // Intentamos ambos destinos; un error no debe aparentar un guardado completo.
    private void guardarHistorico(String id, long inicio, long fin, long duracion,
                                  double porcentajeLectura, int bloque) throws Exception {
        Exception fallo = null;
        try {
            mongo7.guardarHistorico(id, inicio, fin, duracion, porcentajeLectura, bloque);
        } catch (Exception e) {
            fallo = e;
        }
        try {
            historicoCsv.guardar(id, inicio, fin, duracion, porcentajeLectura, bloque);
        } catch (Exception e) {
            if (fallo == null) fallo = e;
            else fallo.addSuppressed(e);
        }
        if (fallo != null) throw fallo;
    }
}
