WEBSITE_DIR := website

.PHONY: site-sync site-serve site-build

## Sync repo content into website/docs (source of truth stays at repo root)
site-sync:
	mkdir -p $(WEBSITE_DIR)/docs
	rsync -a --delete \
		--exclude '.git/' \
		--exclude '.github/' \
		--exclude '.slash-cli/' \
		--exclude '.kiro/' \
		--exclude '$(WEBSITE_DIR)/' \
		--exclude 'systemdesign-docs-site/' \
		--exclude '.DS_Store' \
		./ $(WEBSITE_DIR)/docs/

## Build the static site into ../systemdesign-docs-site
site-build: site-sync
	cd $(WEBSITE_DIR) && mkdocs build

## Live-reload preview at http://127.0.0.1:8000
## Re-run `make site-sync` after editing content to see changes
site-serve: site-sync
	cd $(WEBSITE_DIR) && mkdocs serve
