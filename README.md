# nexo-backend

Cinco microservicios en Spring Boot que forman el backend de NEXO, marketplace de delivery. Actúan como Resource Server de Amazon Cognito: no emiten tokens (el login de la app y del panel lo hace Cognito), los reciben por el Application Load Balancer y los validan por su cuenta antes de responder. Corren como contenedores en Amazon ECS Fargate.

## Estado de evidencia

| Categoría | Estado |
|---|---|
| Implementado | endpoints REST, validación de JWT de Cognito (firma, issuer y vigencia), autorización por rol (CLIENTE, TIENDA, CONDUCTOR, ADMIN) y por dueño del recurso, clave interna entre servicios, notificaciones, puntos, ofertas, mensajería con SNS y SQS y publicación de eventos en Kinesis |
| Configurado | despliegue en ECS Fargate detrás de un ALB con reglas por ruta, base de datos en RDS, imágenes en ECR, credenciales en Secrets Manager, registros en CloudWatch, tres versiones en ramas y etiquetas |
| Validado | 365 pruebas automáticas aprobadas con `mvn clean test` (ms-orders 59, ms-products 162, ms-users 64, ms-queues 57, ms-stores 23); flujo completo de un pedido contra el entorno desplegado; evento de pedido visible en el stream de Kinesis |
| Pendiente de validación | HTTPS (el laboratorio de AWS solo ofrece HTTP); observaciones OBS-01 a OBS-21 del informe de pruebas, entre ellas la clave interna con valor de ejemplo |

## 1. Microservicios

| Microservicio | Puerto | Responsabilidad |
|---|---|---|
| ms-users | 8081 | Usuarios y roles, direcciones, notificaciones, conductores, puntos e inicio de sesión del panel |
| ms-stores | 8082 | Tiendas, ciclo de aprobación y calificaciones de tiendas |
| ms-products | 8083 | Productos, categorías, variantes, opciones, stock y ofertas |
| ms-orders | 8084 | Pedidos y su ciclo de estados, pago, cancelación, canje de puntos y publicación de eventos |
| ms-queues | 8085 | Administración de colas SQS, topics SNS y streams de Kinesis (ruta `/messaging`) |

El ALB enruta por ruta a cada servicio y cada uno tiene su target group.

## 2. Versiones del backend

El backend se entrega en tres versiones, una por etapa del proyecto. Cada versión es una release que llega a `main` pasando por `staging`, y también tiene una rama propia.

| Versión | Release | Rama de versión | Commit | Fecha del commit | Qué incluye |
|---|---|---|---|---|---|
| 1 | v1.0.0 | `rama-1-base` | `c737627` | 5 sep 2026 | Etapa 1: proyecto base |
| 2 | v2.0.0 | `rama-2-sns-sqs` | `3181c03` | 19 oct 2026 | Etapa 2: SNS y SQS. Eventos de pedido, colas con DLQ, notificaciones y sistema de puntos |
| 3 | v3.0.0 | `rama-3-kinesis` | `f39135f` | 22 nov 2026 | Etapa 3: Kinesis. Publicación de eventos de pedido en el stream, administración de streams, ofertas y entorno AWS nuevo |

Ramas de trabajo: `develop` (integración), `staging` (preparación de releases) y `main` (versión final). La rama `deploy` es la que se usó para desplegar en AWS durante el desarrollo.

Para probar una versión:

```
git checkout rama-2-sns-sqs
cd ms-orders
mvn clean test
```

## 3. Stack técnico

- Java 17
- Spring Boot 4.1.1
- Maven
- Spring Web, Spring Data JPA, Spring Security OAuth2 Resource Server, Bean Validation
- Spring Cloud AWS y AWS SDK v2 (SQS, SNS y Kinesis)
- Base de datos MySQL en Amazon RDS
- Actuator
- Docker, con las imágenes en Amazon ECR

## 4. Estructura de cada microservicio

```
config/       seguridad y configuración de AWS
controller/   endpoints REST
service/      casos de uso
repository/   repositorios JPA
model/        entidades
dto/          contratos de entrada y salida
messaging/    publicación y consumo de eventos (ms-orders y ms-queues)
exception/    manejo de errores
```

## 5. Seguridad

| Mecanismo | Detalle |
|---|---|
| Token | JWT de Cognito, validado por cada microservicio |
| Roles | CLIENTE, TIENDA, CONDUCTOR y ADMIN, guardados en ms-users y asignados desde el panel |
| Dueño del recurso | las tiendas, productos, direcciones y pedidos solo los modifica su dueño (o ADMIN) |
| Llamadas entre servicios | encabezado `X-Internal-Key` en las rutas `internal` |
| Sin token | respuesta 401; sin permiso, 403 |

## 6. Eventos (SNS, SQS y Kinesis)

ms-orders publica un evento en cada cambio de estado del pedido (`pedido.pagado`, `pedido.confirmado`, `pedido.listo`, `pedido.aceptado`, `pedido.entregado`, entre otros):

- Al topic SNS `nexo-pedidos-eventos`, que reparte los eventos a seis colas SQS con su DLQ.
- Al stream de Kinesis `nexo-pedidos-stream` (etapa 3).

ms-queues permite administrar colas, topics y streams desde la API (solo ADMIN).

## 7. Cómo ejecutar y probar

Requiere Java 17 y Maven. Cada microservicio se levanta por separado:

```
cd ms-users
mvn spring-boot:run
```

Pruebas automáticas (las pruebas `*ApplicationTests` están deshabilitadas con `@Disabled` porque necesitan la base de datos y AWS):

```
mvn clean test
```

Comprobaciones contra el entorno desplegado:

```
curl http://<ALB>/actuator/health
curl -i http://<ALB>/tiendas        # 200: las tiendas aprobadas son públicas
curl -i http://<ALB>/usuarios/me    # 401: sin token no se pasa
```

El 401 es la señal de que la seguridad está activa.

## 8. Variables de entorno

Cada servicio recibe en ECS la conexión a RDS, los datos de Cognito (user pool y app client), la región de AWS, los nombres de colas, topic y stream, y la clave interna. Las credenciales y claves se guardan en Secrets Manager y no en el repositorio.

## 9. Despliegue

Cada microservicio se empaqueta en una imagen Docker, se sube a ECR y corre como servicio de ECS Fargate detrás del ALB. La base de datos es una instancia de RDS y los eventos usan SNS, SQS y Kinesis.

## 10. Evidencias

- `NEXO_API.postman_collection.json`: colección de Postman con los endpoints de los cinco microservicios.
- `NEXO_Documentacion_API.docx`: documentación de la API.
- `NEXO_Informe_de_pruebas.docx`: 365 pruebas y observaciones OBS-01 a OBS-21.
- `Informe_colas_NEXO.docx`: colas, topics y evidencia de Kinesis.
- `NEXO_Informe_tecnico.docx`: arquitectura, despliegue y capturas de AWS.
