Comando para conectar no docker minio
docker exec -it minio /bin/bash

Caso não permita rodar comandos com as AccessKeys deve-se atribuir as permissões
mc alias set local http://localhost:9000 ADMIN_USER ADMIN_PASSWORD

Para criar a Access Key e Secret Key
mc admin accesskey create local/

Pegar estas chaves e adicionar nas variáveis de ambiente do docker-compose.yml
MINIO_ACCESS_KEY: "RL9HRH51KWYNN50XBP24"
MINIO_SECRET_KEY: "KlGFbvWRec+OcV8zPt3iFSuxOTeIq5Bab39Nh4Us"

## Load Test

```bash
k6 run k6/upload-test.js
```

## Para criar a imagem docker e rodar o container
```bash
docker build -t document-management-service .
docker run --rm -p 8080:8080 -e JAVA_OPTS="-Xms50m -Xmx50m" document-management-service
```
