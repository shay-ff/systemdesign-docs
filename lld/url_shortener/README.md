URL Shortener (LLD) - simple, single-node implementation in C++.
Includes:
- base62 encoder/decoder
- in-memory map (for demo); in production use DB + id generation

## Build & Run
```bash
cd lld/url_shortener/src
g++ -std=c++17 url_shortener.cpp -o url && ./url
```

## Notes
- Focuses on core flow; persistence and collision handling are simplified.