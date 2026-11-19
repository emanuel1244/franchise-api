# Franchise API

API REST reactiva para gestionar **franquicias**, sus **sucursales** y los **productos** de cada sucursal. Construida con Spring Boot 3 + WebFlux, arquitectura hexagonal y persistencia en **Amazon DynamoDB**.

Prueba técnica Dev. Backend. Este documento explica cómo ejecutar el proyecto y cada paso que se siguió para desarrollarlo.

---

## Contenido

1. [Cumplimiento de la prueba](#1-cumplimiento-de-la-prueba)
2. [Tecnologías](#2-tecnologías)
3. [Arquitectura](#3-arquitectura)
4. [Modelo de datos en DynamoDB](#4-modelo-de-datos-en-dynamodb)
5. [Endpoints](#5-endpoints)
6. [Ejecución local](#6-ejecución-local)
7. [Pruebas](#7-pruebas)
8. [Infraestructura como código](#8-infraestructura-como-código)
9. [Paso a paso del desarrollo](#9-paso-a-paso-del-desarrollo)
10. [Decisiones de diseño](#10-decisiones-de-diseño)

---

## 1. Cumplimiento de la prueba

| # | Requisito | Estado | Dónde |
|---|-----------|--------|-------|
| 1 | Proyecto en Spring Boot | ✅ | Spring Boot 3.4.1 |
| 2 | Agregar franquicia | ✅ | `POST /api/franchises` |
| 3 | Agregar sucursal a una franquicia | ✅ | `POST /api/franchises/{id}/branches` |
| 4 | Agregar producto a una sucursal | ✅ | `POST .../branches/{id}/products` |
| 5 | Eliminar producto de una sucursal | ✅ | `DELETE .../products/{id}` |
| 6 | Modificar stock de un producto | ✅ | `PATCH .../products/{id}/stock` |
| 7 | Producto con más stock por sucursal de una franquicia | ✅ | `GET /api/franchises/{id}/top-stock-products` |
| 8 | Persistencia en la nube | ✅ | DynamoDB en AWS (`us-east-1`) |
| + | Empaquetado con Docker | ✅ | `Dockerfile`, `docker-compose.yml` |
| + | Programación reactiva / funcional | ✅ | WebFlux + Reactor + SDK asíncrono de AWS |
| + | Actualizar nombre de franquicia | ✅ | `PUT /api/franchises/{id}` |
| + | Actualizar nombre de sucursal | ✅ | `PUT .../branches/{id}` |
| + | Actualizar nombre de producto | ✅ | `PUT .../products/{id}` |
| + | Persistencia como infraestructura como código | ✅ | CloudFormation `infra/dynamodb.yaml` |
| + | Despliegue de toda la solución en la nube | ⏳ | Pendiente (ver sección 9, paso 11) |

**Mejoras adicionales (fuera de lo pedido):**

- Arquitectura hexagonal (puertos y adaptadores) y clean code.
- Documentación interactiva con Swagger / OpenAPI.
- Colección de Postman con pruebas automáticas que encadenan los IDs.
- Escrituras atómicas: no se puede crear una sucursal en una franquicia inexistente ni un producto en una sucursal inexistente (transacciones de DynamoDB).
- Respuestas de error uniformes (`400`, `404`, `500`) sin exponer detalles internos.
- Pruebas unitarias de los casos de uso y prueba de integración del adaptador DynamoDB.
- Cobertura de código con JaCoCo.
- Pipeline de CI con GitHub Actions (build, pruebas con DynamoDB Local e imagen Docker).
- Script de prueba de humo (`scripts/smoke-test.ps1`).
- Endpoint de salud (`/actuator/health`).
- Imagen Docker multi-stage que corre con un usuario sin privilegios.
- Tabla DynamoDB con cifrado en reposo y Point-in-Time Recovery.

---

## 2. Tecnologías

| Tecnología | Uso |
|------------|-----|
| Java 21 | Lenguaje (records, `Optional`, streams) |
| Spring Boot 3.4.1 + WebFlux | API REST reactiva no bloqueante |
| Project Reactor | `Mono` / `Flux` |
| AWS SDK v2 (`DynamoDbAsyncClient` + Netty) | Acceso asíncrono a DynamoDB |
| Amazon DynamoDB | Base de datos NoSQL administrada |
| AWS CloudFormation | Infraestructura como código |
| springdoc-openapi | Swagger UI |
| JUnit 5 + reactor-test | Pruebas |
| JaCoCo | Cobertura |
| Docker / Docker Compose | Empaquetado y entorno local |
| Postman / Newman | Pruebas de la API |
| GitHub Actions | Integración continua |

---

## 3. Arquitectura

Se aplicó **arquitectura hexagonal**. El dominio y los casos de uso no dependen de Spring ni de AWS; la infraestructura se conecta mediante interfaces (puertos).

```
             ┌────────────────────── infrastructure ──────────────────────┐
             │                                                            │
  HTTP  ───► │  adapter/in/web                adapter/out/dynamodb        │ ───► DynamoDB
             │  FranchiseController           DynamoFranchiseRepository   │
             │        │                               ▲                   │
             └────────┼───────────────────────────────┼───────────────────┘
                      ▼                               │
             ┌──────────────────── application ───────┼───────────────────┐
             │  port/in (casos de uso)          port/out                  │
             │  FranchiseUseCase ◄── FranchiseService ──► FranchiseRepositoryPort
             │  BranchUseCase    ◄── BranchService                         │
             │  ProductUseCase   ◄── ProductService                        │
             └────────────────────────────┬───────────────────────────────┘
                                          ▼
             ┌────────────────────────── domain ──────────────────────────┐
             │  Franchise, Branch, Product, BranchTopProduct              │
             │  NotFoundException, validaciones y reglas de negocio       │
             └────────────────────────────────────────────────────────────┘
```

### Estructura de carpetas

```
src/main/java/com/example/franchise
├── domain
│   ├── model          # Franchise, Branch, Product, BranchTopProduct (records inmutables)
│   └── exception      # NotFoundException
├── application
│   ├── port/in        # FranchiseUseCase, BranchUseCase, ProductUseCase
│   ├── port/out       # FranchiseRepositoryPort
│   └── service        # Implementaciones de los casos de uso (sin anotaciones de Spring)
└── infrastructure
    ├── adapter/in/web        # Controlador REST, DTOs de entrada, manejo de errores
    ├── adapter/out/dynamodb  # Adaptador DynamoDB, cliente y propiedades
    └── config                # Cableado de casos de uso y OpenAPI
```

### Reglas que se respetan

- **Dirección de dependencias:** `infrastructure → application → domain`. Nunca al revés.
- **Dominio rico:** las validaciones (nombre obligatorio, stock no negativo) y la regla "producto con más stock" viven en el dominio (`Branch.topStockProduct()`).
- **Casos de uso sin framework:** `FranchiseService`, `BranchService` y `ProductService` son clases Java puras. Spring las registra en `UseCaseConfig`.
- **Inversión de dependencias:** el controlador depende de interfaces de casos de uso y los servicios dependen de `FranchiseRepositoryPort`. Cambiar DynamoDB por otra base de datos solo requiere un nuevo adaptador.

---

## 4. Modelo de datos en DynamoDB

Se usó **diseño de tabla única** (*single-table design*). La tabla `franchises` tiene clave compuesta `pk` (partition key) + `sk` (sort key):

| Entidad | `pk` | `sk` | Atributos |
|---------|------|------|-----------|
| Franquicia | `F#{franchiseId}` | `META` | `id`, `name` |
| Sucursal | `F#{franchiseId}` | `B#{branchId}` | `id`, `name` |
| Producto | `F#{franchiseId}` | `P#{branchId}#{productId}` | `id`, `branchId`, `name`, `stock` |

**Por qué este diseño:**

- Toda la franquicia (sucursales y productos) vive en la misma partición, así que **una sola consulta (`Query pk = F#id`)** trae el árbol completo. El endpoint de "producto con más stock por sucursal" no necesita escanear la tabla.
- Agregar, renombrar, eliminar o cambiar stock son operaciones de **un solo ítem**, sin reescribir documentos grandes ni arriesgar el límite de 400 KB por ítem.
- Las escrituras usan expresiones condicionales (`attribute_exists`) y transacciones para garantizar que el padre exista y devolver `404` si no.

---

## 5. Endpoints

Base URL local: `http://localhost:8080`. Swagger UI: `http://localhost:8080/swagger-ui.html`.

| Método | Ruta | Cuerpo | Respuesta |
|--------|------|--------|-----------|
| `POST` | `/api/franchises` | `{"name"}` | `201` franquicia |
| `GET` | `/api/franchises/{franchiseId}` | – | `200` franquicia con sucursales y productos |
| `PUT` | `/api/franchises/{franchiseId}` | `{"name"}` | `200` franquicia |
| `GET` | `/api/franchises/{franchiseId}/top-stock-products` | – | `200` lista de productos con más stock por sucursal |
| `POST` | `/api/franchises/{franchiseId}/branches` | `{"name"}` | `201` sucursal |
| `PUT` | `/api/franchises/{franchiseId}/branches/{branchId}` | `{"name"}` | `200` sucursal |
| `POST` | `/api/franchises/{franchiseId}/branches/{branchId}/products` | `{"name","stock"}` | `201` producto |
| `PUT` | `/api/franchises/{franchiseId}/branches/{branchId}/products/{productId}` | `{"name"}` | `200` producto |
| `PATCH` | `/api/franchises/{franchiseId}/branches/{branchId}/products/{productId}/stock` | `{"stock"}` | `200` producto |
| `DELETE` | `/api/franchises/{franchiseId}/branches/{branchId}/products/{productId}` | – | `204` |
| `GET` | `/actuator/health` | – | `200 {"status":"UP"}` |

### Ejemplo: producto con más stock por sucursal

```http
GET /api/franchises/c1245db2-83cc-4596-82a6-94df5619ed71/top-stock-products
```

```json
[
  { "branchId": "837009d6-...", "branchName": "Sucursal Norte", "productId": "e14fb4a8-...", "productName": "Cafe", "stock": 99 },
  { "branchId": "57344b02-...", "branchName": "Sucursal Sur",   "productId": "20186862-...", "productName": "Pan",  "stock": 5 }
]
```

Reglas: se devuelve un producto por sucursal; las sucursales sin productos no aparecen; si hay empate de stock gana el nombre alfabéticamente menor, para que el resultado sea determinista.

### Errores

Todas las respuestas de error tienen el mismo formato:

```json
{ "status": 404, "error": "Franquicia no encontrada: abc" }
```

| Código | Cuándo |
|--------|--------|
| `400` | Nombre vacío, stock negativo, JSON inválido |
| `404` | Franquicia, sucursal o producto inexistente |
| `500` | Error no previsto (se registra en el log; al cliente solo le llega un mensaje genérico) |

---

## 6. Ejecución local

### Requisitos

- Java 21 o superior
- Maven 3.9+
- Para la opción A: AWS CLI configurado con un usuario que tenga permisos sobre DynamoDB
- Para la opción B: Docker Desktop

### Opción A: app local + DynamoDB en AWS (como se probó)

1. Configurar credenciales:
   ```powershell
   aws configure        # Access Key, Secret Key, region us-east-1
   aws sts get-caller-identity
   ```
2. Crear la tabla con CloudFormation:
   ```powershell
   aws cloudformation deploy `
     --stack-name franchise-api-dynamodb `
     --template-file infra/dynamodb.yaml `
     --parameter-overrides TableName=franchises `
     --region us-east-1
   ```
3. Compilar y ejecutar:
   ```powershell
   mvn clean package
   $env:AWS_REGION="us-east-1"
   $env:DYNAMODB_TABLE="franchises"
   java -jar target/franchise-api-1.0.0.jar
   ```
4. Probar: `http://localhost:8080/swagger-ui.html`

### Opción B: todo en Docker (sin cuenta de AWS)

Levanta la API y DynamoDB Local; la tabla se crea sola al arrancar.

```bash
docker compose up --build
```

### Variables de entorno

| Variable | Default | Descripción |
|----------|---------|-------------|
| `DYNAMODB_TABLE` | `franchises` | Nombre de la tabla |
| `AWS_REGION` | `us-east-1` | Región de AWS |
| `DYNAMODB_ENDPOINT` | *(vacío)* | Endpoint alternativo, p. ej. `http://localhost:8000` para DynamoDB Local |
| `DYNAMODB_CREATE_TABLE` | `false` | Crea la tabla al arrancar si no existe (solo para desarrollo local) |

Las credenciales se toman de la cadena estándar del SDK de AWS (variables de entorno, `~/.aws/credentials` o rol IAM). Nunca se guardan en el código.

---

## 7. Pruebas

### Pruebas automatizadas

```powershell
mvn clean verify
```

- `UseCasesTest`: prueba los casos de uso con un adaptador en memoria (flujo completo, top de stock, empates, validaciones, recursos inexistentes).
- `DynamoFranchiseRepositoryIntegrationTest`: prueba el adaptador real contra DynamoDB Local. Crea una tabla temporal y la borra al final. Se activa con una variable de entorno:
  ```powershell
  docker run -d -p 8000:8000 amazon/dynamodb-local
  $env:DYNAMODB_IT_ENDPOINT="http://localhost:8000"
  mvn clean verify
  ```
- El reporte de cobertura queda en `target/site/jacoco/index.html`.

### Postman

1. Importar `postman/Franchise-API.postman_collection.json` y `postman/Local.postman_environment.json`.
2. Seleccionar el entorno **Franchise API - Local**.
3. Ejecutar la colección completa con el **Runner**. Cada petición guarda los IDs que necesitan las siguientes y valida la respuesta.

También se puede ejecutar por consola con Newman:

```powershell
npx newman run postman/Franchise-API.postman_collection.json -e postman/Local.postman_environment.json
```

Resultado obtenido contra la tabla real en AWS: **18 peticiones, 28 aserciones, 0 fallos**.

### Prueba de humo

```powershell
.\scripts\smoke-test.ps1
```

Hace 13 llamadas a los endpoints y 5 casos de error, y muestra una tabla con el resultado de cada uno.

---

## 8. Infraestructura como código

`infra/dynamodb.yaml` (CloudFormation) crea la tabla con:

- Clave `pk` (HASH) + `sk` (RANGE)
- Facturación `PAY_PER_REQUEST`: no hay que estimar capacidad y sin tráfico el costo es cero
- Cifrado en reposo (SSE)
- Point-in-Time Recovery (respaldo continuo de 35 días)
- `DeletionPolicy: Retain`: si se borra el stack, la tabla y sus datos se conservan

---

## 9. Paso a paso del desarrollo

### Paso 1: Análisis del enunciado
Se leyó el PDF de la prueba y se listaron los 8 criterios obligatorios y los 7 puntos extra (tabla de la sección 1). Se eligió **DynamoDB** como motor de base de datos.

### Paso 2: Elección del stack
- **Spring Boot 3 + WebFlux** en lugar de Spring MVC, para cumplir el punto extra de programación reactiva.
- **AWS SDK v2 asíncrono** (`DynamoDbAsyncClient` con Netty), para que el acceso a la base de datos tampoco bloquee hilos. Los `CompletableFuture` del SDK se convierten a `Mono` con `Mono.fromFuture`.

### Paso 3: Diseño del modelo de datos
Se diseñó la tabla única descrita en la sección 4. La clave fue pensar primero en los patrones de acceso: el endpoint más exigente (top de stock por sucursal) necesita toda la franquicia, así que todo se agrupó bajo la misma partition key.

### Paso 4: Arquitectura hexagonal
El código se organizó en tres capas:
1. **Dominio:** records inmutables (`Franchise`, `Branch`, `Product`) con sus validaciones y la regla de negocio `Branch.topStockProduct()`.
2. **Aplicación:** interfaces de casos de uso (`port/in`), el puerto de persistencia (`port/out`) y servicios que los implementan sin depender de Spring.
3. **Infraestructura:** el controlador REST (adaptador de entrada), el repositorio DynamoDB (adaptador de salida) y la configuración que conecta todo.

### Paso 5: Adaptador DynamoDB
`DynamoFranchiseRepository` implementa `FranchiseRepositoryPort`:
- Crear sucursal o producto usa `TransactWriteItems` con un `ConditionCheck` sobre el padre, para no crear huérfanos.
- Renombrar, cambiar stock y eliminar usan `attribute_exists(pk)`. Si el ítem no existe, DynamoDB lanza `ConditionalCheckFailedException`, que se traduce a `NotFoundException` (`404`).
- Leer una franquicia usa `queryPaginator` con lectura consistente y arma el árbol en memoria.

### Paso 6: API REST y manejo de errores
- `FranchiseController` expone los endpoints y solo traduce HTTP a casos de uso.
- Los DTOs de entrada (`Requests`) validan con Bean Validation (`@NotBlank`, `@Min(0)`).
- `ApiExceptionHandler` centraliza los errores en un formato JSON uniforme.

### Paso 7: Pruebas automatizadas
- Pruebas de casos de uso con un repositorio en memoria (`InMemoryFranchiseRepository`). Así se prueba la lógica sin AWS y sin mocks.
- Prueba de integración del adaptador contra DynamoDB Local.
- JaCoCo para medir cobertura.

### Paso 8: Infraestructura en AWS
1. Se verificó la conexión con `aws sts get-caller-identity`.
2. Se validó la plantilla con `aws cloudformation validate-template`.
3. Se desplegó el stack `franchise-api-dynamodb` con `aws cloudformation deploy`.
4. Se confirmó que la tabla quedó `ACTIVE` con `aws dynamodb describe-table`.

### Paso 9: Ejecución local contra AWS y pruebas end-to-end
1. Se ejecutó la API localmente apuntando a la tabla real.
2. Se verificó `/actuator/health` → `UP` y `/v3/api-docs` → `200`.
3. Se ejecutó `scripts/smoke-test.ps1`: 18 de 18 verificaciones correctas.
4. Se consultó la tabla con `aws dynamodb query` para confirmar que los datos se guardaron con el diseño esperado (`META`, `B#...`, `P#...`).
5. Se ejecutó la colección de Postman con Newman: 18 peticiones, 28 aserciones, 0 fallos.

### Paso 10: Docker y CI
- `Dockerfile` multi-stage: compila con Maven y ejecuta sobre una imagen JRE con un usuario sin privilegios.
- `docker-compose.yml`: API + DynamoDB Local para quien no tenga cuenta de AWS.
- `.github/workflows/ci.yml`: en cada push compila, ejecuta las pruebas (incluida la de integración con DynamoDB Local como servicio), publica la cobertura y construye la imagen Docker.

### Paso 11: Despliegue en la nube (pendiente)
Siguiente paso planificado: publicar la imagen en Amazon ECR y desplegarla (por ejemplo en AWS App Runner o ECS Fargate) con un rol IAM que solo tenga permisos sobre la tabla `franchises`.

---

## 10. Decisiones de diseño

| Decisión | Alternativa | Motivo |
|----------|-------------|--------|
| Tabla única con ítems por entidad | Un documento por franquicia con listas anidadas | Evita el límite de 400 KB por ítem, las actualizaciones son atómicas por ítem y no hay conflictos al editar sucursales distintas a la vez |
| SDK v2 de bajo nivel | DynamoDB Enhanced Client | Control total de las expresiones condicionales y las transacciones |
| Validación en el dominio y en los DTOs | Solo en los DTOs | El dominio protege sus reglas sin importar quién lo invoque |
| Repositorio en memoria para las pruebas | Mockito | Las pruebas verifican comportamiento real y no detalles de implementación |
| `PAY_PER_REQUEST` | Capacidad provisionada | Costo cero sin tráfico y sin necesidad de estimar capacidad |
| UUID como identificador | IDs secuenciales | DynamoDB no tiene autoincremento y los UUID no revelan cuántos registros hay |
