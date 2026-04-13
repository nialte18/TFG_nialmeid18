package com.nicole.tfg;
import java.sql.Timestamp;
import java.util.UUID;

public class Transaccion {
    // Funciona como una clase de datos ( DTO)
    private final String id;          // identificador 
    private final String operacion;   // "lectura" o "escritura"
    private final String clave;       
    private final String valor;       // value (puede ser null en lecturas)
    private final Timestamp t_inicial;
    private  Timestamp t_final;
    // estados de las transacciones
    public enum Estado{
        INICIADA,
        FINALIZADA,
        ABORTADA
    }
    private Estado estado;

    public Transaccion(String operacion, String clave, String valor) {
        // id e instante se crean automaticamente 
        this.id        = UUID.randomUUID().toString();
        this.operacion = operacion;
        this.clave     = clave;
        this.valor     = valor;
        this.t_inicial=  new Timestamp(System.currentTimeMillis());
        this.t_final = null; // todavía no ha terminado
        this.estado= Estado.INICIADA; 
    }

    public String getId()        { return id; }
    public String getOperacion() { return operacion; }
    public String getClave()     { return clave; }
    public String getValor()     { return valor; }
    public Timestamp getTInicial() { return t_inicial; }
    public Timestamp getTFinal() { return t_final; }
    public Estado getEstado() { return estado; }

    public void setTFinal(Timestamp t_final) {
        this.t_final = t_final;
    }

    public void setEstado(Estado estado) {
        this.estado = estado;
    }}

   
