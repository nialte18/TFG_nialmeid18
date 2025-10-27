package com.nicole.tfg;

import com.mongodb.client.*;
import org.bson.Document;

public class Mongo7 {

    public static void main(String[] args) {
        // URI del contenedor mongo7
        String uri = "mongodb://localhost:27017";

        try (MongoClient mongoClient = MongoClients.create(uri)) {

            MongoDatabase database = mongoClient.getDatabase("tfg_mongo7");
            MongoCollection<Document> collection = database.getCollection("mensajes");

            // Escribir
            String mensaje = "Hola MongoDB 7";
            Document doc = new Document("mensaje", mensaje);
            collection.insertOne(doc);
            System.out.println(" Mensaje guardado: " + mensaje);

            // Lectura
            Document resultado = collection.find().first();
            if (resultado != null) {
                System.out.println("Mensaje leído: " + resultado.getString("mensaje"));
            } else {
                System.out.println(" No se encontró ningún mensaje.");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
