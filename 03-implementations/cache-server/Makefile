.PHONY: help install dev test benchmark clean docker-build docker-up docker-down

help: ## Show this help message
	@echo "Cache Server - Available Commands:"
	@echo ""
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-15s\033[0m %s\n", $$1, $$2}'

install: ## Install Python dependencies
	pip install -r requirements.txt

dev: ## Run development server
	python app.py

test: ## Run functionality tests
	python test_cache.py

benchmark: ## Run performance benchmarks
	cd benchmarks && python benchmark.py

benchmark-install: ## Install benchmark dependencies
	cd benchmarks && pip install -r requirements.txt

docker-build: ## Build Docker image
	docker build -t cache-server .

docker-up: ## Start services with docker-compose
	docker-compose up -d

docker-down: ## Stop docker-compose services
	docker-compose down

docker-logs: ## View docker-compose logs
	docker-compose logs -f

redis-cli: ## Connect to Redis CLI
	redis-cli

clean: ## Clean up temporary files
	find . -type f -name "*.pyc" -delete
	find . -type d -name "__pycache__" -delete
	rm -f benchmark_results.json

health: ## Check server health
	curl -s http://localhost:8000/health | python -m json.tool

stats: ## Get cache statistics
	curl -s http://localhost:8000/stats | python -m json.tool

# Development helpers
set-example: ## Set an example cache item
	curl -X PUT "http://localhost:8000/cache/example" \
		-H "Content-Type: application/json" \
		-d '{"value": {"message": "Hello from cache!"}, "ttl": 300}'

get-example: ## Get the example cache item
	curl -s "http://localhost:8000/cache/example" | python -m json.tool

clear-cache: ## Clear all cache items
	curl -X DELETE "http://localhost:8000/cache"