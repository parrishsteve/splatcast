/*
curl -i -X GET http://localhost:8080/apps

curl -i -X POST http://localhost:8080/apps \
-H "Content-Type: application/json" \
-d '{"name": "sapp"}'

curl -i -X POST http://localhost:8080/apps/1/schemas \
-H "Content-Type: application/json" \
-d '{
"name": "Orders-2.0",
"jsonSchema": {
"type": "object",
"properties": {
"userId": {
"type": "string"
},
"eventType": {
"type": "string",
"enum": ["click", "view", "purchase", "share"]
},
"timestamp": {
"type": "string",
"format": "date-time"
},
"metadata": {
"type": "object"
}
},
"required": ["userId", "eventType", "timestamp"]
},
"status": "draft"
}'


# Create topic with quotas
curl -i -X POST http://localhost:8080/apps/1/topics -H "Content-Type: application/json" \
-d '{
"name": "POS-Orders",
"description": "User activity events",
"defaultSchemaId": 1, 
"retentionHours": 168,
"quotas": {
"perMinute": 10000,
"perDay": 2000000
}
}'


curl -X GET http://localhost:8080/apps/1/schemas \
-H "Accept: application/json"

curl -i -X GET "http://localhost:8080/apps/1/topics/1" -H "Accept: application/json"

wscat -c ws://localhost:8080/apps/1/topics/1/subscribe 

curl -i -X POST "http://localhost:8080/apps/1/topics/1/publish" \
-H "Authorization: Bearer <TOKEN>" \
-H "Content-Type: application/json" \
-d '{
"schemaId": 1,
"data": {
"userId": 123,
"action": "signup",
"metadata": { "source": "web" }
}
}'

curl -i -X POST "http://localhost:8080/apps/1/topics/1/transformers" \
-H "Authorization: Bearer <TOKEN>" \
-H "Content-Type: application/json" \
-d '{
"name": "OrdersOneTwo",
"fromSchemaId": 1,
"toSchemaId": 2,
"code": "function transform(input) {
const newValue = {
steve: input.action,
}
return newValue;
}",
"timeoutMs": 5000,
"enabled": true
}'


