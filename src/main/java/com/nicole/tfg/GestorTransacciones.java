package com.nicole.tfg;

import java.util.HashMap;
import java.util.Map;

// capa intermedia que coordina 
public class GestorTransacciones {

    private final Mongo7 mongo7;  // necesitamos el metodo de guardarHistórico
    private final DatabaseClient mongo8;
    private final DatabaseClient redis;
   

    

    public GestorTransacciones(Mongo7 mongo7,
                               DatabaseClient mongo8,
                               DatabaseClient redis) {
        this.mongo7 = mongo7;
        this.mongo8 = mongo8;
        this.redis  = redis;
    }

    public void abrir() throws Exception {
        mongo7.open();
        mongo8.open();
        redis.open();
    }

    public void cerrar() throws Exception {
        mongo7.close();
        mongo8.close();
        redis.close();
    }

    // Ejecuta una escritura en las 3 BDs
    // Ejecuta una escritura en las 3 BDs
public void ejecutarEscritura(Transaccion tx,
                              double porcentajeEscritura,
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
    mongo7.guardarHistorico(
            tx.getId(),
            inicio,
            fin,
            duracion,
            porcentajeEscritura,
            totalTransacciones      
    );
}

    // Ejecuta una lectura en las 3 BDs
    public Map<String,String> ejecutarLectura(Transaccion tx,double porcentajeEscritura,int totalTransacciones) throws Exception {
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
        
            mongo7.guardarHistorico(
            tx.getId(),
            inicio,
            fin,
            duracion,
            porcentajeEscritura,
            totalTransacciones      
    );


        return resultado;
    }
}
