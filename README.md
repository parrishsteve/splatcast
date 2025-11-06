# API Usage Examples

## Apps API examples

/*
curl -i -X GET http://localhost:8080/apps
curl -i -X GET http://localhost:8080/apps/1
curl -i -X GET http://localhost:8080/apps/by-name/sapp

curl -i -X POST http://localhost:8080/apps \
-H "Content-Type: application/json" \
-d '{"name": "test2"}'

curl -i -X DELETE http://localhost:8080/apps/4
curl -i -X DELETE http://localhost:8080/apps/by-name/test2

curl -i -X PUT http://localhost:8080/apps/6 \
-H "Content-Type: application/json" \
-d '{"name": "test16"}'

curl -i -X PUT http://localhost:8080/apps/by-name/test7 \
-H "Content-Type: application/json" \
-d '{"name": "test16"}'

## Schema API examples
curl -i -X GET http://localhost:8080/apps/6/schemas
curl -i -X GET http://localhost:8080/apps/by-name/test16/schemas
curl -i -X GET http://localhost:8080/apps/6/schemas/3
curl -i -X GET http://localhost:8080/apps/by-name/test16/schemas/Test-2.0

curl -i -X POST http://localhost:8080/apps/6/schemas \
-H "Content-Type: application/json" \
-d '{
"name": "Test-2.0",
"jsonSchema": {
 "type": "object",
 "properties": {
   "userId": {
     "type": "string"
   },
   "timestamp": {
     "type": "string",
     "format": "date-time"
   },
   "metadata": {
     "type": "object"
   }
 } 
},
  "status": "draft"
}'

curl -i -X POST http://localhost:8080/apps/by-name/test16/schemas \
-H "Content-Type: application/json" \
-d '{
"name": "Test-2.2",
"jsonSchema": {
"type": "object",
"properties": {
"userId": {
"type": "string"
},
"timestamp": {
"type": "string",
"format": "date-time"
},
"metadata": {
"type": "object"
}
}
},
"status": "draft"
}'

curl -i -X PUT http://localhost:8080/apps/6/schemas/6 \
-H "Content-Type: application/json" \
-d '{
"name": "Test-2.3",
"jsonSchema": {
"type": "object",
"properties": {
"userId": {
"type": "string"
},
"timestamp": {
"type": "string",
"format": "date-time"
},
"metadata": {
"type": "object"
}
}
},
"status": "active"
}'

curl -i -X PUT http://localhost:8080/apps/by-name/test16/schemas/Test-2.3.1 \
-H "Content-Type: application/json" \
-d '{
"name": "Test-2.4.1",
"jsonSchema": {
"type": "object",
"properties": {
"userId": {
"type": "string"
},
"timestamp": {
"type": "string",
"format": "date-time"
},
"metadata": {
"type": "object"
}
}
},
"status": "active"
}'

curl -i -X DELETE http://localhost:8080/apps/6/schemas/4
curl -i -X DELETE http://localhost:8080/apps/by-name/test16/schemas/Test-2.2


# Topic API examples
curl -i -X GET http://localhost:8080/apps/6/topics
curl -i -X GET http://localhost:8080/apps/by-name/test16/topics
curl -i -X GET http://localhost:8080/apps/6/topics/2
curl -i -X GET http://localhost:8080/apps/by-name/test16/topics/Test-Topic


curl -i -X POST http://localhost:8080/apps/6/topics -H "Content-Type: application/json" \
-d '{
"name": "Test-Topic",
"description": "User activity events",
"defaultSchemaId": 6, 
"retentionHours": 168,
"quotas": {
"perMinute": 10000,
"perDay": 2000000
}
}'

curl -i -X POST http://localhost:8080/apps/by-name/test16/topics -H "Content-Type: application/json" \
-d '{
"name": "Test-Topic2",
"description": "User activity events",
"defaultSchemaId": 6,
"retentionHours": 168,
"quotas": {
"perMinute": 10000,
"perDay": 2000000
}
}'

curl -i -X PATCH http://localhost:8080/apps/by-name/test16/topics/Test-Topic2 -H "Content-Type: application/json" \
-d '{
"name": "Test-Topic2",
"description": "User activity events",
"defaultSchemaName": "Test-2.0",
"retentionHours": 168,
"quotas": {
"perMinute": 10000,
"perDay": 2000000
}
}'

curl -i -X PATCH http://localhost:8080/apps/by-name/test16/topics/Test-Topic2 -H "Content-Type: application/json" \
-d '{
"name": "Test-Topic2",
"description": "User activity events",
"defaultSchemaId": 6,
"retentionHours": 168,
"quotas": {
"perMinute": 10000,
"perDay": 2000000
}
}'

curl -i -X PATCH http://localhost:8080/apps/6/topics/3 -H "Content-Type: application/json" \
-d '{
"name": "Test-Topic2",
"description": "User activity events",
"defaultSchemaName": "Test-2.0",
"retentionHours": 168,
"quotas": {
"perMinute": 10000,
"perDay": 2000000
}
}'

curl -i -X PATCH http://localhost:8080/apps/6/topics/3 -H "Content-Type: application/json" \
-d '{
"name": "Test-Topic2",
"description": "User activity events",
"defaultSchemaId": 6,
"retentionHours": 168,
"quotas": {
"perMinute": 10000,
"perDay": 2000000
}
}'


curl -i -X PUT http://localhost:8080/apps/by-name/test16/topics/Test-Topic2 -H "Content-Type: application/json" \
-d '{
"name": "Test-Topic2",
"description": "User activity events",
"defaultSchemaName": "Test-2.0",
"retentionHours": 168,
"quotas": {
"perMinute": 5000,
"perDay": 50000
}
}'

curl -i -X PUT http://localhost:8080/apps/by-name/test16/topics/Test-Topic2 -H "Content-Type: application/json" \
-d '{
"name": "Test-Topic2",
"description": "User activity events",
"defaultSchemaId": 6,
"retentionHours": 168,
"quotas": {
"perMinute": 6000,
"perDay": 60000
}
}'

curl -i -X PUT http://localhost:8080/apps/6/topics/3 -H "Content-Type: application/json" \
-d '{
"name": "Test-Topic2",
"description": "User activity events",
"defaultSchemaName": "Test-2.0",
"retentionHours": 168,
"quotas": {
"perMinute": 7000,
"perDay": 70000
}
}'

curl -i -X PUT http://localhost:8080/apps/6/topics/3 -H "Content-Type: application/json" \
-d '{
"name": "Test-Topic2",
"description": "User activity events",
"defaultSchemaId": 6,
"retentionHours": 168,
"quotas": {
"perMinute": 8000,
"perDay": 80000
}
}'

# Transformer API examples
curl -i -X GET http://localhost:8080/apps/6/topics/3/transformers
curl -i -X GET http://localhost:8080/apps/by-name/test16/topics/Test-Topic2/transformers
curl -i -X GET http://localhost:8080/apps/6/topics/3/transformers/4
curl -i -X GET http://localhost:8080/apps/by-name/test16/topics/Test-Topic2/transformers/OrdersTransformer

schemas": [
"id": 6,
"appId": 6,
"name": "Test-2.3",

"id": 3,
"appId": 6,
"name": "Test-2.0",
{
"id": 4,
"appId": 6,
"topicId": 3,
"name": "OrdersTransformer",
"fromSchemaId": 6,
"toSchemaId": 3,
"fromSchemaName": "SchemaInfoResult(id=6, name=Test-2.3)",
"toSchemaName": "Test-2.0",
"lang": "JS",
"code": "function transform(input) {\nconst newValue = {\nsteve: input.action,\n}\nreturn newValue;\n}",
"codeHash": "2a71096ae656e39808ef409e59fdbed36999f831352106c2cf8bd29ff01e8aed",
"timeoutMs": 5000,
"enabled": true,
"createdBy": null,
"createdAt": "2025-11-06T11:14:14.737807128-06:00"


curl -i -X POST "http://localhost:8080/apps/6/topics/3/transformers" \
-H "Authorization: Bearer <TOKEN>" \
-H "Content-Type: application/json" \
-d '{
"name": "OrdersTransformerRev",
"fromSchemaId": 3,
"toSchemaId": 6,
"code": "function transform(input) {
const newValue = {
steve: input.action,
}
return newValue;
}",
"timeoutMs": 5000,
"enabled": true
}'



# Publishing and Subscribing examples

wscat -c ws://localhost:8080/apps/1/topics/1/subscribe
wscat -c ws://localhost:8080/apps/by-name/sapp/topics/POS-Orders/subscribe

curl -i -X POST "http://localhost:8080/apps/by-name/test16/topics/Test-Topic2/publish" \
-H "Authorization: Bearer <TOKEN>" \
-H "Content-Type: application/json" \
-d '{
"schemaId": 6,
"targetSchemaId": 3,
"data": {
"userId": 123,
"action": "signup",
"metadata": { "source": "web" }
}
}'

curl -i -X POST "http://localhost:8080/apps/by-name/test16/topics/Test-Topic2/publish" \
-H "Authorization: Bearer <TOKEN>" \
-H "Content-Type: application/json" \
-d '{
"schemaName": "Test-2.0",
"transformToSchemaName": "Test-2.3",
"data": {
"userId": 123,
"action": "signup",
"metadata": { "source": "web" }
}
}'

# TODO
 - WE need indexes on names.
 - Can oy change the name on a transformer? Right now you can't 
 - Should we centralize all route exception handling into StatusPages 
 - Logger enhancements, make structured, add parameters like app, topic, schema, requestId etc.
 - Handle Idempotency-Key
 - Handle Authorization header with Bearer token
 - Add examples for error cases
 - Handle qoutas in publish example
 - Check retension by subscribing after some time
 - Check replay
 - What about 'batch' publishing? support it?
