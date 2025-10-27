@"
# TFG - Sistemas Distribuidos
Proyecto desarrollado por Nicole Almeida Terán

## 📚 Descripción
Implementación de operaciones básicas de **lectura y escritura** sobre tres contenedores Docker:
- **MongoDB 7** → `localhost:27017`
- **MongoDB 8** → `localhost:27018`
- **Redis 7** → `localhost:6379`

Cada servicio se ejecuta dentro de un contenedor Docker independiente, y se accede a través de clases Java mediante Maven.


---

## ▶️ Ejecución de los contenedores
Si los contenedores ya existen:
```bash
docker start mongo7 mongo8 redis