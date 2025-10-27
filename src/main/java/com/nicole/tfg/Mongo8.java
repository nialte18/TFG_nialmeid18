package com.nicole.tfg;

import com.mongodb.client.*;
import org.bson.Document;
import org.bson.types.ObjectId;
import com.mongodb.client.result.InsertOneResult;

public class Mongo8 {
    public static void main(String[] args) {
        // Usa el puerto publicado de tu contenedor mongo8
        String uri = "mongodb://localhost:27018";

        try (MongoClient mongoClient = MongoClients.create(uri)) {
            MongoDatabase database = mongoClient.getDatabase("tfg_mongo8");
            MongoCollection<Document> collection = database.getCollection("mensajes");

            String mensaje = "Hola MongoDB 8";
            Document doc = new Document("mensaje", mensaje);
            InsertOneResult result = collection.insertOne(doc);
            ObjectId id = result.getInsertedId().asObjectId().getValue();
            System.out.println("Mensaje guardado en Mongo8 con _id: " + id + " -> " + mensaje);

            Document out = collection.find(new Document("_id", id)).first();
            if (out != null) {
                System.out.println("Mensaje leído de Mongo8: " + out.getString("mensaje"));
            } else {
                System.out.println("No se encontró ningún mensaje en Mongo8.");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

