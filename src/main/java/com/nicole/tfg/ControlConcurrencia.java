package com.nicole.tfg;

import java.sql.Timestamp;
import java.util.concurrent.locks.ReentrantLock;

/** Una sola transaccion puede ejecutar operaciones de datos en cada momento. */
public class ControlConcurrencia {
    private final ReentrantLock lock = new ReentrantLock();
    private Transaccion activa;

    public boolean intentarIniciar(Transaccion tx) {
        lock.lock();
        try {
            if (tx.getEstado() != Transaccion.Estado.INICIADA || activa == tx) {
                throw new IllegalStateException("La transaccion ya se ha utilizado");
            }
            // El inicio se fija aqui, no al abrir el socket ni al crear el objeto.
            tx.setTInicial(new Timestamp(System.currentTimeMillis()));
            if (activa != null) {
                tx.setEstado(Transaccion.Estado.ABORTADA);
                tx.setMotivoAborto(Transaccion.MotivoAborto.COLISION_TEMPORAL);
                tx.setTFinal(new Timestamp(System.currentTimeMillis()));
                return false;
            }
            activa = tx;
            return true;
        } finally {
            lock.unlock();
        }
    }

    public void finalizar(Transaccion tx) {
        lock.lock();
        try {
            if (activa != tx) {
                throw new IllegalStateException("Esta transaccion no ocupa el control");
            }
            tx.setTFinal(new Timestamp(System.currentTimeMillis()));
            activa = null;
        } finally {
            lock.unlock();
        }
    }
}
