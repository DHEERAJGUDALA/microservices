# Phase 11 Infrastructure — Docker Commands
# Run these BEFORE starting product-service

# ==================== Elasticsearch ====================
# ES 8.x has security enabled by default (HTTPS + password).
# For local dev we DISABLE security to avoid SSL cert setup.
# In production: remove the xpack.security lines and configure SSL + API keys.

docker run -d \
  --name elasticsearch \
  -p 9200:9200 \
  -p 9300:9300 \
  -e "discovery.type=single-node" \
  -e "xpack.security.enabled=false" \
  -e "ES_JAVA_OPTS=-Xms512m -Xmx512m" \
  elasticsearch:8.11.0

# Verify ES is running (wait ~30 seconds for startup):
# curl http://localhost:9200
# Expected: {"name":"...","cluster_name":"docker-cluster","status":"green",...}

# ==================== Redis ====================
# Redis 7.x — no auth for local dev.
# In production: use "requirepass yourpassword" and "bind 127.0.0.1".

docker run -d \
  --name redis \
  -p 6379:6379 \
  redis:7.2-alpine

# Verify Redis is running:
# docker exec -it redis redis-cli ping
# Expected: PONG

# ==================== Verify Both Running ====================
# docker ps | findstr -E "elasticsearch|redis"

# ==================== Useful Redis Debug Commands ====================
# Connect to Redis CLI:
#   docker exec -it redis redis-cli

# After creating a product via API:
#   KEYS products::*          → shows all cached product keys
#   GET "products::1"         → shows JSON-serialized ProductResponse
#   TTL "products::1"         → seconds until this cache entry expires
#   DEL "products::1"         → manually evict a cache entry

# ==================== Useful Elasticsearch Debug Commands ====================
# After creating a product via API (ES auto-synced):
#   curl http://localhost:9200/products/_search?pretty
#   → shows all indexed product documents

# Search test:
#   curl "http://localhost:9200/products/_search?pretty" \
#     -H "Content-Type: application/json" \
#     -d '{"query":{"multi_match":{"query":"laptop","fields":["name^2","description"],"fuzziness":"AUTO"}}}'

# ==================== Service Startup Order for Phase 11 ====================
# 1. PostgreSQL (already running locally)
# 2. docker start zookeeper && docker start kafka
# 3. docker start zipkin (optional)
# 4. docker start elasticsearch
# 5. docker start redis
# 6. config-server (port 8888)
# 7. eureka-server (port 8761)
# 8. product-service (port 8084)  ← gets ES + Redis config from config-server
# 9. All other services in any order
