LRU Cache (C++) - typical interview LLD.
Files:
- `src/lru_cache.cpp` - implementation
- `design.puml` - PlantUML class diagram

## Build & Run
```bash
cd lld/lru_cache/src
g++ -std=c++17 lru_cache.cpp -o lru && ./lru
```

## Notes
- O(1) get/put using doubly-linked list + hash map.
- Replace `<bits/stdc++.h>` if your toolchain lacks it.