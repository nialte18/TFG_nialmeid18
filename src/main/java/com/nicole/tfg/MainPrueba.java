package com.nicole.tfg;

public class MainPrueba {
    public static void main(String[] args) throws Exception {

        DatabaseClient mongo7 = new Mongo7();
        DatabaseClient mongo8 = new Mongo8();
        DatabaseClient redis  = new Redis();

        mongo7.open();
        mongo8.open();
        redis.open();

        String key   = "test";
        String valor = "Hola TFG";

        System.out.println("=== ESCRIBIENDO EN LAS 3 BDD ===");
        mongo7.write(key, valor);
        mongo8.write(key, valor);
        redis.write(key, valor);

        System.out.println("=== LEYENDO DE LAS 3 BDD ===");
        String v7 = mongo7.read(key);
        String v8 = mongo8.read(key);
        String vr = redis.read(key);

        System.out.println("Mongo7: " + v7);
        System.out.println("Mongo8: " + v8);
        System.out.println("Redis : " + vr);

        if (valor.equals(v7) && valor.equals(v8) && valor.equals(vr)) {
            System.out.println("✅ TODO COHERENTE: las 3 devuelven el mismo valor.");
        } else {
            System.out.println("⚠️ INCONSISTENTE: alguna BDD devuelve algo distinto.");
        }

        mongo7.close();
        mongo8.close();
        redis.close();
    }
    
}
