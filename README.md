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
wscat -c ws://localhost:8080/apps/by-name/test16/topics/Test-Topic2/subscribe?fromTimestamp=1696166096000
wscat -c ws://localhost:8080/apps/by-name/test16/topics/Test-Topic2/subscribe?fromTimestamp=2025-11-11T12:34:56Z
wscat -c ws://localhost:8080/apps/by-name/test16/topics/Test-Topic2/subscribe?fromTimestamp=2023-11-11T09:34:56-05:00 = 11 values

For testing different timestamps tomorrow...
wscat -c ws://localhost:8080/apps/by-name/test16/topics/Test-Topic2/subscribe?fromTimestamp=2025-11-10T09:30:19.066458855-06:00
wscat -c ws://localhost:8080/apps/by-name/test16/topics/Test-Topic2/subscribe?fromTimestamp=2025-11-11T09:40:19.066458855-06:00

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
 - Should we centralize all route exception handling into StatusPages 
 - Logger enhancements, make structured, add parameters like app, topic, schema, requestId etc.
 - Handle Idempotency-Key
 - Handle Authorization header with Bearer token
 - Add examples for error cases
 - Check replay



# 1) By numeric IDs, epoch millis (seek to a specific instant)
WS GET ws://localhost:8080/apps/123/topics/456/subscribe?fromTimestamp=1696166096000
# expected: consumer seeks to 2023-10-01T12:34:56Z (epoch millis)

# 2) By numeric IDs, ISO-8601 Zulu
WS GET ws://localhost:8080/apps/123/topics/456/subscribe?fromTimestamp=2023-10-01T12:34:56Z
# expected: same as above, parsed from ISO-8601

# 3) By numeric IDs, ISO-8601 with offset
WS GET ws://localhost:8080/apps/123/topics/456/subscribe?fromTimestamp=2023-10-01T08:34:56-04:00
# expected: parsed including offset

# 4) By app name + topic name, no timestamp (live tail)
WS GET ws://localhost:8080/apps/by-name/my-app/topics/events/subscribe
# expected: subscribe from current/latest offset (no seek)

# 5) With schema selection plus timestamp
WS GET ws://localhost:8080/apps/123/topics/456/subscribe?schemaId=10&fromTimestamp=1696166096000
WS GET ws://localhost:8080/apps/by-name/my-app/topics/events/subscribe?schemaName=json&fromTimestamp=2023-10-01T12:34:56Z

# 6) Invalid timestamp (parsing fails) — verifies fallback/error handling
WS GET ws://localhost:8080/apps/123/topics/456/subscribe?fromTimestamp=not-a-timestamp
# expected: parseTimestamp returns null -> route proceeds without timestamp (or returns 400 if validated elsewhere)

# 7) Invalid path params (non-numeric IDs) — triggers validation error
WS GET ws://localhost:8080/apps/abc/topics/xyz/subscribe
# expected: 400 close reason "Invalid appId or topicId"

# 8) Edge case: epoch seconds instead of millis (verify units)
WS GET ws://localhost:8080/apps/123/topics/456/subscribe?fromTimestamp=1696166096
# expected: parsed as 1696166096 (likely wrong unit if code expects millis) — verify behavior

# 9) Large/old timestamp (seek to beginning-ish)
WS GET ws://localhost:8080/apps/123/topics/456/subscribe?fromTimestamp=0
# expected: seek to earliest available messages (depending on consumer logic)