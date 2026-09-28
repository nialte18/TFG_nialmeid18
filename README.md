# TFG - Sistemas Distribuidos

Proyecto desarrollado por Nicole Almeida Terán.

Implementación en Java de lecturas y escrituras sobre MongoDB y Redis. El proyecto está en fase **secuencial**; la concurrencia se abordará más adelante. El trabajo de referencia se denomina «TFG de Conrad» en el seguimiento del proyecto.

## Requisitos y conexiones

- JDK 21 y Maven.
- Para ejecutar los programas, MongoDB y Redis deben estar activos, por ejemplo en sus contenedores Docker.

| Servicio | URI utilizada |
|---|---|
| MongoDB 7 | `mongodb://localhost:27017/tfg_mongo7` |
| MongoDB 8 | `mongodb://localhost:27018/tfg_mongo8` |
| Redis 7 | `redis://localhost:6379` |

Una única clase `Mongo` tiene dos instancias independientes. Tanto `Mongo` como `Redis` implementan `DatabaseClient` y reciben la dirección en `open(String uri)`. Mongo obtiene también el nombre de la base desde la URI.

Las direcciones se definen en los programas de entrada y se pasan a `GestorTransacciones.abrir(uriMongo7, uriMongo8, uriRedis)`. Actualmente no se reciben como argumentos de la línea de comandos.

## Ejecución directa al gestor

Recorrido: `MainPruebaSecuencial → GestorTransacciones → bases de datos`.

Desde la raíz del proyecto:

```bash
mvn -Pprueba compile exec:java
```

Ejecuta los bloques de experimentos directamente contra el gestor, **sin pasar por el servidor**. Las escrituras modifican datos y las operaciones que alcanzan el registro del histórico generan documentos en MongoDB 7.

## Ejecución mediante cliente y servidor

Recorrido: `ClienteTransaccionesSimple → ServidorTransaccionesSimple → GestorTransacciones → bases de datos`.

En una terminal, iniciar el servidor:

```bash
mvn -Pservidor compile exec:java
```

En otra terminal, ejecutar el cliente:

```bash
mvn -Pcliente compile exec:java
```

El servidor escucha en el puerto `5000`. Su bucle `while (true)` acepta una conexión, atiende una petición y vuelve a esperar al siguiente cliente. La atención es secuencial: un cliente que no envía su línea mantiene al servidor esperando. Para detener el proceso desde la terminal, utilizar `Ctrl + C`.

El protocolo admite una línea `WRITE clave valor` o `READ clave`. El cliente actual conecta a `localhost:5000`, envía una única petición (`WRITE test HolaDesdeCliente`), recibe la respuesta y termina. Todavía no permite elegir el número de peticiones al ejecutarlo.

## Escenarios de la prueba secuencial

`workloads` indica la **proporción de lecturas**. Las escrituras son el resto:

| Valor | Lecturas | Escrituras |
|---|---:|---:|
| `0.0` | 0 % | 100 % |
| `0.2` | 20 % | 80 % |
| `0.5` | 50 % | 50 % |
| `0.8` | 80 % | 20 % |
| `1.0` | 100 % | 0 % |

Cada escenario recorre bloques de **2, 4, 8, 16, 32, 64, 128, 256 y 512 transacciones**. Los valores están definidos en el código. El número de lecturas se redondea a un entero, por lo que en bloques pequeños el porcentaje real puede diferir del solicitado. Las operaciones se mezclan antes de ejecutarse y utilizan las claves `cuenta1` a `cuenta4`.

Se muestran en consola los recuentos, el tiempo total medido, la latencia media y el throughput (transacciones por segundo). Estos resultados corresponden a la prueba directa al gestor.

## Histórico y errores

El gestor registra las operaciones que alcanzan ese paso en la colección `historico` de `tfg_mongo7`, con ID, tiempos de inicio y fin, duración, workload y tamaño de bloque. No es un registro completo de fallos: una excepción anterior impide llegar al guardado. Las llamadas del servidor utilizan actualmente `0` para workload y bloque.

Mongo y Redis capturan errores de sus bibliotecas, añaden un mensaje sobre la operación y propagan la excepción conservando su causa. El servidor captura fallos al atender a un cliente y continúa con el siguiente.

Este tratamiento no implementa rollback ni garantiza atomicidad entre las tres bases: si una escritura posterior falla, las anteriores pueden haber quedado aplicadas. Crear un cliente o pool tampoco garantiza por sí solo que el servicio esté disponible.

## Pruebas automáticas

```bash
mvn test
```

Hay 8 pruebas: 5 de Mongo y 3 de Redis. Verifican configuración inválida y propagación de errores conservando la causa. Utilizan fallos simulados o un pool cerrado; no necesitan servicios activos ni escriben en las bases reales. No sustituyen las pruebas de integración ni los experimentos de rendimiento.

## Pendiente

- Aclarar con Maite la parametrización del cliente de experimentos y el recorrido que debe medirse.
- Ejecutar los nuevos escenarios de 80 % y 100 % de lecturas y revisar los resultados.
- Preparar las gráficas cuando quede aclarado el diseño experimental.
- Definir el procedimiento de exportación y análisis; no hay exportación automática a CSV en el código actual.
- Abordar la concurrencia después de validar la fase secuencial.
