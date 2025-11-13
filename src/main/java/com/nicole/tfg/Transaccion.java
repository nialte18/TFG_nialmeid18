package com.nicole.tfg;

import java.sql.Timestamp;
import java.util.UUID;

public class Transaccion {
    // Funciona como una clase de datos ( DTO)

    private final String id;          // identificador 
    private final String operacion;   // "lectura" o "escritura"
    private final String clave;       
    private final String valor;       // value (puede ser null en lecturas)
    private final Timestamp instante; // momento en que se creó la transacción

    public Transaccion(String operacion, String clave, String valor) {
        // id e instante se crean automaticamente 
        this.id        = UUID.randomUUID().toString();
        this.operacion = operacion;
        this.clave     = clave;
        this.valor     = valor;
        this.instante  = new Timestamp(System.currentTimeMillis());
    }

    public String getId()        { return id; }
    public String getOperacion() { return operacion; }
    public String getClave()     { return clave; }
    public String getValor()     { return valor; }
    public Timestamp getInstante() { return instante; }
}