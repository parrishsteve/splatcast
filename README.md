curl -X GET http://localhost:8080/api/apps

curl -X POST http://localhost:8080/api/apps \
-H "Content-Type: application/json" \
-d '{"appId": "test12345", "name": "My Test App"}'

# Create API key
curl -X POST http://localhost:8080/api/apps/app_1761081168689/keys \
-H "Content-Type: application/json" \
-d '{"role": "admin", "label": "Production Key"}'

# List API keys for an app
curl -X GET http://localhost:8080/api/apps/app_1761081168689/keys

# Get specific API key
curl -X GET http://localhost:8080/api/keys/key_123456789

# Verify API key
curl -X POST http://localhost:8080/api/keys/key_123456789/verify \
-H "Content-Type: application/json" \
-d '{"plainKey": "sk_YOUR_PLAIN_KEY"}'

# Delete API key
curl -X DELETE http://localhost:8080/api/keys/key_123456789

# Create topic with quotas
curl -i -X POST -H "Content-Type: application/json" \
-d '{
"name": "user-events",
"description": "User activity events",
"retentionHours": 168,
"quotas": {
"perMinute": 10000,
"perDay": 2000000
}
}' \
http://localhost:8080/api/apps/app_1761081168689/topics

# Update topic and quotas
curl -X PUT -H "Content-Type: application/json" \
-d '{
"description": "Updated description",
"quotas": {
"perMinute": 15000,
"perDay": 3000000
}
}' \
http://localhost:8080/api/apps/app_1761081168689/topics/topic_abc123

curl -X POST http://localhost:8080/apps/app_123/topics/topic_456/schemas \
-H "Content-Type: application/json" \
-d '{
"version": "1.0.0",
"jsonSchema": {
"type": "object",
"properties": {
"userId": {
"type": "string"
},
"eventType": {
"type": "string",
"enum": ["click", "view", "purchase"]
},
"timestamp": {
"type": "string",
"format": "date-time"
}
},
"required": ["userId", "eventType", "timestamp"]
},
"status": "active"
}'


curl -X POST http://localhost:8080/apps/app_123/topics/topic_456/schemas \
-H "Content-Type: application/json" \
-d '{
"version": "1.1.0",
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

curl -X GET http://localhost:8080/apps/app_123/topics/topic_456/schemas \
-H "Accept: application/json"

curl -i -X GET "http://localhost:8080/apps/app_1761081168689/topics/topic_09779bbc2690" -H "Accept: application/json"

/*
app_1761081168689
topic_09779bbc2690

wscat -c ws://localhost:8080/apps/app_1761081168689/topics/topic_09779bbc2690/subscribe 

curl -i -X POST "http://localhost:8080/apps/app_1761081168689/topics/topic_09779bbc2690/publish" \
-H "Authorization: Bearer <TOKEN>" \
-H "Content-Type: application/json" \
-d '{
"schemaVersion": "1.0",
"data": {
"userId": 123,
"action": "signup",
"metadata": { "source": "web" }
}
}'

curl -i -X POST "http://localhost:8080/apps/app_1761081168689/topics/topic_09779bbc2690/transformers" \
-H "Authorization: Bearer <TOKEN>" \
-H "Content-Type: application/json" \
-d '{
"fromSchema": "1.0",
"toSchema": "2.0",
"code": "function transform(input) {
const newValue = {
steve: input.action,
}
return newValue;
}",
"timeoutMs": 5000,
"enabled": true
}'


js_transforms (
app_id, code, code_hash, created_at, enabled, from_schema, id, lang, timeout_ms, to_schema, topic_id) 

VALUES
(('app_1761081168689'),
('function transform(input) { return input; }'),
('2fb7cee54f1786b85f9785252e985efe593df773d216cc96f4285bf08219fe33'),
('2025-10-25 12:26:07.909712-05'::timestamp with time zone),
('TRUE'::boolean),
('1.0'),
('trf_1761413167908'),
('0'::int4),
('5000'::int4),
('2.0'),
('topic_09779bbc2690'))

\nRETURNING * was aborted: ERROR: column \"lang\" is of type transform_lang but expression is of type integer\n



test
test
72
"1.0"
*/